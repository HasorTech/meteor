/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.zookeeper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import net.hasor.meteor.registry.shared.storage.domain.*;
import net.hasor.meteor.registry.shared.utils.RegistryJson;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.registry.zookeeper.ZookeeperRecords.instance;
import static net.hasor.meteor.registry.zookeeper.ZookeeperRecords.service;
import static org.junit.jupiter.api.Assertions.*;

/** 登记身份、会话所有权、临时成员与持久目录的边界。 */
public class ZookeeperRegistrationTest {
    @Test
    public void instanceEndpointsSurviveRoundTripAcrossSessions() throws Exception {
        // 嵌入 ZooKeeper，分别打开登记和读取会话。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess writer = configuration.openStorage(); //
                 StorageAccess reader = configuration.openStorage()) {
                EntInstanceRuntimeData registered = instance("one", "orders", "east");
                EntInstanceEndpointData publicEndpoint = new EntInstanceEndpointData();
                publicEndpoint.setProtocolName("jsonrpc");
                publicEndpoint.getEndpoints().put("endpoint", "orders-rpc://127.0.0.1:8080/orders");
                EntInstanceEndpointData internalEndpoint = new EntInstanceEndpointData();
                internalEndpoint.setProtocolName("jsonrpc");
                internalEndpoint.getEndpoints().put("endpoint", "jsonrpc://127.0.0.1:8081/internal");
                internalEndpoint.getEndpoints().put("management", "jsonrpc://127.0.0.1:8081/internal/management");
                EntInstanceEndpointData[] expected = { publicEndpoint, internalEndpoint };
                publicEndpoint.getEndpoints().put("schema", "");
                registered.setEndpoints(expected);
                writer.registerInstance(registered);

                // ZooKeeper 正文往返后保留所有端点，不以 scheme 代替协议名。
                EntInstanceRuntimeData runtime = reader.getInstance("one").getRuntime();
                EntInstanceEndpointData[] actual = runtime.getEndpoints();
                assertEquals(2, actual.length);
                // 按登记顺序逐字段核对，不能将同协议的两个连接器合并。
                for (int index = 0; index < expected.length; index++) {
                    assertEquals(expected[index].getProtocolName(), actual[index].getProtocolName());
                    assertEquals(expected[index].getEndpoints(), actual[index].getEndpoints());
                }
            }
        }
    }

    @Test
    public void zeroContractServiceIdSurvivesStorageRoundTrip() throws Exception {
        // 契约编号为零保存在实例描述中，字符串服务 ID 继续用于目录定位。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("contractServiceId", 0);
                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 实例描述保留零值，不改写登记身份。
                EntServiceRuntimeData registered = storage.getInstanceService("one", "contract");
                ObjectNode actualDescription = RegistryJson.copy(registered.getDescription(), ObjectNode.class);
                assertEquals(0L, actualDescription.get("contractServiceId").longValue());
                assertEquals("contract", registered.getServiceId());
            }
        }
    }

    @Test
    public void negativeContractServiceIdSurvivesStorageRoundTrip() throws Exception {
        // 存储保留公共数值声明，具体协议的合法编号范围由协议扩展校验。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("contractServiceId", -1);
                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 负值在描述中原样读回，不与未声明编号混淆。
                EntServiceRuntimeData registered = storage.getInstanceService("one", "contract");
                ObjectNode actualDescription = RegistryJson.copy(registered.getDescription(), ObjectNode.class);
                assertEquals(-1L, actualDescription.get("contractServiceId").longValue());
            }
        }
    }

    @Test
    public void unsignedContractServiceIdSurvivesStorageRoundTrip() throws Exception {
        // ONC 程序号的无符号 32 位上界不能在存储时缩为 Java int。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("contractServiceId", 0xffffffffL);
                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 成员查询沿用本实例的描述，返回完整 long 数值。
                RefServiceRelationData member = storage.getMember("contract", MetServiceType.Provider, "one");
                ObjectNode actualDescription = RegistryJson.copy(member.getService().getDescription(), ObjectNode.class);
                assertEquals(0xffffffffL, actualDescription.get("contractServiceId").longValue());
            }
        }
    }

    @Test
    public void nullContractServiceIdSurvivesDescriptionRoundTrip() throws Exception {
        // 描述中的显式 null 编号与缺少整个描述不同。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.putNull("contractServiceId");
                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 实例查询和成员查询都保留 null 节点，不擅自补编号或移除字段。
                ObjectNode registered = RegistryJson.copy(storage.getInstanceService("one", "contract").getDescription(), ObjectNode.class);
                RefServiceRelationData member = storage.getMember("contract", MetServiceType.Provider, "one");
                ObjectNode memberDescription = RegistryJson.copy(member.getService().getDescription(), ObjectNode.class);
                assertTrue(registered.has("contractServiceId"));
                assertTrue(registered.get("contractServiceId").isNull());
                assertEquals(registered, memberDescription);
            }
        }
    }

    @Test
    public void contractServiceIdsStayWithTheirOwnInstance() throws Exception {
        // 同一服务的两个实例分别在描述中声明编号与显式 null。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("numbered", "orders", "east"));
                storage.registerInstance(instance("unnumbered", "orders", "east"));
                EntServiceRuntimeData numbered = service("contract", MetServiceType.Provider);
                ObjectNode numberedDescription = JsonNodeFactory.instance.objectNode();
                numberedDescription.put("contractServiceId", 0xffffffffL);
                numbered.setDescription(RegistryJson.copy(numberedDescription, RefServiceDescriptionData.class));
                storage.registerService("numbered", numbered);
                EntServiceRuntimeData unnumbered = service("contract", MetServiceType.Provider);
                ObjectNode unnumberedDescription = JsonNodeFactory.instance.objectNode();
                unnumberedDescription.putNull("contractServiceId");
                unnumbered.setDescription(RegistryJson.copy(unnumberedDescription, RefServiceDescriptionData.class));
                storage.registerService("unnumbered", unnumbered);

                // 共享目录不能覆盖各实例自己的描述，空编号不会继承另一实例的值。
                EntServiceRuntimeData first = storage.getInstanceService("numbered", "contract");
                EntServiceRuntimeData second = storage.getInstanceService("unnumbered", "contract");
                ObjectNode firstDescription = RegistryJson.copy(first.getDescription(), ObjectNode.class);
                ObjectNode secondDescription = RegistryJson.copy(second.getDescription(), ObjectNode.class);
                assertEquals(0xffffffffL, firstDescription.get("contractServiceId").longValue());
                assertTrue(secondDescription.has("contractServiceId"));
                assertTrue(secondDescription.get("contractServiceId").isNull());
            }
        }
    }

    @Test
    public void serviceDescriptionIsReplacedAsOneInstanceRecord() throws Exception {
        // 准备一份带方法及类型信息的实例服务登记。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess writer = configuration.openStorage(); //
                 StorageAccess reader = configuration.openStorage()) {
                writer.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("description", MetServiceType.Provider, "rsf://127.0.0.1:8000");
                ObjectNode first = JsonNodeFactory.instance.objectNode();
                first.put("contractServiceId", 0);
                first.putArray("methods").addObject().put("name", "Before");
                first.putObject("types").putObject("Order").put("name", "Order");
                service.setDescription(RegistryJson.copy(first, RefServiceDescriptionData.class));
                writer.registerService("one", service);
                ObjectNode captured = RegistryJson.copy(reader.getInstanceService("one", "description").getDescription(), ObjectNode.class);
                assertEquals(RegistryJson.copy(RegistryJson.copy(first, RefServiceDescriptionData.class), ObjectNode.class), captured);

                // 整体替换编号、方法和类型，不影响已读取的旧描述。
                ObjectNode replacement = JsonNodeFactory.instance.objectNode();
                replacement.put("contractServiceId", 0xffffffffL);
                replacement.putArray("methods").addObject().put("name", "After");
                replacement.putObject("types");
                service.setDescription(RegistryJson.copy(replacement, RefServiceDescriptionData.class));
                writer.registerService("one", service);
                ObjectNode current = RegistryJson.copy(reader.getMember("description", MetServiceType.Provider, "one") //
                        .getService().getDescription(), ObjectNode.class);
                assertEquals(RegistryJson.copy(RegistryJson.copy(replacement, RefServiceDescriptionData.class), ObjectNode.class), current);
                assertEquals(0xffffffffL, current.get("contractServiceId").longValue());
                assertEquals(RegistryJson.copy(RegistryJson.copy(first, RefServiceDescriptionData.class), ObjectNode.class), captured);
                assertEquals(0L, captured.get("contractServiceId").longValue());
            }
        }
    }

    @Test
    public void replacementDescriptionRemovesPreviousContractServiceId() throws Exception {
        // 初次描述有编号，替换描述只保留新的方法声明。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode first = JsonNodeFactory.instance.objectNode();
                first.put("contractServiceId", 0xffffffffL);
                service.setDescription(RegistryJson.copy(first, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 缺少编号的新描述整体覆盖旧描述，不合并保留上一次编号。
                ObjectNode replacement = JsonNodeFactory.instance.objectNode();
                replacement.putArray("methods").addObject().put("name", "After");
                service.setDescription(RegistryJson.copy(replacement, RefServiceDescriptionData.class));
                storage.registerService("one", service);
                ObjectNode registered = RegistryJson.copy(storage.getInstanceService("one", "contract").getDescription(), ObjectNode.class);
                RefServiceRelationData member = storage.getMember("contract", MetServiceType.Provider, "one");
                assertEquals(RegistryJson.copy(RegistryJson.copy(replacement, RefServiceDescriptionData.class), ObjectNode.class), registered);
                assertFalse(registered.hasNonNull("contractServiceId"));
                assertEquals(RegistryJson.copy(RegistryJson.copy(replacement, RefServiceDescriptionData.class), ObjectNode.class), RegistryJson.copy(member.getService().getDescription(), ObjectNode.class));
            }
        }
    }

    @Test
    public void clearingDescriptionRemovesPreviousContractServiceId() throws Exception {
        // 首次登记保留有编号的描述，后续将整个描述清空。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode first = JsonNodeFactory.instance.objectNode();
                first.put("contractServiceId", 0xffffffffL);
                service.setDescription(RegistryJson.copy(first, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 清空描述同时移除旧编号，实例与成员查询都不能保留旧描述。
                service.setDescription(null);
                storage.registerService("one", service);
                EntServiceRuntimeData registered = storage.getInstanceService("one", "contract");
                RefServiceRelationData member = storage.getMember("contract", MetServiceType.Provider, "one");
                assertNull(RegistryJson.copy(registered.getDescription(), ObjectNode.class));
                assertNull(RegistryJson.copy(member.getService().getDescription(), ObjectNode.class));
            }
        }
    }

    @Test
    public void storedServiceBodiesKeepContractServiceIdOnlyInRuntimeDescription() throws Exception {
        // 通过正式存储入口登记带描述编号的实例服务。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            ExponentialBackoffRetry retry = new ExponentialBackoffRetry(50, 3);
            try (StorageAccess storage = configuration.openStorage(); //
                 CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), retry)) {
                client.start();
                assertTrue(client.blockUntilConnected(5, TimeUnit.SECONDS));
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider);
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("contractServiceId", 0xffffffffL);
                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                storage.registerService("one", service);

                // 直接读取目录、实例正文与反向引用，防止 DTO 查询隐藏多写的字段。
                String serviceRoot = "/meteor/services/Y29udHJhY3Q";
                ObjectNode catalog = readBody(client, serviceRoot + "/info");
                assertFalse(catalog.hasNonNull("contractServiceId"));
                assertFalse(catalog.has("description"));
                ObjectNode runtime = readBody(client, "/meteor/instances/b25l/services/Y29udHJhY3Q");
                assertFalse(runtime.hasNonNull("contractServiceId"));
                assertEquals(RegistryJson.copy(RegistryJson.copy(description, RefServiceDescriptionData.class), ObjectNode.class), runtime.get("description"));
                ObjectNode reference = readBody(client, serviceRoot + "/providers/b25l");
                assertEquals(1, reference.size());
                assertEquals("one", reference.get("instanceId").textValue());
                assertFalse(reference.hasNonNull("contractServiceId"));

                // 公开成员结果中的服务正文也只在 description 中暴露契约编号。
                RefServiceRelationData member = storage.getMember("contract", MetServiceType.Provider, "one");
                ObjectNode memberJson = RegistryJson.copy(member, ObjectNode.class);
                assertFalse(memberJson.hasNonNull("contractServiceId"));
                assertFalse(memberJson.get("service").hasNonNull("contractServiceId"));
                assertEquals(RegistryJson.copy(RegistryJson.copy(description, RefServiceDescriptionData.class), ObjectNode.class), memberJson.get("service").get("description"));
            }
        }
    }

    @Test
    public void storedServiceBodiesKeepJavaTypeOnlyInInstanceDescription() throws Exception {
        // 正式存储入口登记实例声明，两个连接器使用相同的真实协议名。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            ExponentialBackoffRetry retry = new ExponentialBackoffRetry(50, 3);
            try (StorageAccess storage = configuration.openStorage(); //
                 CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), retry)) {
                client.start();
                assertTrue(client.blockUntilConnected(5, TimeUnit.SECONDS));
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("contract", MetServiceType.Provider, //
                        "rsf://127.0.0.1:8000", "rsf://127.0.0.1:9000");
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("javaType", "sample.OrderService");
                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                RefProtocolMountData primary = new RefProtocolMountData();
                primary.setContextPath("/primary");
                primary.setProtocolName("rsf");
                primary.setPublishedAddress("rsf://127.0.0.1:8000");
                RefProtocolMountData backup = new RefProtocolMountData();
                backup.setContextPath("/backup");
                backup.setProtocolName("rsf");
                backup.setPublishedAddress("rsf://127.0.0.1:9000");
                service.setEndpoints(new RefProtocolMountData[] { primary, backup });
                storage.registerService("one", service);

                // 原始目录不写 Java 类型，运行角色仍保留在服务正文顶层。
                ObjectNode catalog = readBody(client, "/meteor/services/Y29udHJhY3Q/info");
                assertFalse(catalog.has("type"));
                assertFalse(catalog.hasNonNull("javaType"));
                assertFalse(catalog.has("description"));
                ObjectNode runtime = readBody(client, "/meteor/instances/b25l/services/Y29udHJhY3Q");
                assertFalse(runtime.has("type"));
                assertFalse(runtime.hasNonNull("javaType"));
                assertEquals("Provider", runtime.get("role").textValue());
                assertEquals(RegistryJson.copy(RegistryJson.copy(description, RefServiceDescriptionData.class), ObjectNode.class), runtime.get("description"));
                assertFalse(runtime.get("description").has("role"));

                // 每个端点只保存 protocolName，实际发布地址区分同协议的两个挂载。
                JsonNode endpoints = runtime.get("endpoints");
                assertEquals(2, endpoints.size());
                assertEquals("rsf://127.0.0.1:8000", endpoints.get(0).get("publishedAddress").textValue());
                assertEquals("rsf://127.0.0.1:9000", endpoints.get(1).get("publishedAddress").textValue());
                for (JsonNode endpoint : endpoints) {
                    assertEquals("rsf", endpoint.get("protocolName").textValue());
                    assertFalse(endpoint.has("protocol"));
                    assertFalse(endpoint.has("connectorName"));
                    assertFalse(endpoint.has("bindEnabled"));
                    assertFalse(endpoint.has("configuredAddress"));
                }
                ServiceSummary summary = storage.serviceSummaries(List.of("contract")).get("contract");
                assertArrayEquals(new String[] { "rsf" }, summary.getProtocols());
            }
        }
    }

    @Test
    public void javaTypeDescriptionsRemainIndependentAcrossInstances() throws Exception {
        // 同一服务由两个不同 Java 声明和一个未声明 Java 类型的实例提供。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                storage.registerInstance(instance("two", "orders", "east"));
                storage.registerInstance(instance("three", "orders", "east"));
                EntServiceRuntimeData first = service("contract", MetServiceType.Provider);
                ObjectNode firstDescription = JsonNodeFactory.instance.objectNode();
                firstDescription.put("javaType", "sample.FirstOrderService");
                first.setDescription(RegistryJson.copy(firstDescription, RefServiceDescriptionData.class));
                storage.registerService("one", first);
                EntServiceRuntimeData second = service("contract", MetServiceType.Provider);
                ObjectNode secondDescription = JsonNodeFactory.instance.objectNode();
                secondDescription.put("javaType", "sample.SecondOrderService");
                second.setDescription(RegistryJson.copy(secondDescription, RefServiceDescriptionData.class));
                storage.registerService("two", second);
                EntServiceRuntimeData untyped = service("contract", MetServiceType.Provider);
                ObjectNode untypedDescription = JsonNodeFactory.instance.objectNode();
                untypedDescription.put("name", "orders");
                untyped.setDescription(RegistryJson.copy(untypedDescription, RefServiceDescriptionData.class));
                storage.registerService("three", untyped);

                // 实例与成员查询都只返回自己的完整描述，空缺类型不会从共享目录回填。
                assertEquals(RegistryJson.copy(RegistryJson.copy(firstDescription, RefServiceDescriptionData.class), ObjectNode.class), RegistryJson.copy(storage.getInstanceService("one", "contract").getDescription(), ObjectNode.class));
                assertEquals(RegistryJson.copy(RegistryJson.copy(secondDescription, RefServiceDescriptionData.class), ObjectNode.class), RegistryJson.copy(storage.getInstanceService("two", "contract").getDescription(), ObjectNode.class));
                RefServiceRelationData firstMember = storage.getMember("contract", MetServiceType.Provider, "one");
                RefServiceRelationData secondMember = storage.getMember("contract", MetServiceType.Provider, "two");
                RefServiceRelationData untypedMember = storage.getMember("contract", MetServiceType.Provider, "three");
                assertEquals(RegistryJson.copy(RegistryJson.copy(firstDescription, RefServiceDescriptionData.class), ObjectNode.class), RegistryJson.copy(firstMember.getService().getDescription(), ObjectNode.class));
                assertEquals(RegistryJson.copy(RegistryJson.copy(secondDescription, RefServiceDescriptionData.class), ObjectNode.class), RegistryJson.copy(secondMember.getService().getDescription(), ObjectNode.class));
                assertEquals(RegistryJson.copy(RegistryJson.copy(untypedDescription, RefServiceDescriptionData.class), ObjectNode.class), RegistryJson.copy(untypedMember.getService().getDescription(), ObjectNode.class));
                assertFalse(RegistryJson.copy(untypedMember.getService().getDescription(), ObjectNode.class).hasNonNull("javaType"));
            }
        }
    }

    @Test
    public void catalogUpdatePreservesInstanceDescriptionAndEndpoints() throws Exception {
        // 同一服务的两个实例具有各自的运行描述和地址。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            ExponentialBackoffRetry retry = new ExponentialBackoffRetry(50, 3);
            try (StorageAccess storage = configuration.openStorage(); //
                 CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), retry)) {
                client.start();
                assertTrue(client.blockUntilConnected(5, TimeUnit.SECONDS));
                storage.registerInstance(instance("one", "orders", "east"));
                storage.registerInstance(instance("two", "orders", "west"));
                EntServiceRuntimeData first = service("contract", MetServiceType.Provider, "rsf://127.0.0.1:8000");
                ObjectNode description = JsonNodeFactory.instance.objectNode();
                description.put("javaType", "sample.OrderService");
                first.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                RefProtocolMountData endpoint = new RefProtocolMountData();
                endpoint.setContextPath("/tcp");
                endpoint.setProtocolName("rsf");
                endpoint.setPublishedAddress("rsf://127.0.0.1:8000");
                first.setEndpoints(new RefProtocolMountData[] { endpoint });
                storage.registerService("one", first);
                EntServiceRuntimeData second = service("contract", MetServiceType.Provider, "rsf://127.0.0.1:9000");
                storage.registerService("two", second);
                String firstPath = "/meteor/instances/b25l/services/Y29udHJhY3Q";
                String secondPath = "/meteor/instances/dHdv/services/Y29udHJhY3Q";
                ObjectNode firstBefore = readBody(client, firstPath);
                ObjectNode secondBefore = readBody(client, secondPath);

                // 局部更新持久目录名称，不能将实例描述复制到目录或改写任一运行正文。
                EntServiceCatalogData update = new EntServiceCatalogData();
                update.setServiceId("contract");
                update.setName("RenamedOrders");
                storage.saveService(update);
                ObjectNode catalog = readBody(client, "/meteor/services/Y29udHJhY3Q/info");
                assertEquals("RenamedOrders", catalog.get("name").textValue());
                assertFalse(catalog.has("type"));
                assertFalse(catalog.hasNonNull("javaType"));
                assertFalse(catalog.has("description"));
                assertEquals(firstBefore, readBody(client, firstPath));
                assertEquals(secondBefore, readBody(client, secondPath));
            }
        }
    }

    @Test
    public void addressOnlyProviderRemainsDiscoverableWithoutEndpoints() throws Exception {
        // 存储允许只发布地址的提供者，不强制补充协议端点。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                String address = "rsf://127.0.0.1:8000";
                storage.registerService("one", service("contract", MetServiceType.Provider, address));

                // 服务成员保持地址，查询结果不凭地址生成端点或协议摘要。
                RecordPage<RefServiceRelationData> providers = storage.members("contract", MetServiceType.Provider, //
                        new DirectoryQuery());
                assertEquals(1, providers.getItems().size());
                EntServiceRuntimeData registered = providers.getItems().get(0).getService();
                assertArrayEquals(new String[] { address }, registered.getAddresses());
                assertEquals(0, registered.getEndpoints().length);
                ServiceSummary summary = storage.serviceSummaries(List.of("contract")).get("contract");
                assertEquals(0, summary.getProtocols().length);
            }
        }
    }

    @Test
    public void persistentDirectoriesSurviveSessionClose() throws Exception {
        // 同一注册中心分别打开登记会话和查询会话。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess reader = configuration.openStorage()) {
                try (StorageAccess writer = configuration.openStorage()) {
                    writer.registerInstance(instance("one", "orders", "east"));
                    EntServiceRuntimeData service = service("A", MetServiceType.Provider, "rsf://127.0.0.1:8000");
                    writer.registerService("one", service);

                    // 先确认两个方向都有完整声明，避免把未登记误判为关闭后消失。
                    assertEquals("A", reader.getInstanceService("one", "A").getName());
                    RefServiceRelationData member = reader.getMember("A", MetServiceType.Provider, "one");
                    assertEquals("orders", member.getInstance().getApplicationName());
                    assertEquals(Long.valueOf(123), member.getInstance().getProcessId());
                    assertEquals("A", member.getService().getName());
                }

                // 会话关闭后仅删除临时登记，应用、实例和服务档案仍可点查。
                assertNotNull(reader.getApplication("orders"));
                assertNotNull(reader.getInstance("one").getInstance());
                assertNull(reader.getInstance("one").getRuntime());
                assertNotNull(reader.getService("A"));
                assertNull(reader.getInstanceService("one", "A"));
                assertNull(reader.getMember("A", MetServiceType.Provider, "one"));
                int archivedInstances = reader.instances("orders", new DirectoryQuery()).getItems().size();
                assertEquals(1, archivedInstances);
            }
        }
    }

    @Test
    public void rollingUpgradeOnlyDiscoversInstancesActuallyProvidingTheService() throws Exception {
        // 模拟滚动升级：旧实例只提供 A，新实例同时提供 A 和 B。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess old = configuration.openStorage(); //
                 StorageAccess next = configuration.openStorage(); //
                 StorageAccess reader = configuration.openStorage()) {
                old.registerInstance(instance("old", "orders", "east"));
                EntServiceRuntimeData oldService = service("A", MetServiceType.Provider, "rsf://127.0.0.1:8000");
                old.registerService("old", oldService);
                next.registerInstance(instance("new", "orders", "east"));
                EntServiceRuntimeData shared = service("A", MetServiceType.Provider, "rsf://127.0.0.1:9000");
                EntServiceRuntimeData added = service("B", MetServiceType.Provider, "rsf://127.0.0.1:9000");
                next.registerService("new", shared);
                next.registerService("new", added);

                // 按 B 发现时只有新实例，不能仅按应用地址把旧实例也算进来。
                DirectoryQuery query = new DirectoryQuery();
                RecordPage<RefServiceRelationData> providers = reader.members("B", MetServiceType.Provider, query);
                assertEquals(1, providers.getItems().size());
                assertEquals("new", providers.getItems().get(0).getInstance().getInstanceId());
                assertNull(reader.getInstanceService("old", "B"));
                int sharedProviders = reader.members("A", MetServiceType.Provider, query).getItems().size();
                assertEquals(2, sharedProviders);
            }
        }
    }

    @Test
    public void replacedSessionCannotRegisterInstanceAgain() throws Exception {
        // 新会话接管相同实例 ID，旧会话不能再次抢回登记所有权。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess old = configuration.openStorage(); //
                 StorageAccess next = configuration.openStorage()) {
                old.registerInstance(instance("one", "orders", "east"));
                next.registerInstance(instance("one", "orders", "east"));

                assertThrows(IllegalStateException.class, () -> {
                    old.registerInstance(instance("one", "orders", "east"));
                });
                assertNotNull(next.getInstance("one").getRuntime());
            }
        }
    }

    @Test
    public void replacedSessionCannotWithdrawNewService() throws Exception {
        // 新会话接管实例后发布自己的服务。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess old = configuration.openStorage(); //
                 StorageAccess next = configuration.openStorage()) {
                old.registerInstance(instance("one", "orders", "east"));
                next.registerInstance(instance("one", "orders", "east"));
                next.registerService("one", service("B", MetServiceType.Provider));

                // 旧会话撤销同一个实例的服务时，必须暴露所有权冲突。
                assertThrows(IllegalStateException.class, () -> old.unregisterService("one", "B"));
                assertNotNull(next.getMember("B", MetServiceType.Provider, "one"));
            }
        }
    }

    @Test
    public void replacedSessionCloseKeepsOnlyNewRegistrations() throws Exception {
        // 同一个实例先后由两个会话登记，分别提供不同服务。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess old = configuration.openStorage(); //
                 StorageAccess next = configuration.openStorage()) {
                old.registerInstance(instance("one", "orders", "east"));
                old.registerService("one", service("A", MetServiceType.Provider));
                next.registerInstance(instance("one", "orders", "east"));
                next.registerService("one", service("B", MetServiceType.Provider));

                // 关闭旧会话不能删除接管者的临时节点，也不能保留旧服务。
                old.close();
                assertNotNull(next.getInstance("one").getRuntime());
                assertNotNull(next.getMember("B", MetServiceType.Provider, "one"));
                assertNull(next.getMember("A", MetServiceType.Provider, "one"));
            }
        }
    }

    @Test
    public void roleChangeReplacesBothDirectionsWithoutChangingOtherServices() throws Exception {
        // 同一实例最初发布两个服务。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                storage.registerService("one", service("A", MetServiceType.Provider));
                storage.registerService("one", service("B", MetServiceType.Provider));

                // 仅把 A 改为消费身份，实例清单和服务成员必须同步更新。
                storage.registerService("one", service("A", MetServiceType.Consumer));
                assertNull(storage.getMember("A", MetServiceType.Provider, "one"));
                assertNotNull(storage.getMember("A", MetServiceType.Consumer, "one"));
                assertEquals(MetServiceType.Consumer, storage.getInstanceService("one", "A").getRole());
                assertNotNull(storage.getMember("B", MetServiceType.Provider, "one"));
            }
        }
    }

    @Test
    public void withdrawalRemovesOnlySelectedRuntimeService() throws Exception {
        // 实例同时消费 A 并发布 B，两个服务都有持久档案。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                storage.registerService("one", service("A", MetServiceType.Consumer));
                storage.registerService("one", service("B", MetServiceType.Provider));

                // 撤销 A 仅删除该实例的登记，不删除目录或无关服务。
                storage.unregisterService("one", "A");
                assertNull(storage.getInstanceService("one", "A"));
                assertNull(storage.getMember("A", MetServiceType.Consumer, "one"));
                assertNotNull(storage.getService("A"));
                assertNotNull(storage.getMember("B", MetServiceType.Provider, "one"));
                int remaining = storage.instanceServices("one", new DirectoryQuery()).getItems().size();
                assertEquals(1, remaining);
            }
        }
    }

    @Test
    public void memberQueryReturnsDetachedIdentityAndDeclaration() throws Exception {
        // 登记可修改的实例身份与服务地址正文。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                EntServiceRuntimeData service = service("A", MetServiceType.Provider, "rsf://127.0.0.1:8000");
                storage.registerService("one", service);

                // 修改返回值不能悄悄回写存储中的任一正文。
                RefServiceRelationData read = storage.getMember("A", MetServiceType.Provider, "one");
                read.getInstance().setIpAddress("changed");
                read.getService().setAddresses(new String[0]);
                RefServiceRelationData unchanged = storage.getMember("A", MetServiceType.Provider, "one");
                assertEquals("127.0.0.1", unchanged.getInstance().getIpAddress());
                assertEquals(1, unchanged.getService().getAddresses().length);
            }
        }
    }

    @Test
    public void serviceSummaryReflectsProviderWithdrawal() throws Exception {
        // 查询同一服务的登记前后数量，确认统计依据实时成员而非持久目录。
        try (TestingServer server = new TestingServer()) {
            ZookeeperRegistryConfiguration configuration = new ZookeeperRegistryConfiguration();
            configuration.setAddress(server.getConnectString());
            try (StorageAccess storage = configuration.openStorage()) {
                storage.registerInstance(instance("one", "orders", "east"));
                storage.registerService("one", service("A", MetServiceType.Provider));
                ServiceSummary registered = storage.serviceSummaries(List.of("A")).get("A");
                assertEquals(1, registered.getProviderCount());

                storage.unregisterService("one", "A");
                ServiceSummary withdrawn = storage.serviceSummaries(List.of("A")).get("A");
                assertEquals(0, withdrawn.getProviderCount());
            }
        }
    }

    private static ObjectNode readBody(CuratorFramework client, String path) throws Exception {
        byte[] body = client.getData().forPath(path);
        return RegistryJson.read(new String(body, StandardCharsets.UTF_8), ObjectNode.class);
    }

}
