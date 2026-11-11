/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.realmeteor.registry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.TargetAdmin;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.registry.shared.RegistryTestUtils.createConfiguration;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证插件家族信息经过应用上报、存储、治理查询和真实管理 RPC 后保持一致。 */
public class ProtocolFamilyPublicationScenarioTest {
    @Test
    public void pluginFamiliesSurviveRegistrationAndManagementRpc() throws Exception {
        // 自定义地址 scheme 不能改变家族，出站版本也不能缩减 HTTP 监听能力。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("protocol-family-tests");
        MetConfiguration configuration = createConfiguration().addExtension(registry);
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        configuration.connector("http").bind("127.0.0.1", 0) //
                .protocol("registry-http").addressScheme("custom-http").option("httpVersion", "2");
        try (MetContext context = configuration.buildContext(); //
             StorageAccess storage = registry.openStorage()) {
            context.start();
            context.getExtension(RegistryClient.class).pushSync().get(10, TimeUnit.SECONDS);

            // 存储数据来自插件能力，而不是 URI 前缀或选择的出站版本。
            String instanceId = context.getInstanceID();
            var stored = storage.getInstance(instanceId).getRuntime().getEndpoints();
            assertEquals(2, stored.length);
            for (var endpoint : stored) {
                Set<String> expected = Set.of("TCP");
                if (StringUtils.equals(endpoint.getProtocolName(), "registry-http")) {
                    expected = Set.of("HTTP/1.1", "HTTP/2");
                }
                assertEquals(expected, endpoint.getProtocolFamilies());
            }

            // 本地目录查询与远程实例查询均保留有序家族列表。
            var local = context.getExtension(RegistryAdmin.class).getInstanceAdmin(instanceId).instance();
            var remote = context.getExtension(TargetAdmin.class) //
                    .getInstanceAdminByAddress(context.getListenAddress("rsf")).instance();
            for (var instance : List.of(local, remote)) {
                assertEquals(2, instance.getEndpoints().length);
                for (var endpoint : instance.getEndpoints()) {
                    List<String> expected = List.of("TCP");
                    if (StringUtils.equals(endpoint.getProtocolName(), "registry-http")) {
                        expected = List.of("HTTP/1.1", "HTTP/2");
                    }
                    assertEquals(expected, List.copyOf(endpoint.getProtocolFamilies()));
                }
            }
        }
    }
}
