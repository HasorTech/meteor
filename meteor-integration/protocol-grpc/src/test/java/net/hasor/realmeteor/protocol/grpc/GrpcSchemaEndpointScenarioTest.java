/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.protocol.grpc;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import net.hasor.example.protocol.grpc.AnnotatedEcho;
import net.hasor.example.protocol.grpc.AnnotatedEchoImpl;
import net.hasor.example.protocol.grpc.AnnotatedMessage;
import net.hasor.example.protocol.grpc.TestConfigurationUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.protocol.grpc.GrpcProtoResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(25)
public class GrpcSchemaEndpointScenarioTest {
    @Test
    public void boundOriginalFilesDownloadWithAllImports() throws Exception {
        // HTTP/2 流式挂载仍允许浏览器经 HTTP/1 查看同一绑定的完整原文件图。
        MetConfiguration configuration = this.configuration("grpc", true);
        GrpcProtoResource resource = new GrpcProtoResource(this.getClass().getResource("/proto/echo.proto"));
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc", "example.Echo").serialize("Protobuf") //
                    .bindResource("grpc", resource).register();
            URI uri = this.uri(server.getListenAddress("grpc"), "/rpc/default/1.0.0/example.Echo?proto");
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var page = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("message.proto"));
            assertTrue(page.body().contains("下载全部定义文件"));

            // ZIP 可按原 include 关系重新加载，不丢原始注释和编号。
            URI download = URI.create(uri + "&download");
            var response = http.send(HttpRequest.newBuilder(download).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            Map<String, String> sources = new LinkedHashMap<>();
            Path directory = Files.createTempDirectory("grpc-downloaded-");
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    String text = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    sources.put(entry.getName(), text);
                    Files.writeString(directory.resolve(entry.getName()), text);
                }
            }
            assertEquals(resource.getSources(), sources);
            assertEquals(sources, new GrpcProtoResource(directory.resolve("echo.proto").toUri().toURL()).getSources());
        }
    }

    @Test
    public void generatedProtoCanBeDownloadedAndUsedForAnRpcConsumer() throws Exception {
        // 提供者不绑定文件，从实际发布的注解契约生成 proto。
        MetConfiguration incoming = this.configuration("grpc", true);
        MetConfiguration outgoing = this.configuration("grpc", false);
        try (MetContext server = incoming.buildContext(); //
             MetContext client = outgoing.buildContext()) {
            server.start();
            client.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc", "example.Echo").serialize("Protobuf").register();
            URI uri = this.uri(server.getListenAddress("grpc"), "/rpc/default/1.0.0/example.Echo?proto");
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
            var response = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("service Echo"));
            Path file = Files.createTempDirectory("grpc-generated-").resolve("echo.proto");
            Files.writeString(file, response.body());

            // 新消费者只绑定下载文件，经真实 gRPC 调用证明导出契约可用。
            GrpcProtoResource downloaded = new GrpcProtoResource(file.toUri().toURL());
            MetBindInfo<AnnotatedEcho> binding = client.publisher().rsfService(AnnotatedEcho.class) //
                    .aliasName("grpc", "example.Echo").serialize("Protobuf") //
                    .bindResource("grpc", downloaded).register();
            AnnotatedMessage input = new AnnotatedMessage();
            input.setValue("downloaded");
            input.setCount(7);
            AnnotatedEcho remote = client.getRsfClient(server.getListenAddress("grpc")).getRemote(binding);
            assertEquals(8, remote.mirror(input).getCount());
        }
    }

    @Test
    public void grpcWebExposesProtoOnItsOwnMount() throws Exception {
        // gRPC-Web 使用自身挂载路径，同样导出真正的 Protobuf 契约。
        MetConfiguration configuration = this.configuration("grpc-web", true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc-web", "example.Echo").serialize("Protobuf").register();
            URI uri = this.uri(server.getListenAddress("grpc-web"), "/rpc/default/1.0.0/example.Echo?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("service Echo"));
        }
    }

    @Test
    public void grpcWebSchemaDoesNotBypassTheConfiguredOriginPolicy() throws Exception {
        // 文件入口和业务请求使用同一份 CORS 策略。
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol("grpc-web");
        configuration.connector("http").bind("127.0.0.1", 0) //
                .protocol("grpc-web").contextPath("/rpc").option("corsOrigins", "https://allowed.test");
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc-web", "example.Echo").serialize("Protobuf").register();
            URI uri = this.uri(server.getListenAddress("grpc-web"), "/rpc/default/1.0.0/example.Echo?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).header("Origin", "https://blocked.test").GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, response.statusCode());
            assertFalse(response.body().contains("service Echo"));
        }
    }

    @Test
    public void exactServicePathAlsoExposesItsBoundSchema() throws Exception {
        // 精确路径原样追加文件标记，不额外拼服务名或新建挂载点。
        MetConfiguration configuration = this.configuration("grpc", true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc", "example.Echo").serialize("Protobuf") //
                    .contextPathByExact("grpc", "/example.Echo/echo").register();
            URI uri = this.uri(server.getListenAddress("grpc"), "/rpc/example.Echo/echo?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("service Echo"));
        }
    }

    @Test
    public void jsonOnlyServiceDoesNotInventAProtoFile() throws Exception {
        // 无 proto 且仅使用 JSON 的接口没有可导出的 Protobuf 文件。
        MetConfiguration configuration = this.configuration("grpc", true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc", "example.Echo").serialize("Json").register();
            URI uri = this.uri(server.getListenAddress("grpc"), "/rpc/default/1.0.0/example.Echo?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }

    @Test
    public void postWithDescriptorFlagKeepsTheExistingRpcValidation() throws Exception {
        // 文件标记不能把 POST 变成文件读取，也不能绕过 gRPC 的原有 URI 校验。
        MetConfiguration configuration = this.configuration("grpc", true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc", "example.Echo").serialize("Protobuf").register();
            URI uri = this.uri(server.getListenAddress("grpc"), "/rpc/default/1.0.0/example.Echo/echo?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)) //
                    .header("Content-Type", "application/grpc+proto") //
                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[5])).build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertFalse(response.body().contains("service Echo"));
            assertTrue(response.headers().firstValue("grpc-status").isPresent() || response.statusCode() >= 400);
        }
    }

    @Test
    public void moreSpecificMountCannotSilentlyCaptureTheSchemaPath() throws Exception {
        // 正常挂载下的服务文件路径被更具体的挂载遮挡时，在发布阶段直接拒绝。
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol("grpc");
        configuration.connector("http").bind("127.0.0.1", 0) //
                .protocol("grpc").contextPath("/rpc").addContextPath("/rpc/default/1.0.0/example.Echo");
        try (MetContext server = configuration.buildContext()) {
            server.start();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                        .aliasName("grpc", "example.Echo").serialize("Protobuf").register();
            });
            assertTrue(failure.getMessage().contains("shadowed"));
        }
    }

    @Test
    public void conflictingExactPublicationPreservesThePreviousSchema() throws Exception {
        // 已发布文件入口不能被后续精确服务路径劫持，失败发布不影响原绑定。
        MetConfiguration configuration = this.configuration("grpc", true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                    .aliasName("grpc", "example.Echo").serialize("Protobuf").register();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                server.publisher().rsfService(AnnotatedEcho.class, new AnnotatedEchoImpl()) //
                        .name("other").aliasName("grpc", "other.Echo").serialize("Protobuf") //
                        .contextPathByExact("grpc", "/default/1.0.0/example.Echo").register();
            });
            assertTrue(failure.getMessage().contains("shadowed") || failure.getMessage().contains("Conflicting schema"));

            // 实际 GET 仍读取原提供者的契约。
            URI uri = this.uri(server.getListenAddress("grpc"), "/rpc/default/1.0.0/example.Echo?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("service Echo"));
        }
    }

    private MetConfiguration configuration(String protocol, boolean listener) {
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol(protocol);
        var http = configuration.connector("http").option("httpVersion", 2);
        if (listener) {
            http.bind("127.0.0.1", 0);
        }
        http.protocol(protocol).contextPath("/rpc").option("streamBody", true);
        return configuration;
    }

    private URI uri(InterAddress address, String path) {
        return URI.create("http://" + address.getHostPort() + path);
    }
}
