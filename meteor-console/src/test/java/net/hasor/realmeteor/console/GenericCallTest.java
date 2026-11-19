/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.console;
import java.io.File;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.annotation.MetField;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class GenericCallTest {
    @TempDir
    public Path directory;

    @Test
    public void rsfJsonWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("tcp", "rsf");
    }

    @Test
    public void jsonRpcWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "jsonrpc");
    }

    @Test
    public void xmlRpcWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "xmlrpc");
    }

    @Test
    public void hproseWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "hprose");
    }

    @Test
    public void hessianWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "hessian");
    }

    @Test
    public void dubboWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("tcp", "dubbo");
    }

    @Test
    public void grpcWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "grpc");
    }

    @Test
    public void grpcWebWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "grpc-web");
    }

    @Test
    public void connectWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "connect");
    }

    @Test
    public void thriftWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("tcp", "thrift");
    }

    @Test
    public void thriftHttpWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "thrift-http");
    }

    @Test
    public void oncRpcWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("tcp", "oncrpc");
    }

    @Test
    public void oncRpcUdpWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("udp", "oncrpc-udp");
    }

    @Test
    public void tripleWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "triple");
    }

    @Test
    public void soapWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "soap");
    }

    @Test
    public void soap12WorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "soap", "1.2");
    }

    @Test
    public void tripleProtobufWorksWithoutBusinessClassesInClientJvm() throws Exception {
        this.callFromSeparateJvm("http", "triple", "Protobuf");
    }

    @Test
    public void localCatalogueResolvesPublishedServiceAndCallsItsNetworkEndpoint() throws Exception {
        // 本地上下文发布带对外方法名的服务，并开启 TCP 监听。
        MetConfiguration configuration = new MetConfiguration().setDataHome(this.directory.resolve("provider"));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        try (var server = configuration.buildContext()) {
            server.start();
            var service = server.publisher().rsfService(DiscoveryApi.class, name -> "local:" + name) //
                    .group("test").name("discover").version("1").protocol("rsf").register();

            // 无注册中心时按本地发布表寻找真实监听地址并用泛化参数调用。
            Object result = server.getRsfClient().syncInvokeGeneric(service.getBindID(), "rsf", "call", new Object[] { "client" }, 5000);
            assertEquals("local:client", result);
        }
    }

    @Test
    public void registrySuppliesServiceAndProtocolContractWithoutLocalConsumerRegistration() throws Exception {
        // 两个上下文连接同一个注册中心，消费端仅安装连接器。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        MetConfiguration provider = new MetConfiguration().setDataHome(this.directory.resolve("provider")).addExtension(registry);
        provider.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        MetConfiguration consumer = new MetConfiguration().setDataHome(this.directory.resolve("consumer")).addExtension(registry);
        consumer.connector("tcp").protocol("rsf");

        try (var server = provider.buildContext(); //
             var client = consumer.buildContext()) {
            var service = server.publisher().rsfService(DiscoveryApi.class, name -> "registry:" + name) //
                    .group("test").name("discover").version("1").protocol("rsf").register();
            server.start();
            client.start();

            // 不注册订阅，也可以从注册中心解析契约并按对外方法名发起 RPC。
            String id = service.getBindID();
            assertNull(client.getServiceInfo(id));
            Object result = client.getRsfClient().syncInvokeGeneric(id, "rsf", "call", new Object[] { "client" }, 5000);
            assertEquals("registry:client", result);
        }
    }

    @Test
    public void fixedAddressResolvesTheSelectedProviderInTheRegistry() throws Exception {
        // 两个实例发布同一个服务，分别返回自己的标识。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setPublishHost("127.0.0.1");
        MetConfiguration first = new MetConfiguration().setDataHome(this.directory.resolve("first")).addExtension(registry);
        MetConfiguration second = new MetConfiguration().setDataHome(this.directory.resolve("second")).addExtension(registry);
        MetConfiguration consumer = new MetConfiguration().setDataHome(this.directory.resolve("consumer")).addExtension(registry);
        first.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        second.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        consumer.connector("tcp").protocol("rsf");

        try (var firstProvider = first.buildContext(); //
             var secondProvider = second.buildContext(); //
             var client = consumer.buildContext()) {
            String id = firstProvider.publisher().rsfService(DiscoveryApi.class, name -> "first") //
                    .group("test").name("discover").version("1").protocol("rsf").register().getBindID();
            secondProvider.publisher().rsfService(DiscoveryApi.class, name -> "second") //
                    .group("test").name("discover").version("1").protocol("rsf").register();
            firstProvider.start();
            secondProvider.start();
            client.start();

            // 先确定默认目标，再用同一个入口调用另一个地址。
            Object[] arguments = { "client" };
            Object defaultResult = client.getRsfClient().syncInvokeGeneric(id, "rsf", "call", arguments, 5000);
            var selected = firstProvider;
            String expected = "first";
            if (StringUtils.equals("first", defaultResult.toString())) {
                selected = secondProvider;
                expected = "second";
            }
            var fixed = client.getRsfClient(selected.getListenAddress("rsf"));
            assertEquals(expected, fixed.syncInvokeGeneric(id, "rsf", "call", arguments, 5000));
        }
    }

    private void callFromSeparateJvm(String transport, String protocol) throws Exception {
        this.callFromSeparateJvm(transport, protocol, null);
    }

    private void callFromSeparateJvm(String transport, String protocol, String option) throws Exception {
        JdbcDataSource localDatabase = new JdbcDataSource();
        localDatabase.setURL("jdbc:h2:mem:generic");
        localDatabase.setUser("sa");
        try (var keeper = localDatabase.getConnection(); //
             var schema = new InputStreamReader(GenericCallTest.class.getResourceAsStream("/META-INF/meteor/schema/h2.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(keeper, schema);
            Server database = Server.createTcpServer("-tcpPort", "0", "-tcpDaemon", "-ifExists").start();
            try {
                String jdbcUrl = "jdbc:h2:tcp://127.0.0.1:" + database.getPort() + "/mem:generic";
                this.callThroughRegistry(transport, protocol, option, jdbcUrl);
            } finally {
                database.stop();
            }
        }
    }

    private void callThroughRegistry(String transport, String protocol, String option, String jdbcUrl) throws Exception {
        MetConfiguration config = new MetConfiguration().setDataHome(this.directory.resolve("provider")) //
                .setDefaultProtocol(protocol).addExtension(GenericCallClient.registry(jdbcUrl));
        int port = 0;
        if (StringUtils.equals("udp", transport)) {
            try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
                port = socket.getLocalPort();
            }
        }
        var connector = config.connector(transport).bind("127.0.0.1", port).protocol(protocol);
        if (StringUtils.equals("http", transport)) {
            connector.contextPath("/rpc");
        }
        if (StringUtils.equals("soap", protocol) && option != null) {
            connector.option("soapVersion", option);
        }
        if (!StringUtils.equals("rsf", protocol)) {
            config.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        }
        try (var server = config.buildContext()) {
            boolean protobuf = StringUtils.equals("grpc", protocol) || StringUtils.equals("grpc-web", protocol) //
                    || StringUtils.equals("connect", protocol) || StringUtils.equals("Protobuf", option);
            boolean directStruct = protobuf || StringUtils.equals("thrift", protocol) || StringUtils.equals("thrift-http", protocol) || StringUtils.equals("oncrpc", protocol) || StringUtils.equals("oncrpc-udp", protocol) || StringUtils.equals("soap", protocol);
            MetBindInfo<?> binding;
            if (directStruct) {
                var publication = server.publisher().rsfService(ProtoApi.class, value -> {
                    ProtoMessage reply = new ProtoMessage();
                    reply.value = "provider:" + value.value;
                    return reply;
                }).group("test").name("generic").version("1").aliasName(protocol, protobuf ? "example.Generic" : "Generic").protocol(protocol);
                if (StringUtils.equals("Protobuf", option)) {
                    publication.serialize(option);
                }
                binding = publication.register();
            } else {
                binding = server.publisher().rsfService(BusinessApi.class, values -> {
                    Message reply = new Message();
                    reply.value = "provider:" + values.get(0).value;
                    return List.of(reply);
                }).group("test").name("generic").version("1").aliasName("Hprose", "generic").protocol(protocol).register();
            }

            // 首次启动同步发布，子进程用服务 ID 从同一注册中心读取实际契约。
            server.start();

            Path output = this.directory.resolve("client.log");
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String api = directStruct ? ProtoApi.class.getName() : BusinessApi.class.getName();
            String dto = directStruct ? ProtoMessage.class.getName() : Message.class.getName();
            String arguments = directStruct ? "[{\"value\":\"client\"}]" : "[[{\"value\":\"client\"}]]";
            String expected = directStruct ? "{\"value\":\"provider:client\"}" : "[{\"value\":\"provider:client\"}]";
            Process process = new ProcessBuilder(java, "-Djava.io.tmpdir=" + this.directory, "-Duser.home=" + this.directory, //
                    "-cp", this.clientClasspath(), GenericCallClient.class.getName(), protocol, transport, //
                    jdbcUrl, binding.getBindID(), this.directory.resolve("client").toString(), api, dto, arguments) //
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertTrue(process.waitFor(25, TimeUnit.SECONDS), () -> "Generic client timed out: " + output);
                String result = Files.readString(output);
                assertEquals(0, process.exitValue(), result);
                assertTrue(result.contains("GENERIC_RESULT=" + expected), result);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    private String clientClasspath() throws Exception {
        String mainClass = GenericCallClient.class.getName().replace('.', '/') + ".class";
        Path localClasses = this.directory.resolve("client-classes");
        Path client = localClasses.resolve(mainClass);
        Files.createDirectories(client.getParent());
        try (var input = GenericCallClient.class.getClassLoader().getResourceAsStream(mainClass)) {
            Files.copy(input, client);
        }
        return localClasses + File.pathSeparator + System.getProperty("meteor.console.runtimeClasspath");
    }

    public interface DiscoveryApi {
        @MetMethod(name = "call")
        String invoke(String name);
    }

    @MetService(id = 0x31234567L)
    public interface ProtoApi {
        @MetMethod(name = "reflect", id = 1)
        ProtoMessage reflectMessage(@MetParam(id = 1) ProtoMessage value);
    }

    public static class ProtoMessage {
        @MetField(id = 1)
        public String value;
    }

    public interface BusinessApi {
        @MetMethod(name = "reflect")
        List<Message> reflectMessages(List<Message> values);
    }

    public static class Message implements java.io.Serializable {
        private static final long   serialVersionUID = 1L;
        public               String value;
    }
}
