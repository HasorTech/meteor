/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.protocol.thrift;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import net.hasor.example.protocol.thrift.TestConfigurationUtils;
import net.hasor.example.protocol.thrift.mapping.CommonMetaEcho;
import net.hasor.example.protocol.thrift.mapping.Note;
import net.hasor.example.protocol.thrift.mapping.NoteService;
import net.hasor.example.protocol.thrift.mapping.NoteServiceImpl;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.protocol.thrift.ThriftIdlResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(25)
public class ThriftSchemaEndpointScenarioTest {
    @Test
    public void originalIdlIsReturnedWithoutRewriting() throws Exception {
        // 同一文件含多个服务时，由发布时选定的绑定决定入口。
        MetConfiguration configuration = this.configuration(true);
        ThriftIdlResource resource = new ThriftIdlResource(getClass().getResource("/thrift/mapping.thrift")) //
                .selectService("NoteService");
        try (MetContext server = configuration.buildContext()) {
            server.start();
            MetBindInfo<NoteService> service = server.publisher().rsfService(NoteService.class, new NoteServiceImpl()) //
                    .bindResource("thrift-http", resource).register();
            URI uri = this.uri(server, service);
            HttpClient http = HttpClient.newHttpClient();
            var response = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(resource.getSources().get("mapping.thrift"), response.body());

            // HEAD 保留下载的长度和类型，不发送正文。
            HttpRequest headRequest = HttpRequest.newBuilder(uri) //
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var head = http.send(headRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, head.statusCode());
            assertEquals("", head.body());
            assertEquals(response.headers().firstValue("content-length"), head.headers().firstValue("content-length"));
        }
    }

    @Test
    public void generatedIdlCanBeBoundByANewConsumer() throws Exception {
        // 没有绑定文件时，直接导出当前注解已编译的 IDL。
        MetConfiguration incoming = this.configuration(true);
        MetConfiguration outgoing = this.configuration(false);
        try (MetContext server = incoming.buildContext(); //
             MetContext client = outgoing.buildContext()) {
            server.start();
            client.start();
            MetBindInfo<CommonMetaEcho> published = server.publisher().rsfService(CommonMetaEcho.class, note -> note).register();
            URI uri = this.uri(server, published);
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            Path file = Files.createTempDirectory("thrift-generated-").resolve("notes.thrift");
            Files.writeString(file, response.body());

            // 消费者加载下载后的原生文件，再走真实 HTTP 调用，验证字段与方法编号仍然一致。
            ThriftIdlResource downloaded = new ThriftIdlResource(file.toUri().toURL());
            MetBindInfo<CommonMetaEcho> subscribed = client.publisher().rsfService(CommonMetaEcho.class) //
                    .bindResource("thrift-http", downloaded).register();
            CommonMetaEcho remote = client.getRsfClient(server.getListenAddress("thrift-http")).getRemote(subscribed);
            Note input = new Note();
            input.setId(19);
            assertEquals(19, remote.mirror(input).getId());
        }
    }

    @Test
    public void conflictingDescriptorPreservesThePublishedService() throws Exception {
        // 同一分组、版本下的同名 IDL 在发布阶段拒绝，保留旧服务。
        MetConfiguration configuration = this.configuration(true);
        try (MetContext server = configuration.buildContext()) {
            server.start();
            MetBindInfo<CommonMetaEcho> first = server.publisher().rsfService(CommonMetaEcho.class, note -> note) //
                    .version("1.0").register();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                server.publisher().rsfService(CommonMetaEcho.class, note -> note).name("Other").version("1.0").register();
            });
            assertTrue(failure.getMessage().contains("Conflicting service route"));

            // 冲突发布不改变原服务的短地址及描述内容。
            URI uri = this.uri(server, first);
            assertEquals("/rpc/default/1.0/CommonMetaEcho?thrift", uri.getRawPath() + "?" + uri.getRawQuery());
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("service CommonMetaEcho"));
        }
    }

    @Test
    public void differentServiceSubpathsSeparateVersionDescriptors() throws Exception {
        // 协议挂载点下的服务子路径区分版本。
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol("thrift-http");
        configuration.connector("http").bind("127.0.0.1", 0) //
                .protocol("thrift-http").contextPath("/rpc");
        try (MetContext server = configuration.buildContext()) {
            server.start();
            server.publisher().rsfService(CommonMetaEcho.class, note -> note) //
                    .version("1.0").contextPathByPrefix("thrift-http", "/v1").register();
            server.publisher().rsfService(CommonMetaEcho.class, note -> note) //
                    .version("2.0").contextPathByPrefix("thrift-http", "/v2").register();

            // 两个已发布版本各自的原生 IDL 都能通过短地址读取。
            String origin = "http://" + server.getListenAddress("thrift-http").getHostPort();
            URI firstUri = URI.create(origin + "/rpc/v1/CommonMetaEcho?thrift");
            URI secondUri = URI.create(origin + "/rpc/v2/CommonMetaEcho?thrift");
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest firstRequest = HttpRequest.newBuilder(firstUri).GET().build();
            HttpRequest secondRequest = HttpRequest.newBuilder(secondUri).GET().build();
            var firstResponse = http.send(firstRequest, HttpResponse.BodyHandlers.ofString());
            var secondResponse = http.send(secondRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstResponse.statusCode());
            assertEquals(200, secondResponse.statusCode());
            assertTrue(firstResponse.body().contains("service CommonMetaEcho"));
            assertTrue(secondResponse.body().contains("service CommonMetaEcho"));
        }
    }

    private URI uri(MetContext server, MetBindInfo<?> service) {
        String serviceName = service.getBindType().getSimpleName();
        String path = "/rpc/" + service.getBindGroup() + "/" + service.getBindVersion() + "/" + serviceName + "?thrift";
        return URI.create("http://" + server.getListenAddress("thrift-http").getHostPort() + path);
    }

    private MetConfiguration configuration(boolean listener) {
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol("thrift-http");
        var http = configuration.connector("http");
        if (listener) {
            http.bind("127.0.0.1", 0);
        }
        http.protocol("thrift-http").contextPath("/rpc");
        return configuration;
    }
}
