/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.nio.file.Path;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
}
