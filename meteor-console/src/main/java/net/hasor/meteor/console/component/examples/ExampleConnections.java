/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 示例只覆盖普通连接；不能把 TLS 或 WebSocket 端点生成为明文调用。 */
public final class ExampleConnections {
    private static final Set<String> SECURE_SCHEMES = Set.of("https", "wss", "rsfs", "grpcs", "tls", "ssl", "tcps");

    private ExampleConnections() {
    }

    /** HTTP 的 SDK 地址携带连接挂载；具体服务路径仍由服务绑定负责。 */
    public static String contextPath(ExampleTarget target) {
        if (StringUtils.equals(target.getEndpoint().getTransport(), "http") && StringUtils.isNotBlank(target.getAddress())) {
            String path = URI.create(target.getAddress()).getRawPath();
            return StringUtils.isBlank(path) ? "/" : path;
        }
        return target.getEndpoint().getContextPath();
    }

    /** 返回服务声明的相对路径，默认推导路径无需重复配置。 */
    public static String[] servicePaths(ExampleTarget target) {
        MetProtocolDescription description = target.getEndpoint().getDescription();
        if (description == null || !description.getExtensions().containsKey("servicePaths")) {
            return new String[0];
        }
        Object paths = description.getExtensions().get("servicePaths");
        if (!(paths instanceof Collection<?> values) || values.stream().anyMatch(value -> !(value instanceof String))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_SERVICE_SUBPATH_DESCRIPTION);
        }
        return values.toArray(new String[0]);
    }

    public static void validate(ExampleTarget target) {
        String scheme = target.getEndpoint().getScheme();
        if (scheme != null && SECURE_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_ENDPOINT_REQUIRES_CUSTOM_TLS_CONFIGURATION);
        }
        if (StringUtils.equalsIgnoreCase(scheme, "ws")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_ENDPOINT_REQUIRES_A_WEBSOCKET_UPGRADE);
        }

        MetProtocolDescription binding = target.getEndpoint().getDescription();
        if (binding == null) {
            return;
        }
        Object facts = binding.getExtensions().get("connection");
        if (facts == null) {
            // 历史默认协议快照仍可生成；已知安全与升级 scheme 已在上面明确排除。
            return;
        }
        if (!(facts instanceof Map<?, ?> connection)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ENDPOINT_CONNECTION_DESCRIPTION_IS_INVALID);
        }
        Object httpScheme = connection.get("httpScheme");
        boolean https = httpScheme instanceof String value && StringUtils.equalsIgnoreCase(value, "https");
        if (Boolean.TRUE.equals(connection.get("tlsEnabled")) || https) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_ENDPOINT_REQUIRES_CUSTOM_TLS_CONFIGURATION);
        }
        Object upgrade = connection.get("upgrade");
        if (upgrade instanceof String value && StringUtils.equalsIgnoreCase(value, "websocket")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_ENDPOINT_REQUIRES_A_WEBSOCKET_UPGRADE);
        }
    }
}
