/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.bootstrap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.meteor.registry.admin.*;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import net.hasor.meteor.registry.shared.DecoratedRegistryConfiguration;
import net.hasor.meteor.registry.shared.RegistryTestUtils;
import net.hasor.meteor.registry.shared.RegistryTestUtils.Echo;
import net.hasor.meteor.registry.shared.StorageProbe;
import net.hasor.meteor.registry.shared.rpc.InstanceData;
import net.hasor.meteor.registry.shared.rpc.InstanceEndpointData;
import net.hasor.meteor.registry.shared.rpc.ManagementRPC;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import net.hasor.meteor.registry.shared.storage.domain.EntInstanceEndpointData;
import net.hasor.meteor.registry.shared.storage.domain.EntServiceCatalogData;
import net.hasor.meteor.registry.shared.storage.domain.RefProtocolMountData;
import net.hasor.meteor.registry.shared.utils.RegistryJson;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.registry.shared.RegistryTestUtils.createConfiguration;
import static net.hasor.meteor.registry.shared.RegistryTestUtils.createDataHome;
import static org.junit.jupiter.api.Assertions.*;

public class RegistryAdminConfigurationTest {
    @Test
    public void httpServiceDocumentsStayWithTheirOwnService() throws Exception {
        // 同一个 SOAP 挂载发布两个服务，其中一个使用精确路径。
        MetConfiguration configuration = createConfiguration().addExtension(new RegistryConfiguration().setOnline(false));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        configuration.connector("http").bind("127.0.0.1", 0).protocol("soap").contextPath("/soap");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            var first = context.publisher().rsfService(Echo.class).toInstance(value -> value) //
                    .name("one").protocol("soap").aliasName("soap", "First").register();
            var second = context.publisher().rsfService(Echo.class).toInstance(value -> value) //
                    .name("two").protocol("soap").aliasName("soap", "Second") //
                    .contextPathByExact("soap", "/exact").register();
            ManagementRPC management = context.getServiceProvider(context.getServiceInfo(ManagementRPC.class)).get();
            String base = "http://127.0.0.1:" + context.getListenAddress("soap").getPort();

            // 实例只上报挂载与能力，不能携带任何具体服务及其契约地址。
            InstanceEndpointData instance = Arrays.stream(management.instance(null).getEndpoints()) //
                    .filter(item -> StringUtils.equals(item.getProtocolName(), "soap")).findFirst().orElseThrow();
            assertEquals(base + "/soap", instance.getEndpoints().get("http"));
            assertEquals("", instance.getEndpoints().get("schema"));
            ObjectNode instanceJson = RegistryJson.copy(instance, ObjectNode.class);
            assertEquals(4, instanceJson.size());
            assertEquals(Set.of("HTTP/1.1", "HTTP/2"), instance.getProtocolFamilies());
            assertTrue(instanceJson.has("protocolFamilies"));
            assertTrue(instanceJson.has("defaultProtocol"));
            assertFalse(instanceJson.path("defaultProtocol").asBoolean());
            assertTrue(instanceJson.has("protocolName"));
            assertTrue(instanceJson.has("endpoints"));
            EntInstanceEndpointData storedInstance = RegistryJson.copy(instance, EntInstanceEndpointData.class);
            MetInstanceEndpoint restoredInstance = RegistryJson.copy(storedInstance, MetInstanceEndpoint.class);
            assertEquals(instance.getEndpoints(), restoredInstance.getEndpoints());
            assertEquals(instance.getProtocolFamilies(), restoredInstance.getProtocolFamilies());

            // 每个服务只拥有自己的真实调用地址及 WSDL，不混入另一个服务。
            var firstEndpoint = management.service(null, first.getBindID()).getEndpoints()[0];
            var secondEndpoint = management.service(null, second.getBindID()).getEndpoints()[0];
            String soap = context.getListenAddress("soap").toString();
            Map<String, String> firstDocuments = Map.of("endpoint", soap, //
                    "http", base + "/soap/registry-client/1/First", "schema", base + "/soap/registry-client/1/First?wsdl");
            Map<String, String> exactDocuments = Map.of("endpoint", soap, //
                    "http", base + "/soap/exact", "schema", base + "/soap/exact?wsdl");
            assertEquals(firstDocuments, firstEndpoint.getEndpoints().get("soap"));
            assertEquals(exactDocuments, secondEndpoint.getEndpoints().get("soap"));
            RefProtocolMountData stored = RegistryJson.copy(firstEndpoint, RefProtocolMountData.class);
            MetServiceEndpoint restored = RegistryJson.copy(stored, MetServiceEndpoint.class);
            assertEquals(firstEndpoint.getEndpoints().get("soap"), restored.getEndpoints().get("soap"));
            HttpClient client = HttpClient.newHttpClient();
            List<String> definitions = List.of(firstEndpoint.getEndpoints().get("soap").get("schema"), secondEndpoint.getEndpoints().get("soap").get("schema"));
            for (String definition : definitions) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(definition)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                assertTrue(response.body().contains("definitions"), response.body());
            }
        }
    }

    @Test
    public void protocolEndpointTypesAreReportedWithoutPublishedBusinessServices() throws Exception {
        // 协议能力由 SPI 声明，不能依赖业务服务当前是否存在。
        MetConfiguration configuration = createConfiguration().addExtension(new RegistryConfiguration().setOnline(false));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        configuration.connector("http").bind("127.0.0.1", 0).protocol("soap").contextPath("/soap");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            ManagementRPC management = context.getServiceProvider(context.getServiceInfo(ManagementRPC.class)).get();
            InstanceEndpointData soap = Arrays.stream(management.instance(null).getEndpoints()) //
                    .filter(endpoint -> StringUtils.equals(endpoint.getProtocolName(), "soap")).findFirst().orElseThrow();
            Set<String> types = context.getEndpointTypes("http-1", "soap");
            assertEquals(Set.of("endpoint", "http", "schema"), types);
            assertEquals(types, soap.getEndpoints().keySet());
            assertEquals("", soap.getEndpoints().get("schema"));
            assertFalse(soap.getEndpoints().get("http").contains("?wsdl"));
            assertTrue(context.getEndpointTypes("missing", "soap").isEmpty());
        }
    }

    @Test
    public void defaultsManagementPublicationToRsf() throws Exception {
        // 业务端点包含 HTTP，默认管理服务仍只选择 RSF。
        RegistryConfiguration registry = new RegistryConfiguration().setOnline(false);
        MetConfiguration configuration = createConfiguration() //
                .addExtension(registry);
        configuration.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        configuration.connector("http") //
                .bind("127.0.0.1", 0) //
                .protocol("registry-http");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            MetBindInfo<ManagementRPC> service = context.getServiceInfo(ManagementRPC.class);
            assertEquals(Set.of("rsf"), service.getBindProtocols());

            // 实例保留全部监听地址，管理地址只返回实际开放的协议。
            ManagementRPC local = context.getServiceProvider(service).get();
            InstanceData instance = local.instance(null);
            assertEquals(2, instance.getEndpoints().length);
            InstanceEndpointData rsf = Arrays.stream(instance.getEndpoints()) //
                    .filter(endpoint -> StringUtils.equals("rsf", endpoint.getProtocolName())).findFirst().orElseThrow();
            assertEquals(context.getListenAddress("rsf").toString(), rsf.getEndpoints().get("endpoint"));
            assertEquals(rsf.getEndpoints().get("endpoint"), rsf.getEndpoints().get("management"));
            InstanceEndpointData http = Arrays.stream(instance.getEndpoints()) //
                    .filter(endpoint -> StringUtils.equals("registry-http", endpoint.getProtocolName())) //
                    .findFirst().orElseThrow();
            assertEquals(context.getListenAddress("registry-http").toString(), http.getEndpoints().get("endpoint"));
            assertNull(http.getEndpoints().get("management"));
        }
    }

    @Test
    public void offlineManagementUsesConfiguredProtocolsCapturedAtCreation() throws Exception {
        // 离线扩展明确选择 HTTP；配置对象后续修改不能扩大已建上下文的发布范围。
        RegistryConfiguration registry = new RegistryConfiguration().setOnline(false);
        registry.setManagementProtocols("registry-http");
        MetConfiguration configuration = createConfiguration() //
                .addExtension(registry);
        configuration.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        configuration.connector("http") //
                .bind("127.0.0.1", 0) //
                .protocol("registry-http");
        try (MetContext context = configuration.buildContext()) {
            registry.setManagementProtocols("rsf");
            context.start();
            MetBindInfo<ManagementRPC> service = context.getServiceInfo(ManagementRPC.class);
            assertEquals(Set.of("registry-http"), service.getBindProtocols());
            ManagementRPC local = context.getServiceProvider(service).get();
            InstanceEndpointData[] endpoints = local.instance(null).getEndpoints();
            assertEquals(2, endpoints.length);
            InstanceEndpointData http = Arrays.stream(endpoints) //
                    .filter(endpoint -> StringUtils.equals("registry-http", endpoint.getProtocolName())) //
                    .findFirst().orElseThrow();
            assertEquals(context.getListenAddress("registry-http").toString(), http.getEndpoints().get("management"));
            InstanceEndpointData rsf = Arrays.stream(endpoints) //
                    .filter(endpoint -> StringUtils.equals("rsf", endpoint.getProtocolName())).findFirst().orElseThrow();
            assertEquals(context.getListenAddress("rsf").toString(), rsf.getEndpoints().get("endpoint"));
            assertNull(rsf.getEndpoints().get("management"));
        }
    }

    @Test
    public void onlineManagementUsesAllConfiguredProtocols() throws Exception {
        // 在线存储客户端将同一份协议配置传给管理服务。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementProtocols("rsf", "registry-http");
        MetConfiguration configuration = createConfiguration() //
                .addExtension(registry);
        configuration.connector("tcp") //
                .protocol("rsf");
        configuration.connector("http") //
                .protocol("registry-http");
        try (MetContext context = configuration.buildContext()) {
            assertEquals(Set.of("rsf", "registry-http"), context.getServiceInfo(ManagementRPC.class).getBindProtocols());
        }
    }

    @Test
    public void rejectsEmptyManagementProtocols() {
        RegistryConfiguration registry = new RegistryConfiguration();
        assertThrows(IllegalArgumentException.class, () -> registry.setManagementProtocols());
    }

    @Test
    public void rejectsNullManagementProtocols() {
        RegistryConfiguration registry = new RegistryConfiguration();
        assertThrows(IllegalArgumentException.class, () -> registry.setManagementProtocols((String[]) null));
    }

    @Test
    public void rejectsBlankManagementProtocolName() {
        RegistryConfiguration registry = new RegistryConfiguration();
        assertThrows(IllegalArgumentException.class, () -> registry.setManagementProtocols("rsf", " "));
    }

    @Test
    public void normalizesAndDeduplicatesManagementProtocols() {
        RegistryConfiguration registry = new RegistryConfiguration();
        registry.setManagementProtocols(" rsf ", "registry-http", "rsf");
        assertEquals(List.of("rsf", "registry-http"), registry.getManagementProtocols());
    }

    @Test
    public void validatesManagementConfigurationBeforeOpeningStorage() throws Exception {
        // 准备带存储观察器的注册中心配置。
        StorageProbe probe = new StorageProbe();
        RegistryConfiguration registry = new DecoratedRegistryConfiguration(new MemoryRegistryConfiguration(), probe::observe);

        // 固定凭据为空时直接拒绝配置。
        assertThrows(IllegalArgumentException.class, () -> registry.setManagementToken(null));
        assertThrows(IllegalArgumentException.class, () -> registry.setManagementToken(" "));

        // 移除凭据提供者后仍可创建扩展，但不能取得管理服务。
        registry.setManagementToken("secret");
        registry.setTokenProvider(null);
        MetConfiguration application = createConfiguration() //
                .addExtension(registry);

        try (MetContext context = application.buildContext()) {
            RegistryAdmin admin = context.getExtension(RegistryAdmin.class);
            assertThrows(IllegalStateException.class, admin::getClusterAdmin);
            assertEquals(0, probe.openedSessions());
        }
    }

    @Test
    public void buildsIndependentAdminConfigurationSnapshots() throws Exception {
        // 准备共享内存库，先按原凭据创建第一个上下文。
        MemoryRegistryConfiguration bootstrap = new MemoryRegistryConfiguration();
        bootstrap.setManagementToken("口令");
        MetConfiguration firstConfiguration = createConfiguration() //
                .addExtension(bootstrap);

        try (MetContext firstContext = firstConfiguration.buildContext()) {
            RegistryAdmin first = firstContext.getExtension(RegistryAdmin.class);
            assertEquals(0, first.getClusterAdmin().services(null, null).getItems().size());

            // 修改配置后创建第二个上下文，两个管理入口独立。
            bootstrap.setManagementToken("changed");
            MetConfiguration secondConfiguration = createConfiguration() //
                    .addExtension(bootstrap);

            try (MetContext secondContext = secondConfiguration.buildContext()) {
                RegistryAdmin second = secondContext.getExtension(RegistryAdmin.class);
                assertEquals("memory", first.getClusterAdmin().info().getProvider());
                assertEquals("memory", second.getClusterAdmin().info().getProvider());
                assertNotSame(first, second);

                // 修改查询结果不能反向修改内部配置。
                first.getClusterAdmin().info().getScope().put("external", "changed");
                assertTrue(first.getClusterAdmin().info().getScope().isEmpty());
            }
        }
    }

    @Test
    public void closingAdminReleasesSessionOnceAndRejectsFurtherRegistryStorage() throws Exception {
        // 准备管理扩展并记录存储会话的释放次数。
        MemoryRegistryConfiguration storageConfiguration = new MemoryRegistryConfiguration();
        StorageProbe probe = new StorageProbe();
        RegistryConfiguration bootstrap = new DecoratedRegistryConfiguration(storageConfiguration, probe::observe);
        bootstrap.setManagementToken("secret");
        MetConfiguration adminConfiguration = createConfiguration() //
                .addExtension(bootstrap);

        try (MetContext adminContext = adminConfiguration.buildContext()) {
            RegistryAdmin admin = adminContext.getExtension(RegistryAdmin.class);

            // 首次管理操作打开会话，确认当前可访问。
            MetClusterAdmin cluster = admin.getClusterAdmin();
            assertTrue(cluster.status().isReachable());

            // 重复关闭管理入口，只释放一次会话并拒绝后续操作。
            admin.close();
            admin.close();

            assertEquals(1, probe.closedSessions());
            assertThrows(IllegalStateException.class, () -> admin.getClusterAdmin());
            assertThrows(IllegalStateException.class, () -> cluster.services(null, null));
        }

        // 上下文退出时不会重复释放已关闭的管理会话。
        assertEquals(1, probe.openedSessions());
        assertEquals(1, probe.closedSessions());
    }

    @Test
    public void buildsClientWithExistingServicesRegisteredAndGovernanceApplied() throws Exception {
        // 关闭自动上线，分别配置应用传输层和注册中心扩展。
        MemoryRegistryConfiguration bootstrap = new MemoryRegistryConfiguration();
        bootstrap.setManagementToken("secret");
        MetConfiguration application = createConfiguration() //
                .setAutomaticOnline(false);
        application.connector("tcp") //
                .protocol("rsf");
        application.addExtension(bootstrap);

        try (MetContext context = application.buildContext()) {
            RegistryAdmin admin = context.getExtension(RegistryAdmin.class);

            // 启动前声明服务，并预先写入其治理配置。
            MetBindInfo<Echo> service = context.publisher().rsfService(Echo.class).register();
            admin.getServiceAdmin(service.getBindID()).putMeta("owner", "managed");

            // 启动时注册服务并应用治理，应用仍保持离线。
            context.start();
            RegistryClient client = context.getExtension(RegistryClient.class);
            assertFalse(context.isOnline());
            assertEquals("managed", service.getMetaData("owner"));
            String instanceId = context.getInstanceID();
            MetPage<MetServiceInfo> services = admin.getInstanceAdmin(instanceId).getServiceList(null);
            assertEquals(1, services.getItems().size());
            assertEquals(service.getBindID(), services.getItems().get(0).getServiceId());
            assertFalse(context.getServiceInfo(ManagementRPC.class).isShadow());

            // 主动推送不影响管理 RPC 的注册。
            client.pushSync().get(5, TimeUnit.SECONDS);
            assertNotNull(context.getServiceInfo(ManagementRPC.class));
        }
    }

    @Test
    public void createsClientAndAdminWithIndependentLifecycles() throws Exception {
        // 应用和独立管理上下文使用同一注册中心配置。
        MemoryRegistryConfiguration bootstrap = new MemoryRegistryConfiguration();
        bootstrap.setManagementToken("secret");
        MetConfiguration application = createConfiguration() //
                .setApplicationName("订单");
        application.connector("tcp") //
                .protocol("rsf");
        application.addExtension(bootstrap);

        try (MetContext context = application.buildContext()) {
            context.start();

            // 通过另一上下文下发阻断治理，等待应用订阅生效。
            MetConfiguration adminConfiguration = createConfiguration() //
                    .addExtension(bootstrap);

            try (MetContext adminContext = adminConfiguration.buildContext()) {
                RegistryAdmin admin = adminContext.getExtension(RegistryAdmin.class);
                assertEquals(1, admin.getClusterAdmin().instances("订单", null).getItems().size());
                admin.getInstanceAdmin(context.getInstanceID()).block();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!admin.getInstanceAdmin(context.getInstanceID()).instance().isBlock()) {
                    assertTrue(System.nanoTime() < deadline, "Client did not apply storage change");
                    Thread.sleep(10);
                }
            }

            // 管理上下文关闭后，应用会话及注册信息仍然有效。
            assertTrue(context.isOnline());
            assertTrue(RegistryTestUtils.hasRegistrations(bootstrap));

            // 应用关闭后才撤销自己的临时注册信息。
            context.close();
            assertFalse(context.isOnline());
            assertFalse(RegistryTestUtils.hasRegistrations(bootstrap));
        }
    }

    @Test
    public void directStorageIsIndependentAndDoesNotRequireManagementToken() throws Exception {
        // 准备不带管理凭据的内存库。
        MemoryRegistryConfiguration configuration = new MemoryRegistryConfiguration();
        EntServiceCatalogData service = new EntServiceCatalogData();
        service.setServiceId("echo");

        try (StorageAccess first = configuration.openStorage(); //
             StorageAccess second = configuration.openStorage()) {
            // 两个会话共享记录，但拥有独立生命周期。
            assertNotSame(first, second);
            first.saveService(service);
            assertNotNull(second.getService("echo"));

            // 关闭写入会话不影响另一会话读取持久记录。
            first.close();
            assertNotNull(second.getService("echo"));
        }
    }

    @Test
    public void eachContextCapturesConfigurationAtBuildTime() throws Exception {
        // 准备可重复构建上下文的配置与会话观察器。
        MemoryRegistryConfiguration storageConfiguration = new MemoryRegistryConfiguration();
        StorageProbe probe = new StorageProbe();
        RegistryConfiguration registry = new DecoratedRegistryConfiguration(storageConfiguration, probe::observe);
        MetConfiguration application = createConfiguration() //
                .addExtension(registry);
        application.connector("tcp") //
                .protocol("rsf");

        try (MetContext first = application.buildContext()) {
            // 构建时只创建本地扩展，随后修改配置不能改变已创建客户端。
            assertEquals(0, probe.openedSessions());
            registry.setRefreshMillis(0);
            application.setDataHome(createDataHome());
            IllegalStateException failure = assertThrows(IllegalStateException.class, application::buildContext);
            assertEquals("refreshMillis must be positive", failure.getCause().getMessage());
            first.start();
            assertEquals(1, probe.openedSessions());

            // 恢复有效配置后构建第二个上下文，关闭第一个不影响第二个。
            registry.setRefreshMillis(60000);

            try (MetContext second = application.buildContext()) {
                RegistryClient firstClient = first.getExtension(RegistryClient.class);
                RegistryClient secondClient = second.getExtension(RegistryClient.class);
                assertNotSame(firstClient, secondClient);
                second.start();
                first.close();
                secondClient.pushSync().get(5, TimeUnit.SECONDS);
                assertTrue(second.isOnline());
            }
        }

        // 两个上下文最终各自释放所属会话。
        assertEquals(2, probe.openedSessions());
        assertEquals(2, probe.closedSessions());
    }

    @Test
    public void clientOnlySettingsDoNotPreventIndependentRegistryStorage() throws Exception {
        // 准备对客户端无效、对底层存储无影响的刷新参数。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        registry.setRefreshMillis(0);
        registry.setPublishHost("127.0.0.1");

        // 直接存储访问仍然可用。
        try (StorageAccess storage = registry.openStorage()) {
            assertNotNull(storage);
        }

        // 相同配置用于应用扩展时，在构建阶段报告参数错误。
        MetConfiguration application = createConfiguration() //
                .addExtension(registry);
        application.connector("tcp") //
                .protocol("rsf");
        IllegalStateException failure = assertThrows(IllegalStateException.class, application::buildContext);
        assertEquals("refreshMillis must be positive", failure.getCause().getMessage());
    }

}
