/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.protocol.triple;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.hasor.example.protocol.triple.*;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.protocol.grpc.GrpcProtoResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(25)
public class TripleSchemaEndpointScenarioTest {
    @Test
    public void protobufVersionsExposeTheirOwnBoundDescriptor() throws Exception {
        // 同名 Triple 服务按查询参数选择版本，路径只包含一次协议服务名。
        MetConfiguration configuration = this.configuration(true);
        GrpcProtoResource resource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo.proto"));
        GrpcProtoResource secondResource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo-v2.proto"));
        try (MetContext server = configuration.buildContext()) {
            server.start();
            MetBindInfo<TripleFlowService> first = server.publisher() //
                    .rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").version("1.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();
            MetBindInfo<TripleFlowService> second = server.publisher() //
                    .rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").version("2.0").serialize("Protobuf") //
                    .bindResource("triple", secondResource).register();
            URI firstUri = this.uri(server, first);
            URI secondUri = this.uri(server, second);
            HttpClient http = HttpClient.newHttpClient();

            // 两个入口均指向发布时绑定的原文件，客户端不传文件名或依赖路径。
            assertNotEquals(firstUri, secondUri);
            assertEquals("/rpc/meteor.triple.demo.TripleFlowService", firstUri.getRawPath());
            assertEquals(firstUri.getRawPath(), secondUri.getRawPath());
            var response = http.send(HttpRequest.newBuilder(firstUri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(resource.getSources().get("triple-demo.proto"), response.body());
            HttpRequest secondRequest = HttpRequest.newBuilder(secondUri).GET().build();
            var secondResponse = http.send(secondRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, secondResponse.statusCode());
            assertEquals(secondResource.getSources().get("triple-demo-v2.proto"), secondResponse.body());
        }
    }

    @Test
    public void hessianWrapperDoesNotExposeAnInventedProto() throws Exception {
        // Triple 的 Hessian2 业务包装不是业务 Protobuf 契约，不提供描述文件。
        MetConfiguration configuration = this.configuration(true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            MetBindInfo<TripleDemoService> service = server.publisher() //
                    .rsfService(TripleDemoService.class, new TripleDemoServiceImpl()) //
                    .serialize("Hessian2").register();
            URI uri = this.uri(server, service);
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }

    @Test
    public void omittedVersionIsAmbiguousWhenMultipleVersionsArePublished() throws Exception {
        // 不带选择条件时不能任意读取第一个版本的契约。
        MetConfiguration configuration = this.configuration(true);
        GrpcProtoResource resource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo.proto"));
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").version("1.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();
            server.publisher().rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").version("2.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();

            URI uri = URI.create("http://" + server.getListenAddress("triple").getHostPort() //
                    + "/rpc/meteor.triple.demo.TripleFlowService?proto");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(409, response.statusCode());
        }
    }

    @Test
    public void protobufGroupsExposeTheirOwnBoundDescriptor() throws Exception {
        // 相同服务名、相同版本的两个分组仍能共用挂载路径。
        MetConfiguration configuration = this.configuration(true);
        GrpcProtoResource blue = new GrpcProtoResource(getClass().getResource("/proto/triple-demo.proto"));
        GrpcProtoResource green = new GrpcProtoResource(getClass().getResource("/proto/triple-demo-v2.proto"));
        try (MetContext server = configuration.buildContext()) {
            server.start();
            MetBindInfo<TripleFlowService> first = server.publisher() //
                    .rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").group("blue").version("1.0").serialize("Protobuf") //
                    .bindResource("triple", blue).register();
            MetBindInfo<TripleFlowService> second = server.publisher() //
                    .rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").group("green").version("1.0").serialize("Protobuf") //
                    .bindResource("triple", green).register();

            HttpClient http = HttpClient.newHttpClient();
            HttpRequest firstRequest = HttpRequest.newBuilder(this.uri(server, first)).GET().build();
            HttpRequest secondRequest = HttpRequest.newBuilder(this.uri(server, second)).GET().build();
            var firstResponse = http.send(firstRequest, HttpResponse.BodyHandlers.ofString());
            var secondResponse = http.send(secondRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstResponse.statusCode());
            assertEquals(200, secondResponse.statusCode());
            assertEquals(blue.getSources().get("triple-demo.proto"), firstResponse.body());
            assertEquals(green.getSources().get("triple-demo-v2.proto"), secondResponse.body());
        }
    }

    @Test
    public void protobufVersionsRemainCallableAtTheSameEndpoint() throws Exception {
        // 描述文件的查询选择不能改变业务 POST 的原生分组、版本请求头。
        MetConfiguration incoming = this.configuration(true);
        MetConfiguration outgoing = this.configuration(false);
        GrpcProtoResource resource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo.proto"));
        TripleFlowServiceImpl firstProvider = new TripleFlowServiceImpl();
        TripleFlowServiceImpl secondProvider = new TripleFlowServiceImpl();
        try (MetContext server = incoming.buildContext(); //
             MetContext client = outgoing.buildContext()) {
            server.start();
            client.start();
            server.publisher().rsfService(TripleFlowService.class, firstProvider) //
                    .name("meteor.triple.demo.TripleFlowService").version("1.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();
            server.publisher().rsfService(TripleFlowService.class, secondProvider) //
                    .name("meteor.triple.demo.TripleFlowService").version("2.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();
            MetBindInfo<TripleFlowService> first = client.publisher().rsfService(TripleFlowService.class) //
                    .name("meteor.triple.demo.TripleFlowService").version("1.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();
            MetBindInfo<TripleFlowService> second = client.publisher().rsfService(TripleFlowService.class) //
                    .name("meteor.triple.demo.TripleFlowService").version("2.0").serialize("Protobuf") //
                    .bindResource("triple", resource).register();

            // 同一个远端地址，分别调用后只有对应版本的提供者收到请求。
            TripleFlowService firstRemote = client.getRsfClient(server.getListenAddress("triple")).getRemote(first);
            TripleFlowService secondRemote = client.getRsfClient(server.getListenAddress("triple")).getRemote(second);
            assertEquals(2, firstRemote.echo(TripleMessage.of("first", 1)).getNumber());
            assertEquals(1, firstProvider.getCalls());
            assertEquals(0, secondProvider.getCalls());
            assertEquals(3, secondRemote.echo(TripleMessage.of("second", 2)).getNumber());
            assertEquals(1, firstProvider.getCalls());
            assertEquals(1, secondProvider.getCalls());
        }
    }

    @Test
    public void selectorsDoNotHideDescriptorRouteConflicts() throws Exception {
        // 查询参数不参与挂载匹配，不能让被更长路径遮挡的文件入口通过发布检查。
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol("triple");
        configuration.connector("http").bind("127.0.0.1", 0) //
                .protocol("triple").contextPath("/rpc").addContextPath("/rpc/meteor.triple.demo.TripleFlowService");
        GrpcProtoResource resource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo.proto"));
        try (MetContext server = configuration.buildContext()) {
            server.start();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                server.publisher().rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                        .name("meteor.triple.demo.TripleFlowService").version("1.0").serialize("Protobuf") //
                        .bindResource("triple", resource).register();
            });
            assertTrue(failure.getMessage().contains("shadowed"));
        }
    }

    @Test
    public void exactPathVersionsShareTheDescriptorPath() throws Exception {
        // 精确发布不再追加服务名，仍通过查询区分同路径的两个版本。
        MetConfiguration configuration = this.configuration(true);
        GrpcProtoResource resource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo.proto"));
        GrpcProtoResource secondResource = new GrpcProtoResource(getClass().getResource("/proto/triple-demo-v2.proto"));
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").version("1.0").serialize("Protobuf") //
                    .contextPathByExact("triple", "/schema").bindResource("triple", resource).register();
            server.publisher().rsfService(TripleFlowService.class, new TripleFlowServiceImpl()) //
                    .name("meteor.triple.demo.TripleFlowService").version("2.0").serialize("Protobuf") //
                    .contextPathByExact("triple", "/schema").bindResource("triple", secondResource).register();

            String origin = "http://" + server.getListenAddress("triple").getHostPort();
            HttpRequest first = HttpRequest.newBuilder(URI.create(origin + "/rpc/schema?proto&version=1.0")).GET().build();
            HttpRequest second = HttpRequest.newBuilder(URI.create(origin + "/rpc/schema?proto&version=2.0")).GET().build();
            HttpClient http = HttpClient.newHttpClient();
            var firstResponse = http.send(first, HttpResponse.BodyHandlers.ofString());
            var secondResponse = http.send(second, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstResponse.statusCode());
            assertEquals(200, secondResponse.statusCode());
            assertEquals(resource.getSources().get("triple-demo.proto"), firstResponse.body());
            assertEquals(secondResource.getSources().get("triple-demo-v2.proto"), secondResponse.body());
        }
    }

    private URI uri(MetContext server, MetBindInfo<?> service) {
        String group = URLEncoder.encode(Objects.toString(service.getBindGroup(), ""), StandardCharsets.UTF_8).replace("+", "%20");
        String version = URLEncoder.encode(Objects.toString(service.getBindVersion(), ""), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("http://" + server.getListenAddress("triple").getHostPort() //
                + "/rpc/" + service.getBindName() + "?proto&group=" + group + "&version=" + version);
    }

    private MetConfiguration configuration(boolean listener) {
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol("triple");
        var http = configuration.connector("http").option("httpVersion", 2);
        if (listener) {
            http.bind("127.0.0.1", 0);
        }
        http.protocol("triple").contextPath("/rpc").option("streamBody", true);
        return configuration;
    }
}
