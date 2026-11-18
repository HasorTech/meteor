/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.nio.file.Path;
import net.hasor.meteor.MetAccessEnum;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.AddressPool;
import net.hasor.meteor.container.MetContainer;
import net.hasor.meteor.domain.ServiceDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class MetAccessPolicyExtensionTest {
    @TempDir
    public Path directory;

    @Test
    public void removedBindingDoesNotExemptItsReplacement() throws Exception {
        // 使用正常上下文设置构造容器，访问策略监听实际的服务移除事件。
        try (MetContext context = new MetConfiguration().setDataHome(this.directory).buildContext()) {
            MetContainer container = new MetContainer(new AddressPool(), context.getSettings());
            try (MetAccessPolicyExtension policy = new MetAccessPolicyExtension(container)) {
                container.subscribe(policy::onServiceEvent);
                var publisher = container.createPublisher();
                MetBindInfo<Echo> old = publisher.rsfService(Echo.class).register();
                String serviceId = old.getBindID();
                ServiceDomain<?> original = (ServiceDomain<?>) container.getRsfBindInfo(serviceId);
                policy.allow(old, MetAccessEnum.INSTANCE_BLOCKED);
                policy.allow(old, MetAccessEnum.INSTANCE_BLOCKED);
                assertTrue(policy.allows(old, original, MetAccessEnum.INSTANCE_BLOCKED));
                assertFalse(policy.allows(old, original, MetAccessEnum.SERVICE_DISABLED));

                // 再次注册相同服务 ID，旧绑定的豁免随注销撤销。
                assertTrue(container.recoverService(serviceId));
                MetBindInfo<Echo> replacement = publisher.rsfService(Echo.class).register();
                ServiceDomain<?> current = (ServiceDomain<?>) container.getRsfBindInfo(serviceId);
                assertFalse(policy.allows(old, original, MetAccessEnum.INSTANCE_BLOCKED));
                assertFalse(policy.allows(replacement, current, MetAccessEnum.INSTANCE_BLOCKED));
                assertThrows(IllegalArgumentException.class, () -> policy.allow(old, MetAccessEnum.INSTANCE_BLOCKED));
            }
        }
    }

    public interface Echo {
        String echo(String value);
    }
}
