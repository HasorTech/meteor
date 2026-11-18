/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.nio.file.Path;
import net.hasor.cobble.ExceptionUtils;
import net.hasor.meteor.*;
import net.hasor.meteor.domain.MetException;
import net.hasor.meteor.domain.ProtocolStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

public class MetContextGovernanceTest {
    @TempDir
    public Path directory;

    public interface Echo {
        String echo(String value);
    }

    @Test
    public void serviceStateRemainsIsolatedBetweenContexts() throws Exception {
        // 两个上下文发布相同身份的服务，只停用第一个上下文。
        MetConfiguration firstConfig = new MetConfiguration().setDataHome(this.directory.resolve("first"));
        MetConfiguration secondConfig = new MetConfiguration().setDataHome(this.directory.resolve("second"));
        try (MetContext first = firstConfig.buildContext(); //
             MetContext second = secondConfig.buildContext()) {
            MetBindInfo<Echo> binding = first.publisher().rsfService(Echo.class, value -> value).register();
            second.publisher().rsfService(Echo.class, value -> value).register();
            first.getUpdater().setServiceEnabled(binding.getBindID(), false);

            // 更新器读取的是各自上下文的状态，绑定仍然存在。
            assertFalse(first.getUpdater().isServiceEnabled(binding.getBindID()));
            assertTrue(second.getUpdater().isServiceEnabled(binding.getBindID()));
            assertNotNull(first.getServiceInfo(binding.getBindID()));
        }
    }

    @Test
    public void unknownServiceCannotCreateGovernanceState() throws Exception {
        // 未登记服务不允许通过状态操作创建残留条目。
        MetConfiguration configuration = new MetConfiguration().setDataHome(this.directory.resolve("unknown"));
        try (MetContext context = configuration.buildContext()) {
            MetUpdater updater = context.getUpdater();
            assertThrows(IllegalArgumentException.class, () -> updater.setServiceEnabled("missing", false));
            assertThrows(IllegalArgumentException.class, () -> updater.isServiceEnabled("missing"));
        }
    }

    @Test
    public void blockingDoesNotChangeListenerOnlineState() throws Exception {
        // 启动真实监听器，再阻断业务调用。
        MetConfiguration configuration = new MetConfiguration().setDataHome(this.directory.resolve("block"));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            context.getUpdater().setBlock(true);
            assertTrue(context.getUpdater().isBlock());
            assertTrue(context.isOnline());

            // 解除阻断不需要重建上下文或监听器。
            context.getUpdater().setBlock(false);
            assertFalse(context.getUpdater().isBlock());
            assertTrue(context.isOnline());
        }
    }

    @Test
    public void shadowConsumerRemainsBlockedEvenWithTheFormerFilterName() throws Exception {
        // 提供者使用真实 RSF 监听，调用方使用普通 Shadow 消费绑定。
        MetConfiguration provider = new MetConfiguration().setDataHome(this.directory.resolve("provider"));
        provider.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        MetConfiguration consumer = new MetConfiguration().setDataHome(this.directory.resolve("consumer"));
        consumer.connector("tcp").protocol("rsf");
        try (MetContext target = provider.buildContext(); //
             MetContext caller = consumer.buildContext()) {
            target.publisher().rsfService(Echo.class, value -> value).register();
            caller.publisher().rsfService(Echo.class) //
                    .bindFilter("meteor.governance", (request, response, chain) -> chain.doFilter(request, response)) //
                    .asShadow().asDistributedCall().register();
            target.start();
            caller.start();
            Echo echo = caller.getRsfClient(target.getListenAddress("rsf")).wrapper(Echo.class);
            assertEquals("open", echo.echo("open"));

            // Shadow 和同名应用过滤器均保留，实例阻断仍在内核执行。
            caller.getUpdater().setBlock(true);
            Exception failure = assertThrows(Exception.class, () -> echo.echo("blocked"));
            MetException denied = assertInstanceOf(MetException.class, ExceptionUtils.getRootCause(failure));
            assertEquals(ProtocolStatus.Forbidden, denied.getStatus());
        }
    }

    @Test
    public void rpcAccessPolicyRejectsForeignBindingWithTheSameServiceId() throws Exception {
        // 两个上下文注册相同服务，访问策略只接受该上下文实际持有的绑定。
        MetConfiguration local = new MetConfiguration().setDataHome(this.directory.resolve("local"));
        MetConfiguration remote = new MetConfiguration().setDataHome(this.directory.resolve("remote"));
        try (MetContext first = local.buildContext(); //
             MetContext second = remote.buildContext()) {
            MetBindInfo<Echo> own = first.publisher().rsfService(Echo.class).register();
            MetBindInfo<Echo> foreign = second.publisher().rsfService(Echo.class).register();
            MetAccessPolicy policy = first.getExtension(MetAccessPolicy.class);
            assertEquals(own.getBindID(), foreign.getBindID());

            policy.allow(own, MetAccessEnum.values());
            assertThrows(IllegalArgumentException.class, () -> policy.allow(foreign, MetAccessEnum.values()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = MetAccessEnum.class, names = { "INSTANCE_BLOCKED", "SERVICE_DISABLED" })
    public void rpcAccessRequiresEveryActiveCondition(MetAccessEnum first) throws Exception {
        // 提供者同时阻断实例和停用服务，通过真实 RSF 调用该绑定。
        MetConfiguration provider = new MetConfiguration().setDataHome(this.directory.resolve("provider"));
        provider.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        MetConfiguration consumer = new MetConfiguration().setDataHome(this.directory.resolve("consumer"));
        consumer.connector("tcp").protocol("rsf");
        try (MetContext target = provider.buildContext(); //
             MetContext caller = consumer.buildContext()) {
            MetBindInfo<Echo> binding = target.publisher().rsfService(Echo.class, value -> value).register();
            caller.publisher().rsfService(Echo.class).register();
            target.start();
            caller.start();
            target.getUpdater().setBlock(true);
            target.getUpdater().setServiceEnabled(binding.getBindID(), false);
            Echo echo = caller.getRsfClient(target.getListenAddress("rsf")).wrapper(Echo.class);
            MetAccessPolicy policy = target.getExtension(MetAccessPolicy.class);

            // 分次登记两个条件。第一项不足以放行，第二项登记后同一代理可调用。
            policy.allow(binding, first);
            Exception failure = assertThrows(Exception.class, () -> echo.echo("blocked"));
            MetException denied = assertInstanceOf(MetException.class, ExceptionUtils.getRootCause(failure));
            assertEquals(ProtocolStatus.Forbidden, denied.getStatus());
            MetAccessEnum remaining = first == MetAccessEnum.INSTANCE_BLOCKED ? MetAccessEnum.SERVICE_DISABLED : MetAccessEnum.INSTANCE_BLOCKED;
            policy.allow(binding, remaining);
            assertEquals("open", echo.echo("open"));
        }
    }
}
