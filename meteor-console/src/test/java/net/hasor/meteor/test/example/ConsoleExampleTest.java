/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.test.example;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.boot.web.WebServer;
import net.hasor.cobble.StringUtils;
import net.hasor.core.AppContext;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.debug.DebugContract;
import net.hasor.meteor.console.component.debug.DebugInvoker;
import net.hasor.meteor.console.component.provider.ProviderConnection;
import net.hasor.meteor.console.component.provider.ProviderFactory;
import net.hasor.meteor.console.component.provider.jdbc.JdbcProviderFactory;
import net.hasor.meteor.console.component.provider.nacos.NacosProviderFactory;
import net.hasor.meteor.console.component.provider.remote.RemoteProviderFactory;
import net.hasor.meteor.console.component.provider.zk.ZookeeperProviderFactory;
import net.hasor.meteor.console.component.rpc.RpcServer;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.example.Consumer1Main;
import net.hasor.meteor.example.Consumer2Main;
import net.hasor.meteor.example.Provider1Main;
import net.hasor.meteor.example.Provider2Main;
import net.hasor.meteor.example.api.*;
import net.hasor.meteor.example.consumer.ServiceConsumer;
import net.hasor.meteor.example.provider.CalculatorServiceImpl;
import net.hasor.meteor.example.provider.EchoServiceImpl;
import net.hasor.meteor.example.provider.StatusServiceImpl;
import net.hasor.meteor.registry.RegistryAdmin;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 验证示例自身的 Boot 装配、角色隔离和经注册发现后的真实调用。 */
public class ConsoleExampleTest {
    @TempDir
    public Path temporary;

    private static final Set<String>                           PROTOCOLS = Set.of("rsf", "dubbo", "thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "grpc", "grpc-web", "connect", "triple");
    private static final Set<Class<? extends ProviderFactory>> FACTORIES = Set.of(JdbcProviderFactory.class, NacosProviderFactory.class, RemoteProviderFactory.class, ZookeeperProviderFactory.class);

    @ParameterizedTest(name = "Boot 提供者 → JDBC 注册中心 → 注入消费者：{0}")
    @ValueSource(strings = { "rsf", "dubbo", "thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "grpc", "grpc-web", "connect", "triple" })
    public void bootExamplesDiscoverAndCallAllServices(String protocol) throws Exception {
        // 每个协议场景使用独立 H2 存储；保留连接直到两个应用退出，避免跨场景残留。
        String database = "console_example_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + database);
        source.setUser("sa");
        try (Connection connection = source.getConnection(); //
             InputStream resource = getClass().getResourceAsStream("/META-INF/meteor/schema/h2.sql"); //
             InputStreamReader script = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, script);
            Server registry = Server.createTcpServer("-tcpPort", "0", "-tcpDaemon", "-ifExists").start();
            try {
                String jdbcUrl = "jdbc:h2:tcp://127.0.0.1:" + registry.getPort() + "/mem:" + database;
                Set<Integer> ports = new HashSet<>();
                Class<?> providerEntry = Provider1Main.class;
                Class<?> consumerEntry = Consumer1Main.class;
                String providerConfiguration = "example-provider1.properties";
                String consumerConfiguration = "example-consumer1.properties";
                if (StringUtils.equals(protocol, "rsf")) {
                    providerEntry = Provider2Main.class;
                    consumerEntry = Consumer2Main.class;
                    providerConfiguration = "example-provider2.properties";
                    consumerConfiguration = "example-consumer2.properties";
                }
                Boot providerBoot = application(providerEntry, providerConfiguration, jdbcUrl, ports);
                Boot consumerBoot = application(consumerEntry, consumerConfiguration, jdbcUrl, ports)//
                        .property("meteor.default-protocol", protocol)//
                        .property("meteor.registry.management-protocols", "rsf");
                // 业务调用使用当前协议；RSF 保留给管理通道，不要求其它协议承载管理契约。
                for (String candidate : PROTOCOLS) {
                    String host = "";
                    if (StringUtils.equals(candidate, protocol) || StringUtils.equals(candidate, "rsf")) {
                        host = "127.0.0.1";
                    }
                    consumerBoot.property("meteor.connectors." + candidate + ".host", host);
                }
                if (!Set.of("rsf", "dubbo", "thrift", "oncrpc", "oncrpc-udp").contains(protocol)) {
                    String context = "/" + protocol;
                    if (StringUtils.equals(protocol, "thrift-http")) {
                        context = "/thrift";
                    }
                    consumerBoot.property("meteor.connectors." + protocol + ".context", context);
                }
                if (Set.of("grpc", "triple").contains(protocol)) {
                    consumerBoot.property("meteor.connectors." + protocol + ".options.httpVersion", 2);
                }
                if (Set.of("thrift", "thrift-http").contains(protocol)) {
                    consumerBoot.property("meteor.connectors." + protocol + ".options.multiplexed", true);
                }

                // 直接加载示例 Module 和配置，发布及代理生成均由 meteor-hasor 完成。
                try (BootApplication provider = providerBoot.start(); //
                     BootApplication consumer = consumerBoot.start()) {
                    AppContext providerApp = provider.getAppContext();
                    AppContext consumerApp = consumer.getAppContext();
                    MetContext server = providerApp.getInstance(MetContext.class);
                    MetContext client = consumerApp.getInstance(MetContext.class);
                    assertEquals(PROTOCOLS, server.runProtocols());
                    Set<String> clientProtocols = new HashSet<>();
                    clientProtocols.add("rsf");
                    clientProtocols.add(protocol);
                    assertEquals(clientProtocols, client.runProtocols());
                    assertEquals(protocol, client.getDefaultProtocol());

                    // 相同扫描范围只定义业务 API；提供者实现不会泄漏到消费者容器。
                    String providerScope = providerApp.getSettings().getString("hasor.loadPackages");
                    String consumerScope = consumerApp.getSettings().getString("hasor.loadPackages");
                    assertEquals("net.hasor.meteor.example", providerScope);
                    assertEquals("net.hasor.meteor.example", consumerScope);
                    assertNull(providerApp.getBindInfo(ConsoleMain.class));
                    assertNull(consumerApp.getBindInfo(ConsoleMain.class));
                    for (Class<? extends ProviderFactory> factory : FACTORIES) {
                        assertNull(providerApp.getBindInfo(factory));
                        assertNull(consumerApp.getBindInfo(factory));
                    }
                    assertTrue(providerApp.findBindingBean(ServiceConsumer.class).isEmpty());
                    assertTrue(consumerApp.findBindingBean(EchoServiceImpl.class).isEmpty());
                    assertTrue(consumerApp.findBindingBean(CalculatorServiceImpl.class).isEmpty());
                    assertTrue(consumerApp.findBindingBean(StatusServiceImpl.class).isEmpty());
                    RegistryAdmin registryAdmin = server.getExtension(RegistryAdmin.class);
                    String instanceId = server.getInstanceID();
                    for (Class<?> service : Set.of(EchoService.class, CalculatorService.class, StatusService.class)) {
                        MetBindInfo<?> published = server.getServiceInfo(service);
                        assertEquals(MetServiceType.Provider, published.getServiceType());
                        assertEquals(MetServiceType.Consumer, client.getServiceInfo(service).getServiceType());
                        // 未限定协议的服务，应在注册中心记录全部 15 个有效协议端点。
                        String[] protocols = registryAdmin.getServiceAdmin(published.getBindID(), instanceId)//
                                .info().getProtocols();
                        assertEquals(PROTOCOLS, Set.of(protocols), published.getBindName());
                    }

                    // JSON-RPC 与 Hessian 共享监听端口，接口挂载路径由各自配置区分。
                    int jsonRpcPort = server.getListenAddress("jsonrpc").getPort();
                    assertEquals(jsonRpcPort, server.getListenAddress("hessian").getPort());
                    assertDoesNotThrow(() -> consumerApp.getInstance(ServiceConsumer.class).call());
                    DebugMessage sample = new DebugMessage();
                    sample.setValue("Hi");
                    sample.setLeft(6);
                    sample.setRight(2);
                    EchoService echo = consumerApp.getInstance(EchoService.class);
                    CalculatorService calculator = consumerApp.getInstance(CalculatorService.class);
                    StatusService status = consumerApp.getInstance(StatusService.class);
                    // 参数演示保留原生多参数签名，不向单消息协议伪装发布。
                    MetBindInfo<?> parameters = server.getServiceInfo(ParameterService.class);
                    Set<String> parameterProtocols = new HashSet<>(PROTOCOLS);
                    parameterProtocols.removeAll(Set.of("grpc", "grpc-web", "connect"));
                    assertEquals(parameterProtocols, Set.of(registryAdmin.getServiceAdmin(parameters.getBindID(), instanceId).info().getProtocols()));
                    if (parameterProtocols.contains(protocol)) {
                        ParameterService parameter = consumerApp.getInstance(ParameterService.class);
                        assertEquals("pong", parameter.ping());
                        assertFalse(parameter.negate(true));
                        assertTrue(parameter.negate(false));
                        assertEquals("first line\nsecond line", parameter.text("first line\nsecond line"));
                        assertEquals("a / b", parameter.join(new String[] { "a", "b" }, " / "));
                        DebugMessage merged = parameter.merge(sample, sample);
                        assertEquals("HiHi", merged.getValue());
                        assertEquals(12, merged.getLeft());
                        assertEquals(4, merged.getRight());
                        assertEquals(42, parameter.sum(20, 22));
                        if (StringUtils.equals("xmlrpc", protocol)) {
                            // XML-RPC 的标准整数是有符号 32 位。
                            assertEquals(42L, parameter.sumLong(20L, 22L));
                        } else {
                            assertEquals(6_000_000_000L, parameter.sumLong(4_000_000_000L, 2_000_000_000L));
                        }
                        assertEquals(3.75, parameter.scale(1.5, 2.5), 0.000001);
                    }
                    assertEquals("HI", echo.uppercase(sample).getValue());
                    assertEquals("Hi".repeat(6), echo.repeat(sample).getValue());
                    assertEquals(4, calculator.subtract(sample).getResult());
                    assertEquals(12, calculator.multiply(sample).getResult());
                    assertEquals(3, calculator.divide(sample).getResult());
                    assertEquals("UP", status.health(sample).getValue());
                    assertTrue(status.runtime(sample).getValue().startsWith("Java "));

                    var admin = registryAdmin.getServiceAdmin(server.getServiceInfo(EchoService.class).getBindID(), instanceId);
                    var member = admin.providers(null, null).getItems().get(0);
                    var endpoint = Arrays.stream(member.getEndpoints()).filter(item -> StringUtils.equals(protocol, item.getProtocolName())).findFirst().orElseThrow();
                    var target = new ExampleTarget();
                    target.setServiceId(member.getService().getServiceId());
                    target.setInstanceId(instanceId);
                    target.setService(member.getService());
                    target.setEndpoint(endpoint);
                    target.setAddress(endpoint.getPublishedAddress());
                    target.setDescription(admin.descriptions().get(instanceId));
                    var contract = new DebugContract(target.getDescription());
                    assertNotSame(EchoService.class, contract.serviceType());
                    Object debug = new DebugInvoker().invoke(target, target.getDescription().getMethods().stream().filter(method -> StringUtils.equals("echo", method.getJavaName())).findFirst().orElseThrow().getJavaSignature(), JsonUtils.readTree("[{\"value\":\"console-debug\",\"left\":20,\"right\":22}]"), 5000, this.temporary.resolve("debug"));
                    assertTrue(JsonUtils.writeValueAsString(debug).contains(":console-debug"));
                    if (StringUtils.equals("rsf", protocol)) {
                        // 调试器根据上报契约创建代理，验证零参数和多个独立输入的组装。
                        var parameterAdmin = registryAdmin.getServiceAdmin(parameters.getBindID(), instanceId);
                        var parameterMember = parameterAdmin.providers(null, null).getItems().get(0);
                        target.setServiceId(parameters.getBindID());
                        target.setService(parameterMember.getService());
                        target.setDescription(parameterAdmin.descriptions().get(instanceId));
                        target.setEndpoint(Arrays.stream(parameterMember.getEndpoints()).filter(item -> StringUtils.equals(protocol, item.getProtocolName())).findFirst().orElseThrow());
                        target.setAddress(target.getEndpoint().getPublishedAddress());
                        var inputs = java.util.Map.of("ping", "[]", "sum", "[20,22]", "merge", "[{\"value\":\"a\",\"left\":10},{\"value\":\"b\",\"right\":20}]");
                        for (var input : inputs.entrySet()) {
                            String methodName = input.getKey();
                            var method = target.getDescription().getMethods().stream().filter(item -> StringUtils.equals(methodName, item.getJavaName())).findFirst().orElseThrow();
                            var result = JsonUtils.valueToTree(new DebugInvoker().invoke(target, method.getJavaSignature(), JsonUtils.readTree(input.getValue()), 5000, this.temporary.resolve("parameter-debug")));
                            if (StringUtils.equals("ping", methodName)) {
                                assertEquals("pong", result.asText());
                            } else if (StringUtils.equals("sum", methodName)) {
                                assertEquals(42, result.asInt());
                            } else {
                                assertEquals("ab", result.get("value").asText());
                                assertEquals(10, result.get("left").asInt());
                                assertEquals(20, result.get("right").asInt());
                            }
                        }
                    }
                }
            } finally {
                registry.stop();
            }
        }
    }

    @Test
    public void registryUsesProductionScanScopeAndProvidesRemoteManagement() throws Exception {
        // 使用注册中心示例配置和独立数据库，端口及数据目录与手工调试进程隔离。
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:registry_example_" + UUID.randomUUID());
        source.setUser("sa");
        try (Connection connection = source.getConnection(); //
             InputStream resource = getClass().getResourceAsStream("/META-INF/meteor/schema/h2.sql"); //
             InputStreamReader script = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, script);
            Boot boot = new Boot().sources(ConsoleMain.class) //
                    .hconfigFile("example-registry.properties") //
                    .property("meteor.storage.address", source.getURL()) //
                    .property("meteor.rpc.port", 0) //
                    .property("meteor.data.directory", this.temporary.resolve("registry").toString());
            try (BootApplication application = boot.start()) {
                AppContext app = application.getAppContext();

                // Boot 从正式入口推导扫描范围，全部工厂可发现且不会启动业务示例。
                assertEquals("net.hasor.meteor.console", app.getSettings().getString("hasor.loadPackages"));
                for (Class<? extends ProviderFactory> factory : FACTORIES) {
                    assertNotNull(app.getBindInfo(factory));
                }
                assertTrue(app.findBindingBean(ServiceConsumer.class).isEmpty());
                assertTrue(app.findBindingBean(EchoServiceImpl.class).isEmpty());
                assertFalse(app.getSettings().getBoolean("hasor.boot.web.connectors.http.enabled"));

                // 通过实际管理 RPC 访问 H2 注册中心，证明连接工厂与无头管理服务均已就绪。
                ClusterProfile profile = new ClusterProfile();
                profile.setName("registry-example");
                profile.setType("remote");
                profile.setAddress(app.getInstance(RpcServer.class).getAddress());
                profile.setManagementToken("console-debug");
                RemoteProviderFactory factory = app.getInstance(RemoteProviderFactory.class);
                try (ProviderConnection remote = factory.open(profile, () -> "console-debug")) {
                    assertTrue(remote.registry().getClusterAdmin().status().isReachable());
                }
            }
        }
    }

    @Test
    public void consoleUsesProductionScanScopeAndRegistersWeb() throws Exception {
        // 正式控制台自动扫描生产组件，HTTP 使用独立随机端口。
        Boot boot = new Boot().sources(ConsoleMain.class)//
                .hconfigFile("meteor.properties")//
                .property("hasor.boot.web.connectors.http.port", 0)//
                .property("meteor.data.directory", this.temporary.resolve("console").toString());
        try (BootApplication application = boot.start()) {
            AppContext app = application.getAppContext();
            assertEquals("net.hasor.meteor.console", app.getSettings().getString("hasor.loadPackages"));
            for (Class<? extends ProviderFactory> factory : FACTORIES) {
                assertNotNull(app.getBindInfo(factory));
            }
            assertTrue(app.findBindingBean(ServiceConsumer.class).isEmpty());
            assertTrue(app.findBindingBean(EchoServiceImpl.class).isEmpty());
            assertNull(app.getInstance(MetContext.class).getServiceInfo(EchoService.class));

            // 经真实 HTTP 请求验证路由与 JSON 装配，控制台不会启动示例提供者或消费者。
            int port = app.getInstance(WebServer.class).getPort();
            URI address = URI.create("http://127.0.0.1:" + port + "/api/auth/session");
            HttpRequest request = HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(5)).GET().build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertFalse(JsonUtils.readTree(response.body()).path("securityEnabled").asBoolean());

            // 示例查询、文档和远程调试路由都必须登记；参数错误返回 JSON，不能落入 Tomcat 404。
            for (String route : Set.of("examples/targets", "examples/render", "service/debug")) {
                URI endpoint = URI.create("http://127.0.0.1:" + port + "/api/" + route);
                HttpRequest query = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> result = client.send(query, HttpResponse.BodyHandlers.ofString());
                assertEquals(400, result.statusCode(), route + ": " + result.body());
                assertEquals("缺少参数：mode", JsonUtils.readTree(result.body()).path("error").asText());
            }

            URI debugEndpoint = URI.create("http://127.0.0.1:" + port + "/api/service/debug");
            HttpRequest invocation = HttpRequest.newBuilder(debugEndpoint).timeout(Duration.ofSeconds(5))//
                    .header("Content-Type", "application/json")//
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            HttpResponse<String> rejected = client.send(invocation, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, rejected.statusCode(), rejected.body());
            assertEquals("缺少参数：mode", JsonUtils.readTree(rejected.body()).path("error").asText());
        }
    }

    private Boot application(Class<?> source, String configuration, String jdbcUrl, Set<Integer> ports) throws Exception {
        Boot boot = new Boot().sources(source)//
                .hconfigFile(configuration)//
                .property("example.jdbc.url", jdbcUrl)//
                .property("example.jdbc.username", "sa")//
                .property("example.jdbc.password", "")//
                .property("meteor.data-home", this.temporary.resolve(source.getSimpleName()).toString())//
                .property("hasor.boot.web.connectors.http.enabled", false);

        // 非零端口让不同协议保持独立 Connector；两个 HTTP 协议明确共用一个端口。
        int sharedHttpPort = availablePort(ports);
        for (String protocol : PROTOCOLS) {
            int port = availablePort(ports);
            if (StringUtils.equals(protocol, "jsonrpc") || StringUtils.equals(protocol, "hessian")) {
                port = sharedHttpPort;
            }
            boot.property("meteor.connectors." + protocol + ".port", port);
        }
        return boot;
    }

    private int availablePort(Set<Integer> used) throws Exception {
        // 避开客户端临时端口段，避免启动中的注册连接抢占稍后才绑定的测试监听端口。
        for (int attempt = 0; attempt < 1000; attempt++) {
            int port = ThreadLocalRandom.current().nextInt(10000, 30000);
            if (used.contains(port))
                continue;
            try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
                used.add(port);
                return port;
            } catch (BindException occupied) {
                // 当前端口由其它进程使用，继续选择。
            }
        }
        throw new IllegalStateException("无法分配示例测试端口");
    }
}
