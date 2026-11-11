/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.text.StringEscapeUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.RouteMount;
import net.hasor.meteor.connector.RouteMountIndex;
import net.hasor.meteor.connector.transport.http.channel.HttpRequest;
import net.hasor.meteor.connector.transport.http.channel.HttpResponse;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.domain.ProtocolBinding;

/** 已发布 HTTP 契约的文件集合；文件仅保存在本地绑定，不进入注册中心的描述正文。 */
public final class HttpSchemaResource {
    private final String              type;
    private final String              entry;
    private final Map<String, String> sources;

    public HttpSchemaResource(String type, String entry, Map<String, String> sources) {
        this.type = Objects.requireNonNull(type, "type");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        if (!type.matches("[a-z]+") || !sources.containsKey(entry)) {
            throw new IllegalArgumentException("Schema requires a known type and an explicit entry file");
        }
    }

    /** 描述地址沿用当前服务目录或精确路径；身份分量由协议确定，不依赖本地文件位置。 */
    public void describe(ProtocolBinding binding, List<String> identity) {
        this.describe(binding, identity, Map.of());
    }

    /** 支持多版本共用业务路径的协议，可在文件查询中附带其服务选择条件。 */
    public void describe(ProtocolBinding binding, List<String> identity, Map<String, String> selection) {
        if (binding.getContextPath() == null) {
            return;
        }
        ObjectNode endpoints = binding.getDescription().withObject("endpoints");
        String suffix = identity.stream().map(HttpSchemaResource::segment).reduce((left, right) -> {
            return left + "/" + right;
        }).orElse("");
        StringBuilder query = new StringBuilder(this.type);
        selection.forEach((key, value) -> {
            query.append('&').append(segment(key)).append('=').append(segment(value));
        });

        for (String path : binding.getContextPath().getPaths()) {
            String target = path;
            if (!binding.getContextPath().isExact() && !suffix.isEmpty()) {
                target = (path.endsWith("/") ? path : path + "/") + suffix;
            }
            ObjectNode addresses = (ObjectNode) endpoints.get(path);
            if (addresses == null) {
                addresses = endpoints.putObject(path);
            }
            addresses.put("schema", target + "?" + query);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 只有 GET/HEAD 的完整文件标记才被接管；POST、升级及其他查询保持协议原有行为。 */
    public static HttpResponse receive(HttpRequest request, MetContext context, RouteMountIndex routes, //
            String type, Function<ProtocolBinding, HttpSchemaResource> resources) {
        return receive(request, context, routes, type, Set.of(), resources);
    }

    /** 文件查询的选择字段由协议声明，不截获未声明的业务查询参数。 */
    public static HttpResponse receive(HttpRequest request, MetContext context, RouteMountIndex routes, //
            String type, Set<String> selectorNames, Function<ProtocolBinding, HttpSchemaResource> resources) {
        boolean head = StringUtils.equals(request.method(), "HEAD");
        if (!head && !StringUtils.equals(request.method(), "GET")) {
            return null;
        }
        int separator = request.uri().indexOf('?');
        if (separator < 0) {
            return null;
        }
        String query = request.uri().substring(separator + 1);
        boolean download = query.endsWith("&download");
        if (download) {
            query = query.substring(0, query.length() - "&download".length());
        }
        Map<String, String> selection = selection(query, type, selectorNames);
        if (selection == null) {
            return null;
        }
        if (request.header("Upgrade") != null) {
            return null;
        }
        if (request.stream() != null) {
            // 流式挂载在头部结束即交付请求。文件入口不消费业务正文，也不能阻塞网络线程等待 EOF。
            request.stream().discard();
            String length = request.header("Content-Length");
            if ((length != null && !StringUtils.equals(length, "0")) || request.header("Transfer-Encoding") != null) {
                return empty(400);
            }
        } else if (request.body().length != 0) {
            return empty(400);
        }

        RouteMount mount = request.route() == null ? routes.match(request.uri()) : request.route().mount();
        if (mount == null) {
            return empty(404);
        }
        String path = request.uri().substring(0, separator);
        HttpSchemaResource selected = null;
        for (String id : context.getServiceIDs()) {
            MetBindInfo<?> service = context.getServiceInfo(id);
            if (service == null || service.isShadow() || service.getServiceType() != MetServiceType.Provider) {
                continue;
            }
            if (!routes.accepts(service, mount)) {
                continue;
            }
            ProtocolBinding binding = routes.binding(service);
            if (binding == null || binding.getDescription() == null) {
                continue;
            }
            JsonNode definitions = binding.getDescription().path("endpoints");
            boolean matches = false;
            for (JsonNode addresses : definitions) {
                JsonNode address = addresses.get("schema");
                if (address == null) {
                    continue;
                }
                URI uri = URI.create(address.asText());
                Map<String, String> declared = selection(uri.getRawQuery(), type, selectorNames);
                boolean selectedService = declared != null && declared.entrySet().containsAll(selection.entrySet());
                if (selectedService && StringUtils.equals(path, uri.getRawPath())) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            HttpSchemaResource resource = resources.apply(binding);
            if (resource == null) {
                continue;
            }
            if (selected != null) {
                return empty(409);
            }
            selected = resource;
        }
        return selected == null ? empty(404) : selected.response(head, download, query);
    }

    private static Map<String, String> selection(String query, String type, Set<String> selectorNames) {
        if (query == null) {
            return null;
        }
        String[] parts = query.split("&", -1);
        if (!StringUtils.equals(parts[0], type)) {
            return null;
        }
        Map<String, String> selection = new LinkedHashMap<>();
        for (int index = 1; index < parts.length; index++) {
            int separator = parts[index].indexOf('=');
            if (separator <= 0) {
                return null;
            }
            String key = parts[index].substring(0, separator);
            if (!selectorNames.contains(key) || selection.containsKey(key)) {
                return null;
            }
            try {
                selection.put(key, URLDecoder.decode(parts[index].substring(separator + 1), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException invalidEncoding) {
                return null;
            }
        }
        return selection;
    }

    private HttpResponse response(boolean head, boolean download, String query) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        byte[] body;
        String filename = Path.of(this.entry).getFileName().toString();
        if (this.sources.size() == 1) {
            body = this.sources.get(this.entry).getBytes(StandardCharsets.UTF_8);
            headers.put("Content-Type", List.of("text/plain; charset=utf-8"));
        } else if (download) {
            try {
                body = this.archive();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot export bound schema files", failure);
            }
            filename += ".zip";
            headers.put("Content-Type", List.of("application/zip"));
        } else {
            body = this.page(query).getBytes(StandardCharsets.UTF_8);
            headers.put("Content-Type", List.of("text/html; charset=utf-8"));
        }
        if (download) {
            headers.put("Content-Disposition", List.of("attachment; filename*=UTF-8''" + segment(filename)));
        }
        headers.put("Content-Length", List.of(Integer.toString(body.length)));
        headers.put("Cache-Control", List.of("no-store"));
        headers.put("X-Content-Type-Options", List.of("nosniff"));
        headers.put("Content-Security-Policy", List.of("default-src 'none'; base-uri 'none'; frame-ancestors 'none'"));
        return HttpResponse.newBuffered(200, headers, head ? new byte[0] : body, Map.of());
    }

    private String page(String query) {
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\">");
        html.append("<title>契约文件</title><body>");
        html.append("<h1>契约文件</h1><p><a href=\"?").append(StringEscapeUtils.escapeHtml4(query));
        html.append("&amp;download\">下载全部定义文件</a></p>");
        List<String> names = new ArrayList<>(this.sources.keySet());
        names.sort(Comparator.comparing((String name) -> !StringUtils.equals(name, this.entry)).thenComparing(name -> name));
        for (String name : names) {
            html.append("<details open><summary>").append(StringEscapeUtils.escapeHtml4(name));
            html.append("</summary><pre><code>").append(StringEscapeUtils.escapeHtml4(this.sources.get(name)));
            html.append("</code></pre></details>");
        }
        return html.append("</body></html>").toString();
    }

    private byte[] archive() throws IOException {
        // 父目录 include 通过共同前缀重定位，保留原引用关系，ZIP 条目不带绝对路径或 ..。
        int parents = 0;
        for (String name : this.sources.keySet()) {
            Path path = Path.of(name).normalize();
            if (path.isAbsolute() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
                throw new IOException("Invalid schema resource name: " + name);
            }
            int depth = 0;
            for (Path part : path) {
                if (!StringUtils.equals(part.toString(), "..")) {
                    break;
                }
                depth++;
            }
            parents = Math.max(parents, depth);
        }
        Path root = Path.of("schema/".repeat(parents));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> source : new TreeMap<>(this.sources).entrySet()) {
                String name = root.resolve(source.getKey()).normalize().toString().replace('\\', '/');
                if (!names.add(name)) {
                    throw new IOException("Conflicting schema resource name: " + name);
                }
                ZipEntry item = new ZipEntry(name);
                item.setTime(0);
                zip.putNextEntry(item);
                zip.write(source.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static HttpResponse empty(int status) {
        return HttpResponse.newBuffered(status, Map.of(), new byte[0], Map.of());
    }
}
