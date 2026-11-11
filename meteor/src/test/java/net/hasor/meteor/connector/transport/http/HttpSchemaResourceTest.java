/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.RouteMountIndex;
import net.hasor.meteor.connector.transport.http.channel.HttpRequest;
import net.hasor.meteor.connector.transport.http.channel.HttpResponse;
import net.hasor.meteor.domain.*;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HttpSchemaResourceTest {
    private MetContext              context;
    private RouteMountIndex         routes;
    private ServiceDomain<Runnable> service;
    private ProtocolBinding         binding;

    @BeforeEach
    public void publishedService() {
        this.service = new ServiceDomain<>(Runnable.class);
        this.service.setServiceType(MetServiceType.Provider);
        this.binding = new ProtocolBinding(new ContextPath(List.of("/rpc"), false), null, JsonNodeFactory.instance.objectNode());
        ProtocolMeta meta = new ProtocolMeta(this.service, null, null, Map.of("listener", this.binding));
        this.service.replaceProtocolPublication(Map.of("grpc", meta));
        ProtocolConfig config = ProtocolConfig.newConfig("grpc", "grpc", Map.of("contextPath", "/rpc"));
        this.routes = new RouteMountIndex("listener", config);
        ClassLoader loader = getClass().getClassLoader();
        Class<?>[] interfaces = { MetContext.class };
        this.context = (MetContext) Proxy.newProxyInstance(loader, interfaces, (proxy, method, arguments) -> {
            if (StringUtils.equals(method.getName(), "getServiceIDs")) {
                return List.of("service");
            }
            if (StringUtils.equals(method.getName(), "getServiceInfo")) {
                return this.service;
            }
            throw new AssertionError(method);
        });
    }

    @Test
    public void originalSingleSourceIsReturnedVerbatim() {
        // 文件声明只追加 URL，不把源文件正文放入可上报描述。
        String original = "// original\nservice Echo {}\n";
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", Map.of("echo.proto", original));
        resource.describe(this.binding, List.of("example.Echo"));
        assertEquals("/rpc/example.Echo?proto", this.binding.getDescription().path("endpoints").path("/rpc").path("schema").asText());
        assertFalse(this.binding.getDescription().toString().contains(original));

        // 不需要文件名参数，服务路径唯一确定原文件。
        HttpResponse response = this.receive("GET", "/rpc/example.Echo?proto", resource);
        assertEquals(200, response.status());
        assertEquals(original, new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    public void headRetainsTheLengthWithoutSendingTheFile() {
        // HEAD 与 GET 使用同一个文件表示。
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", Map.of("echo.proto", "service Echo {}"));
        resource.describe(this.binding, List.of("example.Echo"));
        HttpResponse get = this.receive("GET", "/rpc/example.Echo?proto", resource);
        HttpResponse head = this.receive("HEAD", "/rpc/example.Echo?proto", resource);
        assertEquals(200, head.status());
        assertEquals(get.headers(), head.headers());
        assertEquals(0, head.body().length);
    }

    @Test
    public void postRemainsOwnedByTheRpcProtocol() {
        // 即使查询标记相同，也不截获 POST 或检查业务正文。
        HttpRequest request = HttpRequest.newBuffered("POST", "/rpc/example.Echo/mirror?proto", //
                Map.of(), new byte[] { 1 }, 2, Map.of());
        assertNull(HttpSchemaResource.receive(request, null, null, "proto", binding -> fail()));
    }

    @Test
    public void businessQueryIsNotClaimedAsAFileRequest() {
        // 只有完整的保留标记被接管，其它查询仍交给协议处理。
        assertNull(this.receive("GET", "/rpc/example.Echo?proto=value", null));
        assertNull(this.receive("GET", "/rpc/example.Echo?proto&parameter=value", null));
    }

    @Test
    public void undeclaredServiceCannotReadAnotherServiceFiles() {
        // 文件集合属于已发布服务，不按用户路径读取磁盘。
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", Map.of("echo.proto", "private binding"));
        resource.describe(this.binding, List.of("example.Echo"));
        assertEquals(404, this.receive("GET", "/rpc/other.Service?proto", resource).status());
        assertEquals(404, this.receive("GET", "/rpc/../../echo.proto?proto", resource).status());
    }

    @Test
    public void shadowServiceDoesNotExposeItsFiles() {
        // 隐藏服务不会因为本地有绑定而意外开放文件。
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", Map.of("echo.proto", "service Echo {}"));
        resource.describe(this.binding, List.of("example.Echo"));
        this.service.setShadow(true);
        assertEquals(404, this.receive("GET", "/rpc/example.Echo?proto", resource).status());
    }

    @Test
    public void multiFilePageEscapesSourceAndOffersWholeArchive() throws Exception {
        // 展示依赖图全部原文，不能把原文件注释作为 HTML 执行。
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("echo.proto", "// <script>alert(1)</script>\nimport \"types.proto\";");
        sources.put("types.proto", "message Types {}");
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", sources);
        resource.describe(this.binding, List.of("example.Echo"));
        String page = new String(this.receive("GET", "/rpc/example.Echo?proto", resource).body(), StandardCharsets.UTF_8);
        assertTrue(page.contains("&lt;script&gt;"));
        assertTrue(page.contains("types.proto"));
        assertTrue(page.contains("?proto&amp;download"));

        // 下载包保持每个原文件与 include 路径，正文不做二次生成。
        HttpResponse response = this.receive("GET", "/rpc/example.Echo?proto&download", resource);
        Map<String, String> restored = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                restored.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(sources, restored);
    }

    @Test
    public void parentIncludesRemainResolvableInsideTheArchive() throws Exception {
        // 源文件合法引用父目录时，整体重定位；包内不存在可穿越解压目录的条目。
        Map<String, String> sources = Map.of("echo.thrift", "include \"../types.thrift\"", "../types.thrift", "struct Value {}");
        HttpSchemaResource resource = new HttpSchemaResource("thrift", "echo.thrift", sources);
        resource.describe(this.binding, List.of("Echo"));
        HttpRequest request = HttpRequest.newBuffered("GET", "/rpc/Echo?thrift&download", Map.of(), new byte[0], 1, Map.of());
        HttpResponse response = HttpSchemaResource.receive(request, this.context, this.routes, "thrift", binding -> resource);
        Map<String, String> restored = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                restored.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals("include \"../types.thrift\"", restored.get("schema/echo.thrift"));
        assertEquals("struct Value {}", restored.get("types.thrift"));
    }

    @Test
    public void selectedArchiveRetainsItsSelectionInTheDownloadLink() throws Exception {
        // 分组中保留特殊字符，查询顺序不影响选择；页面下载链接必须携带相同条件。
        Map<String, String> sources = Map.of("echo.proto", "import \"types.proto\";", "types.proto", "message Value {}");
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", sources);
        resource.describe(this.binding, List.of("example.Echo"), Map.of("group", "blue &+", "version", "2.0"));
        String target = "/rpc/example.Echo?proto&version=2.0&group=blue%20%26%2B";
        HttpRequest request = HttpRequest.newBuffered("GET", target, Map.of(), new byte[0], 1, Map.of());
        Set<String> selectors = Set.of("group", "version");
        HttpResponse response = HttpSchemaResource.receive(request, this.context, this.routes, "proto", selectors, item -> resource);
        assertEquals(200, response.status());
        String page = new String(response.body(), StandardCharsets.UTF_8);
        assertTrue(page.contains("?proto&amp;version=2.0&amp;group=blue%20%26%2B&amp;download"));

        // 用页面生成的同条件下载入口读取原文件集合。
        HttpRequest download = HttpRequest.newBuffered("GET", target + "&download", Map.of(), new byte[0], 1, Map.of());
        HttpResponse archive = HttpSchemaResource.receive(download, this.context, this.routes, "proto", selectors, item -> resource);
        assertEquals(200, archive.status());
        Map<String, String> restored = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive.body()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                restored.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(sources, restored);
    }

    @Test
    public void unknownVersionDoesNotReturnAnotherVersion() {
        // 路径相同也不能返回不匹配版本的文件。
        HttpSchemaResource resource = new HttpSchemaResource("proto", "echo.proto", Map.of("echo.proto", "version two"));
        resource.describe(this.binding, List.of("example.Echo"), Map.of("version", "2.0"));
        HttpRequest request = HttpRequest.newBuffered("GET", "/rpc/example.Echo?proto&version=1.0", //
                Map.of(), new byte[0], 1, Map.of());
        HttpResponse response = HttpSchemaResource.receive(request, this.context, this.routes, //
                "proto", Set.of("version"), item -> resource);
        assertEquals(404, response.status());
    }

    @Test
    public void selectedProtoQueryDoesNotInterceptPost() {
        // 版本选择只属于文件 GET/HEAD，POST 的相同参数仍交给 RPC 协议。
        HttpRequest request = HttpRequest.newBuffered("POST", "/rpc/example.Echo/mirror?proto&version=2.0", //
                Map.of(), new byte[] { 1 }, 2, Map.of());
        assertNull(HttpSchemaResource.receive(request, null, null, "proto", Set.of("version"), item -> fail()));
    }

    private HttpResponse receive(String method, String uri, HttpSchemaResource resource) {
        HttpRequest request = HttpRequest.newBuffered(method, uri, Map.of(), new byte[0], 1, Map.of());
        return HttpSchemaResource.receive(request, this.context, this.routes, "proto", binding -> resource);
    }
}
