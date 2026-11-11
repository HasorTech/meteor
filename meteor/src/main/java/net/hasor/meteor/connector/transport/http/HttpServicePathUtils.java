/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package net.hasor.meteor.connector.transport.http;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;

/** HTTP 服务默认路径的身份编码；显式服务路径相对于协议挂载点。 */
public final class HttpServicePathUtils {
    private HttpServicePathUtils() {
    }

    public static ProtocolBinding bind(ProtocolConfig config, ProtocolMeta meta, Object definition, ObjectNode description) {
        boolean scoped = meta.getContextPath() == null && config.option("contextPath", null) != null;
        description.put("serviceScoped", scoped);
        description.put("serviceBasePath", config.option("contextPath", null));
        return meta.build(config, definition, description, path -> {
            return qualify(path, meta.getService(), description);
        });
    }

    public static String qualify(String path, MetBindInfo<?> service) {
        String base = StringUtils.equals(path, "/") ? "" : path;
        return base + suffix(service);
    }

    /** SDK 连接协议挂载点，由消费绑定补齐服务身份或显式子路径。 */
    public static String clientPath(ProtocolBinding binding, String servicePath) {
        ObjectNode description = binding.getDescription();
        if (description != null && (description.path("serviceScoped").asBoolean() || description.has("servicePaths"))) {
            return description.path("serviceBasePath").asText(null);
        }
        return servicePath;
    }

    private static String qualify(String path, MetBindInfo<?> service, ObjectNode description) {
        String qualified = qualify(path, service);
        if (description.has("servicePathName")) {
            qualified += "/" + segment(description.path("servicePathName").asText());
        }
        return qualified;
    }

    /** 目标既可以是监听挂载，也可以是注册中心下推的完整服务挂载。 */
    public static String target(String path, MetBindInfo<?> service, ObjectNode description) {
        String suffix = suffix(service);
        if (description.has("servicePathName")) {
            suffix += "/" + segment(description.path("servicePathName").asText());
        }

        // 本地显式配置的监听目录先按目录解释，即使它恰好以分组/版本结尾。
        if (StringUtils.equals(path, description.path("serviceBasePath").asText(null))) {
            return qualify(path, service, description);
        }
        return path.endsWith(suffix) ? path : qualify(path, service, description);
    }

    private static String suffix(MetBindInfo<?> service) {
        return "/" + segment(service.getBindGroup()) + "/" + segment(service.getBindVersion());
    }

    /** 空值使用 ~；真实 ~、分隔符与百分号编码，避免不同身份被归并。 */
    public static String segment(String value) {
        if (value == null || value.isEmpty()) {
            return "~";
        }
        boolean dot = StringUtils.equals(value, ".") || StringUtils.equals(value, "..");
        StringBuilder result = new StringBuilder();
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            int current = element & 0xff;
            boolean safe = current >= 'a' && current <= 'z' || current >= 'A' && current <= 'Z' //
                    || current >= '0' && current <= '9' || current == '-' || current == '_' || current == '.' && !dot;
            if (safe) {
                result.append((char) current);
            } else {
                result.append('%').append(Character.toUpperCase(Character.forDigit(current >>> 4, 16))) //
                        .append(Character.toUpperCase(Character.forDigit(current & 15, 16)));
            }
        }
        return result.toString();
    }
}
