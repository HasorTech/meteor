/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.realmeteor.registry;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.TargetAdmin;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import net.hasor.meteor.registry.shared.RegistryTestUtils.Echo;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.registry.shared.RegistryTestUtils.createConfiguration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 默认标记来自实例生效配置，并沿注册存储与直连管理链路保留。 */
public class DefaultProtocolPublicationScenarioTest {
    @Test
    public void explicitNonFirstDefaultSurvivesRegistrationAndManagementRpc() throws Exception {
        // RSF 排在第一个，但应用显式选择另一个协议作为默认值。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("default-protocol-tests");
        MetConfiguration configuration = createConfiguration() //
                .setDefaultProtocol("registry-http") //
                .addExtension(registry);
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        configuration.connector("http").bind("127.0.0.1", 0).protocol("registry-http");
        try (MetContext context = configuration.buildContext(); //
             StorageAccess storage = registry.openStorage()) {
            context.start();
            var published = context.publisher().rsfService(Echo.class, value -> value).register();
            context.getExtension(RegistryClient.class).pushSync().get(10, TimeUnit.SECONDS);
            String instanceId = context.getInstanceID();
            String serviceId = published.getBindID();

            // 实例、服务的存储快照都保留显式默认值，不能按端点顺序推断。
            var instanceEndpoints = storage.getInstance(instanceId).getRuntime().getEndpoints();
            assertEquals(2, instanceEndpoints.length);
            for (var endpoint : instanceEndpoints) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }
            var storedEndpoints = storage.getInstanceService(instanceId, serviceId).getEndpoints();
            assertEquals(2, storedEndpoints.length);
            for (var endpoint : storedEndpoints) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }

            // 治理 API 和真实 RSF 管理 RPC 读取相同标记，默认协议无需是管理通道协议。
            RegistryAdmin admin = context.getExtension(RegistryAdmin.class);
            var instance = admin.getInstanceAdmin(instanceId).instance();
            for (var endpoint : instance.getEndpoints()) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }
            var member = admin.getServiceAdmin(serviceId).providers(null, null).getItems().get(0);
            assertEquals(2, member.getEndpoints().length);
            for (var endpoint : member.getEndpoints()) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }
            TargetAdmin target = context.getExtension(TargetAdmin.class);
            var remote = target.getServiceAdminByAddress(context.getListenAddress("rsf"), serviceId);
            var remoteMember = remote.providers(null, null).getItems().get(0);
            assertEquals(2, remoteMember.getEndpoints().length);
            for (var endpoint : remoteMember.getEndpoints()) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }
            var remoteInstance = target.getInstanceAdminByAddress(context.getListenAddress("rsf")).instance();
            assertEquals(2, remoteInstance.getEndpoints().length);
            for (var endpoint : remoteInstance.getEndpoints()) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }
        }
    }

    @Test
    public void serviceWithoutTheApplicationDefaultDoesNotMarkAnotherProtocol() throws Exception {
        // 应用默认协议没有被此服务开放，唯一发布的 RSF 不能冒充默认协议。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("default-protocol-tests");
        MetConfiguration configuration = createConfiguration() //
                .setDefaultProtocol("registry-http") //
                .addExtension(registry);
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        configuration.connector("http").bind("127.0.0.1", 0).protocol("registry-http");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            var published = context.publisher().rsfService(Echo.class, value -> value) //
                    .protocol("rsf").register();
            context.getExtension(RegistryClient.class).pushSync().get(10, TimeUnit.SECONDS);

            // 通过正式治理入口观察服务上报结果。
            var admin = context.getExtension(RegistryAdmin.class).getServiceAdmin(published.getBindID());
            var endpoints = admin.providers(null, null).getItems().get(0).getEndpoints();
            assertEquals(1, endpoints.length);
            assertEquals("rsf", endpoints[0].getProtocolName());
            assertFalse(endpoints[0].isDefaultProtocol());
        }
    }

    @Test
    public void inferredDefaultIsAlsoReported() throws Exception {
        // 不设置默认协议时，采用配置中第一个挂载协议。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("default-protocol-tests");
        MetConfiguration configuration = createConfiguration().addExtension(registry);
        configuration.connector("http").bind("127.0.0.1", 0).protocol("registry-http");
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            var published = context.publisher().rsfService(Echo.class, value -> value).register();
            context.getExtension(RegistryClient.class).pushSync().get(10, TimeUnit.SECONDS);

            // 推导出的默认值与显式配置具有相同的上报语义。
            var admin = context.getExtension(RegistryAdmin.class).getServiceAdmin(published.getBindID());
            var endpoints = admin.providers(null, null).getItems().get(0).getEndpoints();
            assertEquals(2, endpoints.length);
            for (var endpoint : endpoints) {
                boolean expectedDefault = StringUtils.equals("registry-http", endpoint.getProtocolName());
                assertEquals(expectedDefault, endpoint.isDefaultProtocol());
            }
        }
    }
}
