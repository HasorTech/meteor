/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.scenario;
import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.cobble.ClassUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.example.api.DebugMessage;
import net.hasor.meteor.example.api.EchoService;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.meteor.test.scenario.api.InvocationEcho;
import net.hasor.meteor.test.scenario.api.InvocationNestedApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.h2.tools.Server;
import static net.hasor.meteor.test.scenario.ConsoleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 编译或执行 HTTP 文档中的主代码，真实调用选定服务；不读取 met-example 的业务源码。 */
public class ConsoleInvocationExamplesTest {
    @TempDir
    public Path directory;

    @ParameterizedTest(name = "{0} 调试使用 SDK 挂载和当前服务身份")
    @ValueSource(strings = { "connect", "grpc", "grpc-web", "hessian", "hprose", "jsonrpc", "soap", "thrift-http", "triple", "xmlrpc" })
    public void debugCallsTheSelectedServiceThroughItsSdkMount(String protocol) throws Exception {
        // 使用不同于接口注解的分组、版本，控制台必须恢复真实绑定并补齐协议路由。
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), protocol);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .group("sdk-debug").version("2.0").protocol(protocol).register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, protocol);

            // 经过真实 Web 调试入口，SDK 地址与配置的基础挂载完全一致。
            JsonNode contract = api.get("service/debug?" + selected);
            String address = contract.path("endpoints").get(0).path("address").asText();
            assertEquals(provider.getListenAddress(protocol).toString(), address);
            String signature = "echo(" + DebugMessage.class.getName() + ")";
            var request = Map.of("protocol", protocol, "address", address, "method", signature, //
                    "arguments", List.of(Map.of("value", "sdk", "left", 3, "right", 4)));
            JsonNode response = api.ok("service/debug?" + selected, "POST", request);
            assertTrue(response.path("success").asBoolean(), response.toPrettyString());
            assertEquals("actual:sdk", response.at("/value/value").asText());
            assertEquals(7, response.at("/value/result").asInt());
        }
    }

    @ParameterizedTest(name = "{0} / {1} 生成客户端")
    @CsvSource({ "jsonrpc,python", "jsonrpc,node", "xmlrpc,python" })
    public void generatedHttpClientsCallThePublishedDtoContract(String protocol, String language) throws Exception {
        String executable = runtime(language);
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), protocol);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            // 使用 Console 自有的演示接口和 DTO，真实服务返回调用中计算出的业务数据。
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol(protocol).register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, protocol);
            var rendered = api.get("examples/render?" + selected);
            JsonNode document = document(rendered, language, "direct");
            Path files = saveCode(document, this.directory.resolve("generated"), language);

            // 单份代码来自 HTTP 文档，不替换模板中的地址、方法名或参数。
            String script = "consumer.mjs";
            if (StringUtils.equals(language, "python")) {
                script = "consumer.py";
            }
            String output = run(files, List.of(executable, script));
            if (StringUtils.equals(protocol, "xmlrpc")) {
                assertTrue(output.contains("'value': 'actual:example'"), output);
                assertTrue(output.contains("'result': 2"), output);
            } else {
                var response = JsonUtils.readTree(output);
                assertEquals("actual:example", response.path("value").asText());
                assertEquals(2, response.path("result").asInt());
            }
        }
    }

    @Test
    public void generatedJsonRpcPythonClientKeepsTheRawEncodedContext() throws Exception {
        String python = runtime("python");
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("encoded-context-example") //
                .setDataHome(this.directory.resolve("provider"));
        configuration.addExtension(new RegistryConfiguration().setOnline(false).setManagementToken("test-token"));
        configuration.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        configuration.connector("http") // 原始挂载路径本身包含有效的百分号编码。
                .bind("127.0.0.1", 0) //
                .protocol("jsonrpc") //
                .contextPath("/invoke/%20jsonrpc");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol("jsonrpc").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();

            // 保存 Markdown 中的单份代码，不在测试端替换 URL 或解码 context。
            String selected = query(api, provider, id, "jsonrpc");
            JsonNode document = document(api.get("examples/render?" + selected), "python", "direct");
            Path files = saveCode(document, this.directory.resolve("generated"), "python");
            String consumer = Files.readString(files.resolve("consumer.py"));
            assertTrue(consumer.contains("/invoke/%20jsonrpc"), consumer);
            assertFalse(consumer.contains("%2520"), consumer);

            // 独立 Python 进程走真实监听端口，服务响应证明请求命中了原始挂载。
            String output = run(files, List.of(python, "consumer.py"));
            JsonNode response = JsonUtils.readTree(output);
            assertEquals("actual:example", response.path("value").asText());
            assertEquals(2, response.path("result").asInt());
        }
    }

    @Test
    public void serviceDescriptionOnlyReportsPublicConnectionFacts() throws Exception {
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("connection-facts-example") //
                .setDataHome(this.directory.resolve("provider"));
        configuration.addExtension(new RegistryConfiguration().setOnline(false).setManagementToken("test-token"));
        configuration.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        var connector = configuration.connector("http") //
                .bind("127.0.0.1", 0);
        connector.option("tlsEnabled", false) //
                .option("tlsKeyStore", "private-server.p12") //
                .option("tlsKeyStorePassword", "private-keystore-password");
        connector.protocol("jsonrpc") //
                .contextPath("/public") //
                .option("upgrade", "websocket");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            // 公开管理查询观察最终生效的挂载描述，不直接读取内部配置或私有状态。
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol("jsonrpc").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            api.managementToken("test-token");
            String scope = "mode=direct&target=" + encode(provider.getListenAddress("rsf").toString());
            scope += "&serviceId=" + encode(id) + "&protocol=jsonrpc";
            JsonNode result = api.get("examples/targets?" + scope);
            JsonNode target = result.path("targets").get(0);
            JsonNode connection = target.path("endpoint").path("description").path("extensions").path("connection");

            // 三项只读连接事实足以阻止生成错误的明文示例，证书位置与密码不出现在响应中。
            assertEquals(3, connection.size(), result.toPrettyString());
            assertFalse(connection.path("tlsEnabled").asBoolean());
            assertEquals("http", connection.path("httpScheme").asText());
            assertEquals("websocket", connection.path("upgrade").asText());
            String body = result.toString();
            assertFalse(body.contains("private-server.p12"), body);
            assertFalse(body.contains("private-keystore-password"), body);
        }
    }

    @Test
    public void generatedHproseNodeClientUsesTheExistingInstalledSdk() throws Exception {
        String node = runtime("node");
        Path repository = repository();
        Path dependencies = repository.resolve("met-example/peer/node/node_modules");
        assumeTrue(Files.isDirectory(dependencies.resolve("hprose")), "未安装可供验证的 Hprose Node.js SDK");
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), "hprose");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol("hprose").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, "hprose");
            JsonNode document = document(api.get("examples/render?" + selected), "node", "direct");
            Path artifacts = repository.resolve("build/invocation-examples/hprose-" + UUID.randomUUID());
            Path files = saveCode(document, artifacts, "node");
            Files.writeString(files.resolve("document.md"), document.path("markdown").asText(), StandardCharsets.UTF_8);
            Files.writeString(files.resolve("target-query.txt"), selected, StandardCharsets.UTF_8);

            // 只复用已经安装的官方 SDK 包，不读取其它工程的业务示例，也不下载依赖。
            copyDirectory(dependencies.resolve("hprose"), files.resolve("node_modules/hprose"));
            if (Files.isDirectory(dependencies.resolve("ws"))) {
                copyDirectory(dependencies.resolve("ws"), files.resolve("node_modules/ws"));
            }
            String output = run(files, List.of(node, "consumer.mjs"));
            assertTrue(output.contains("actual:example"), output);
            assertTrue(output.contains("result: 2"), output);
        }
    }

    @ParameterizedTest(name = "{0} Java 文档使用真实业务 API 依赖编译")
    @ValueSource(strings = { "rsf", "dubbo", "thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "grpc", "grpc-web", "connect", "triple" })
    public void generatedJavaDocumentCompilesWithTheDeclaredBusinessApiDependency(String protocol) throws Exception {
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), protocol);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol(protocol).register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, protocol);
            var rendered = api.get("examples/render?" + selected);
            JsonNode document = document(rendered, "java", "direct");
            Path files = saveCode(document, this.directory.resolve("generated"), "java");
            Path evidence = Files.createDirectories(repository().resolve("build/invocation-inventory/" + protocol));
            Files.writeString(evidence.resolve("targets.json"), api.get("examples/targets?" + selected).toPrettyString());

            // 业务 API JAR 显式提供真实接口与 DTO，编译路径不携带整个 Console 测试输出。
            String markdown = document.path("markdown").asText();
            assertTrue(markdown.contains(EchoService.class.getName()), markdown);
            Path businessApi = businessApi(files, EchoService.class, DebugMessage.class);
            Path classes = compile(files, businessApi);
            assertTrue(Files.isRegularFile(classes.resolve("ExampleMain.class")));
            assertFalse(Files.exists(files.resolve("src/main/java")), "文档不再生成接口或 DTO 替身文件");
        }
    }

    @ParameterizedTest(name = "{0} 原生 Java 文档独立调用实际服务")
    @ValueSource(strings = { "dubbo", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "grpc", "grpc-web", "connect", "triple", "thrift", "thrift-http", "oncrpc", "oncrpc-udp" })
    public void nativeJavaDocumentCallsTheActualProvider(String protocol) throws Exception {
        // 先经公共控制台接口生成文档，再用完全不包含 Meteor 的 SDK 运行图执行它。
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), protocol);
        if (StringUtils.equals(protocol, "dubbo")) {
            // Apache Dubbo 将回环地址替换为本机网卡地址，使用 SDK 实际连接的网卡进行验证。
            String host = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(item -> java.util.Collections.list(item.getInetAddresses()).stream())
                    .filter(item -> item instanceof java.net.Inet4Address && !item.isLoopbackAddress())
                    .findFirst().orElseThrow().getHostAddress();
            configuration.getConnectors().get(configuration.getConnectors().size() - 1).bind(host, 0);
        }
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService())
                    .protocol(protocol).register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, protocol);
            JsonNode document = document(api.get("examples/render?" + selected), "java-sdk", "direct");
            Path files = saveCode(document, this.directory.resolve("native"), "java");
            String source = Files.readString(files.resolve("ExampleMain.java"));
            assertFalse(source.contains("import net.hasor.meteor"), source);
            Path businessApi = businessApi(files, EchoService.class, DebugMessage.class);
            List<Path> dependencies = new ArrayList<>(sdkClasspath());
            // 保持已解析类路径的查找顺序，验证 SDK 加载器不能看到 Meteor 或 Console。
            List<URL> sdkUrls = new ArrayList<>();
            for (Path dependency : dependencies) {
                sdkUrls.add(dependency.toUri().toURL());
            }
            try (URLClassLoader sdk = new URLClassLoader(sdkUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
                assertThrows(ClassNotFoundException.class, () -> sdk.loadClass(MetContext.class.getName()));
                assertThrows(ClassNotFoundException.class, () -> sdk.loadClass(ConsoleMain.class.getName()));
            }
            dependencies.add(businessApi);
            Path classes = compile(files, dependencies);
            dependencies.add(classes);
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String output = run(files, List.of(java, "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "-Djava.io.tmpdir=" + files, "-Duser.home=" + files, "-cp", classpath(dependencies), "ExampleMain"));
            assertTrue(output.contains("actual:"), output);
        }
    }

    @Test
    public void generatedJavaDocumentCompilesForAnActualNestedServiceAndDto() throws Exception {
        // 真实接口和 DTO 均为业务 API 外层的 public 嵌套类型，控制台不能猜测美元符号含义。
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), "rsf");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(InvocationNestedApi.Service.class, value -> value) //
                    .register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, "rsf");
            JsonNode document = document(api.get("examples/render?" + selected), "java", "direct");
            Path files = saveCode(document, this.directory.resolve("generated"), "java");

            // 只携带轻量外层、接口和 DTO 的 API JAR；javac 从真实嵌套符号读取源码所属关系。
            String main = Files.readString(files.resolve("ExampleMain.java"));
            assertTrue(main.contains(InvocationNestedApi.Service.class.getCanonicalName()), main);
            assertTrue(main.contains("new " + InvocationNestedApi.Message.class.getCanonicalName() + "()"), main);
            assertFalse(main.contains(InvocationNestedApi.Service.class.getName()), main);
            Path businessApi = businessApi(files, InvocationNestedApi.class, //
                    InvocationNestedApi.Service.class, InvocationNestedApi.Message.class);
            Path classes = compile(files, businessApi);
            assertTrue(Files.isRegularFile(classes.resolve("ExampleMain.class")));
        }
    }

    @ParameterizedTest(name = "{0} SDK 示例使用挂载地址和服务绑定调用")
    @ValueSource(strings = { "rsf", "jsonrpc", "xmlrpc", "hessian" })
    public void generatedJavaDocumentExecutesWithItsDeclaredBusinessApiJar(String protocol) throws Exception {
        // 标量接口可直接检查子 JVM 打印的真实返回值，而不是只看进程退出码。
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), protocol);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        AtomicReference<String> argument = new AtomicReference<>();
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(InvocationEcho.class, value -> {
                argument.set(value);
                return protocol + ":" + value;
            }).group("sdk-demo").version("2.0").protocol(protocol).register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, protocol);
            JsonNode document = document(api.get("examples/render?" + selected), "java", "direct");
            String markdown = document.path("markdown").asText();
            assertTrue(markdown.contains(provider.getListenAddress(protocol).toString()), markdown);
            assertFalse(markdown.contains(".contextPathByPrefix("), markdown);
            Path files = saveCode(document, this.directory.resolve("generated"), "java");
            Path businessApi = businessApi(files, InvocationEcho.class);
            Path classes = compile(files, businessApi);

            // 独立 JVM 只携带明确的业务 API JAR，不携带整个测试输出，并使用自己的数据目录。
            List<Path> classpath = new ArrayList<>();
            classpath.add(classes);
            classpath.add(businessApi);
            classpath.addAll(runtimeClasspath());
            Path home = Files.createDirectories(this.directory.resolve("consumer-home"));
            Path temporary = Files.createDirectories(this.directory.resolve("consumer-tmp"));
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            List<String> command = new ArrayList<>(List.of(java, //
                    "--add-opens=java.base/java.io=ALL-UNNAMED", "-XX:ActiveProcessorCount=2", //
                    "-Duser.home=" + home, "-Djava.io.tmpdir=" + temporary, //
                    "-classpath", classpath(classpath), "ExampleMain"));
            String output = run(files, command);
            assertEquals("example", argument.get());
            assertTrue(output.contains(protocol + ":example"), output);
        }
    }

    @ParameterizedTest(name = "注册中心 Java {0} 文档使用业务 API 编译")
    @ValueSource(strings = { "consumer", "provider" })
    public void registryDocumentsContainTheirDependenciesAndCompileWithBusinessApi(String role) throws Exception {
        // TCP 地址可供独立进程连接；不使用只存在于控制台 JVM 的 H2 内存 URL 伪造发现示例。
        Server database = Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
        try {
            String url = "jdbc:h2:tcp://127.0.0.1:" + database.getPort() + "/mem:" + role + ";DB_CLOSE_DELAY=-1";
            var registry = registry(url);
            Path providerDirectory = this.directory.resolve("provider");
            MetConfiguration configuration = application(providerDirectory, registry, "registry-examples", "east", true);
            Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
            try (MetContext provider = configuration.buildContext(); //
                 BootApplication server = boot.start()) {
                String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                        .register().getBindID();
                provider.start();
                Api api = new Api(server);
                api.login();
                String cluster = api.ok("clusters", "POST", profile("JDBC 发现", url)).path("id").asText();
                String selected = "mode=registry&clusterId=" + cluster + "&serviceId=" + encode(id) + "&protocol=rsf";
                var targets = api.get("examples/targets?" + selected);
                assertEquals(1, targets.path("targets").size(), targets.toPrettyString());
                JsonNode target = targets.path("targets").get(0);
                selected += "&instanceId=" + target.path("instanceId").asText();
                selected += "&endpointAddress=" + encode(target.path("address").asText());

                // 单份 Java 代码自带注册中心配置，依赖坐标直接列在文档正文中。
                JsonNode document = document(api.get("examples/render?" + selected), "java", role);
                Path files = saveCode(document, this.directory.resolve("generated"), "java");
                String markdown = document.path("markdown").asText();
                assertTrue(markdown.contains("meteor-registry-jdbc"), markdown);
                assertTrue(markdown.contains("commons-dbcp2"), markdown);
                assertTrue(markdown.contains("h2"), markdown);
                Path businessApi = businessApi(files, EchoService.class, DebugMessage.class);
                Path classes = compile(files, businessApi);
                assertTrue(Files.isRegularFile(classes.resolve("ExampleMain.class")));
                String main = Files.readString(files.resolve("ExampleMain.java"));
                assertTrue(main.contains(url), main);
                assertFalse(main.contains("test-password"), main);
            }
        } finally {
            database.stop();
        }
    }

    @Test
    public void renderWithoutAnAddressKeepsTheEditablePlaceholder() throws Exception {
        // 不传 address 查询实际提供者契约，契约中的现存地址不得自动成为代码的调用目标。
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), "jsonrpc");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol("jsonrpc").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            api.managementToken("test-token");
            String scope = "mode=direct&target=" + encode(provider.getListenAddress("rsf").toString());
            scope += "&serviceId=" + encode(id) + "&instanceId=" + provider.getInstanceID() + "&protocol=jsonrpc";
            JsonNode available = api.get("examples/targets?" + scope);
            JsonNode target = available.path("targets").get(0);
            String selected = scope + "&endpointAddress=" + encode(target.path("address").asText());
            JsonNode response = api.get("examples/render?" + selected);

            // 使用者看到明确的填写位置，而不是在不知情的情况下指向真实在线服务。
            String main = code(document(response, "java", "direct"), "java");
            assertTrue(main.contains("new InterAddress(\"请填写服务地址\")"), main);
            String actualAddress = target.path("address").asText();
            assertFalse(actualAddress.isBlank(), available.toPrettyString());
            assertFalse(main.contains(actualAddress), main);
        }
    }

    @Test
    public void languageCatalogDoesNotRequireARegistryOrServiceTarget() throws Exception {
        // 语言入口只读取本地能力清单，不要求提供者在线，也不需要注册中心连接。
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (BootApplication server = boot.start()) {
            Api api = new Api(server);
            api.login();
            JsonNode catalog = api.get("examples/languages");

            // 所有已验证语言保留协议范围，前端据此隐藏不支持的入口。
            Set<String> ids = new LinkedHashSet<>();
            for (JsonNode language : catalog) {
                assertTrue(ids.add(language.path("id").asText()), catalog.toPrettyString());
                assertFalse(language.path("label").asText().isBlank());
                assertTrue(language.path("protocols").isArray());
            }
            assertEquals(Set.of("java", "java-sdk", "python", "javascript", "go", "cpp", "browser", "shell", "c"), ids);
        }
    }

    @Test
    public void renderListsAllLanguagesAndTheirVerifiedDubboCapabilities() throws Exception {
        // Dubbo 用来区分经验证的 Go、Node.js SDK 与尚无此方向证据的 Python。
        MetConfiguration configuration = configuration(this.directory.resolve("provider"), "dubbo");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()) //
                    .protocol("dubbo").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            String selected = query(api, provider, id, "dubbo");
            JsonNode response = api.get("examples/render?" + selected);

            // 语言目录始终完整，协议支持以能力声明为准，不因当前没有正文而隐藏语言。
            Map<String, JsonNode> languages = new LinkedHashMap<>();
            for (JsonNode language : response.path("languages")) {
                String languageId = language.path("id").asText();
                assertFalse(language.path("label").asText().isBlank(), response.toPrettyString());
                assertTrue(language.path("protocols").isArray(), response.toPrettyString());
                assertNull(languages.put(languageId, language), response.toPrettyString());
            }
            Set<String> expected = Set.of("java", "java-sdk", "python", "go", "javascript", "browser", "c", "cpp", "shell");
            assertEquals(expected, languages.keySet(), response.toPrettyString());
            JsonNode pythonCapabilities = languages.get("python").path("protocols");
            JsonNode goCapabilities = languages.get("go").path("protocols");
            JsonNode javascriptCapabilities = languages.get("javascript").path("protocols");
            List<String> python = List.of(JsonUtils.convertValue(pythonCapabilities, String[].class));
            List<String> go = List.of(JsonUtils.convertValue(goCapabilities, String[].class));
            List<String> javascript = List.of(JsonUtils.convertValue(javascriptCapabilities, String[].class));
            assertFalse(python.contains("dubbo"), response.toPrettyString());
            assertTrue(go.contains("dubbo"), response.toPrettyString());
            assertTrue(javascript.contains("dubbo"), response.toPrettyString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "targets", "render", "languages" })
    public void examplesRequireAnAuthenticatedSession(String operation) throws Exception {
        // 认证在读取目标或生成代码之前执行，不因示例接口是只读就暴露集群信息。
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (BootApplication server = boot.start()) {
            var denied = new Api(server).send("examples/" + operation, "GET", null);
            assertEquals(401, denied.statusCode(), denied.body());
        }
    }

    static MetConfiguration configuration(Path directory, String protocol) {
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("invocation-examples-" + protocol) //
                .setDataHome(directory) //
                .setDefaultProtocol(protocol);
        configuration.addExtension(new RegistryConfiguration().setOnline(false).setManagementToken("test-token"));
        configuration.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        if (Set.of("connect", "grpc", "grpc-web", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "thrift-http", "triple").contains(protocol)) {
            configuration.connector("http") //
                    .bind("127.0.0.1", 0) //
                    .protocol(protocol) //
                    .contextPath("/invoke/" + protocol);
        } else if (!StringUtils.equals(protocol, "rsf")) {
            String transport = "tcp";
            if (StringUtils.equals(protocol, "oncrpc-udp")) {
                transport = "udp";
            }
            int port = 0;
            if (StringUtils.equals(transport, "udp")) {
                try (var socket = new java.net.DatagramSocket(0)) {
                    port = socket.getLocalPort();
                } catch (java.net.SocketException failure) {
                    throw new IllegalStateException(failure);
                }
            }
            configuration.connector(transport) //
                    .bind("127.0.0.1", port) //
                    .protocol(protocol);
        }
        return configuration;
    }

    static EchoService echoService() {
        return new EchoService() {
            @Override
            public DebugMessage echo(DebugMessage request) {
                return ConsoleInvocationExamplesTest.echo(request);
            }

            @Override
            public DebugMessage uppercase(DebugMessage request) {
                return ConsoleInvocationExamplesTest.echo(request);
            }

            @Override
            public DebugMessage repeat(DebugMessage request) {
                return ConsoleInvocationExamplesTest.echo(request);
            }

        };
    }

    static DebugMessage echo(DebugMessage request) {
        DebugMessage result = new DebugMessage();
        result.setValue("actual:" + request.getValue());
        result.setLeft(request.getLeft());
        result.setRight(request.getRight());
        result.setResult(request.getLeft() + request.getRight());
        return result;
    }

    static String query(Api api, MetContext provider, String serviceId, String protocol) throws Exception {
        api.managementToken("test-token");
        String scope = "mode=direct&target=" + encode(provider.getListenAddress("rsf").toString());
        scope += "&serviceId=" + encode(serviceId) + "&instanceId=" + provider.getInstanceID();
        scope += "&protocol=" + protocol;
        JsonNode available = api.get("examples/targets?" + scope);
        assertTrue(available.path("warnings").isEmpty(), available.toPrettyString());
        assertEquals(1, available.path("targets").size(), available.toPrettyString());
        JsonNode target = available.path("targets").get(0);
        String query = scope + "&endpointAddress=" + encode(target.path("address").asText());
        query += "&address=" + encode(target.path("address").asText());
        if (!target.at("/endpoint/contextPath").isNull()) {
            query += "&contextPath=" + encode(target.at("/endpoint/contextPath").asText());
        }
        return query;
    }

    static Path saveCode(JsonNode document, Path directory, String language) throws Exception {
        Files.createDirectories(directory);
        String name;
        String fence;
        if (StringUtils.equals(language, "java")) {
            name = "ExampleMain.java";
            fence = "java";
        } else if (StringUtils.equals(language, "python")) {
            name = "consumer.py";
            fence = "python";
        } else if (StringUtils.equals(language, "node")) {
            name = "consumer.mjs";
            fence = "javascript";
        } else {
            throw new IllegalArgumentException("未声明示例语言：" + language);
        }
        Files.writeString(directory.resolve(name), code(document, fence), StandardCharsets.UTF_8);
        return directory;
    }

    static JsonNode document(JsonNode result, String language, String role) {
        String canonicalLanguage = language;
        if (StringUtils.equals(language, "node")) {
            canonicalLanguage = "javascript";
        }
        for (JsonNode document : result.path("documents")) {
            if (StringUtils.equals(canonicalLanguage, document.path("language").asText())
                    && StringUtils.equals(role, document.path("role").asText())) {
                assertFalse(document.path("markdown").asText().isBlank(), result.toPrettyString());
                assertFalse(document.has("files"), result.toPrettyString());
                return document;
            }
        }
        fail("没有生成 " + language + " / " + role + " 文档：" + result.toPrettyString());
        return null;
    }

    static String code(JsonNode document, String language) {
        String expression = "(?ms)^```" + Pattern.quote(language) + "[ \t]*\\R(.*?)^```[ \t]*$";
        var matches = Pattern.compile(expression).matcher(document.path("markdown").asText());
        assertTrue(matches.find(), "文档缺少 " + language + " 代码块：" + document);
        String code = matches.group(1);
        assertFalse(matches.find(), "文档只能有一份 " + language + " 主代码：" + document);
        return code;
    }

    private static Path businessApi(Path directory, Class<?>... contracts) throws Exception {
        Path jar = directory.resolve("business-api.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Class<?> contract : contracts) {
                String resource = contract.getName().replace('.', '/') + ".class";
                output.putNextEntry(new JarEntry(resource));
                try (var source = contract.getClassLoader().getResourceAsStream(resource)) {
                    assertNotNull(source, resource);
                    source.transferTo(output);
                }
                output.closeEntry();
            }
        }
        return jar;
    }

    private static Path compile(Path project, Path businessApi) throws Exception {
        List<Path> dependencies = new ArrayList<>(runtimeClasspath());
        dependencies.add(businessApi);
        return compile(project, dependencies);
    }

    private static Path compile(Path project, List<Path> dependencies) throws Exception {
        Path output = Files.createDirectories(project.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "生成的 Java 示例需要 JDK 编译器");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjects(project.resolve("ExampleMain.java").toFile());
            List<String> options = List.of("--release", "17", "-encoding", "UTF-8", //
                    "-classpath", classpath(dependencies), "-d", output.toString());
            StringWriter messages = new StringWriter();
            boolean compiled = compiler.getTask(messages, manager, diagnostics, options, null, units).call();
            assertTrue(compiled, messages + "\n" + diagnostics.getDiagnostics());
        }
        return output;
    }

    private static List<Path> runtimeClasspath() throws Exception {
        // Gradle 的 java.class.path 可能只有 worker JAR，已解析依赖还保存在测试类加载器中。
        Set<Path> paths = new LinkedHashSet<>();
        for (String item : System.getProperty("java.class.path").split(File.pathSeparator)) {
            paths.add(Path.of(item).toAbsolutePath().normalize());
        }
        ClassLoader applicationLoader = Thread.currentThread().getContextClassLoader();
        Set<URL> resources = new LinkedHashSet<>(ClassUtils.getClassPath(applicationLoader));
        resources.addAll(ClassUtils.getClassPath(ConsoleInvocationExamplesTest.class.getClassLoader()));
        for (URL url : resources) {
            if (StringUtils.equals("file", url.getProtocol())) {
                paths.add(Path.of(url.toURI()).toAbsolutePath().normalize());
            }
        }
        URL originalClasses = ConsoleInvocationExamplesTest.class.getProtectionDomain().getCodeSource().getLocation();
        Path originalTests = Path.of(originalClasses.toURI());
        paths.remove(originalTests.toAbsolutePath().normalize());
        paths.removeIf(path -> path.toString().replace(File.separatorChar, '/').endsWith("/build/resources/test"));
        return List.copyOf(paths);
    }

    private static String classpath(List<Path> paths) {
        return String.join(File.pathSeparator, paths.stream().map(Path::toString).toList());
    }

    private static List<Path> sdkClasspath() throws Exception {
        // 复用已解析的测试依赖；SDK 子进程不携带 Meteor、Console、测试输出或测试框架。
        List<Path> dependencies = new ArrayList<>();
        for (Path path : runtimeClasspath()) {
            if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(path.toFile())) {
                boolean infrastructure = jar.stream().map(JarEntry::getName).anyMatch(name -> //
                        name.startsWith("net/hasor/") || name.startsWith("org/junit/") || //
                                name.startsWith("org/gradle/") || name.startsWith("org/mockito/") || //
                                name.startsWith("org/jacoco/") || name.startsWith("org/opentest4j/"));
                if (!infrastructure) {
                    dependencies.add(path);
                }
            }
        }
        return dependencies;
    }

    private static String run(Path directory, List<String> command) throws Exception {
        Path output = directory.resolve("process-output.log");
        Process process = new ProcessBuilder(command).directory(directory.toFile()) //
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            boolean finished = process.waitFor(40, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            String content = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(finished, "示例运行超时：" + command + "\n" + content);
            assertEquals(0, process.exitValue(), content);
            return content;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String runtime(String language) throws Exception {
        List<String> commands = List.of("node");
        if (StringUtils.equals(language, "python")) {
            commands = List.of("python3", "python");
        }
        for (String command : commands) {
            try {
                Process check = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
                if (check.waitFor(5, TimeUnit.SECONDS) && check.exitValue() == 0) {
                    return command;
                }
                check.destroyForcibly();
            } catch (java.io.IOException unavailable) {
                // 某些平台只提供 python，继续检查同一运行时的常见可执行名称。
            }
        }
        assumeTrue(false, "未安装 " + language + " 运行时，未执行原生生成示例");
        return null;
    }

    private static Path repository() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            boolean module = Files.isDirectory(path.resolve("meteor-console"));
            if (module && Files.isRegularFile(path.resolve("settings.gradle"))) {
                return path;
            }
        }
        throw new IllegalStateException("找不到 Meteor 工程根目录");
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path output = target.resolve(source.relativize(file));
                if (Files.isDirectory(file)) {
                    Files.createDirectories(output);
                } else {
                    Files.copy(file, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
