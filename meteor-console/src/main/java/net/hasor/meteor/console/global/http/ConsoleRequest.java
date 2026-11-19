/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.http;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.console.global.error.ConsoleHttpException;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.web.Invoker;

/** HTTP 参数只在控制台边界解析，不进入注册中心的数据模型。 */
public final class ConsoleRequest {
    private final Map<String, String> query = new LinkedHashMap<>();
    private final JsonNode            body;
    private final String              managementToken;
    private final Locale              locale;

    ConsoleRequest(URI uri, JsonNode body, String managementToken, Locale locale) {
        this.locale = locale;
        this.body = body;
        this.managementToken = managementToken;
        String raw = uri.getRawQuery();
        if (raw == null) {
            return;
        }
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (this.query.putIfAbsent(key, value) != null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_DUPLICATE_QUERY_PARAMETER, key);
            }
        }
    }

    public String query(String name) {
        return this.query.get(name);
    }

    public String required(String name) {
        String value = this.query(name);
        if (StringUtils.isBlank(value)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_PARAMETER, name);
        }
        return value;
    }

    public String text(String name) {
        JsonNode value = this.body.get(name);
        if (value == null || !value.isTextual()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_FIELD_MUST_BE_STRING, name);
        }
        return value.textValue();
    }

    public boolean flag(String name) {
        JsonNode value = this.body.get(name);
        if (value == null || !value.isBoolean()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_FIELD_MUST_BE_BOOLEAN, name);
        }
        return value.booleanValue();
    }

    public JsonNode body() {
        return this.body;
    }

    public boolean direct() {
        String mode = this.required("mode");
        if (StringUtils.equals("direct", mode)) {
            return true;
        }
        if (!StringUtils.equals("registry", mode)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MANAGEMENT_MODE_MUST_BE_REGISTRY_OR);
        }
        return false;
    }

    public Locale locale() {
        return this.locale;
    }

    public static ConsoleRequest from(Invoker invoker) {
        return (ConsoleRequest) invoker.getHttpRequest().getAttribute(ConsoleRequest.class.getName());
    }

    public MetRuleType ruleType() {
        return MetRuleType.valueOf(this.required("type"));
    }

    public ManagementTarget target() {
        ManagementTarget target = new ManagementTarget();
        target.setDirect(this.direct());
        target.setClusterId(this.query("clusterId"));
        target.setInstanceId(this.query("instanceId"));
        target.setServiceId(this.query("serviceId"));
        target.setScope(this.query("scope"));
        target.setFilter(this.query("filter"));
        target.setField(this.query("field"));
        target.setProtocol(this.query("protocol"));
        target.setCursor(StringUtils.trimToNull(this.query("cursor")));
        target.setUnitName(StringUtils.trimToNull(this.query("unitName")));
        String limit = this.query("limit");
        if (limit != null) {
            target.setLimit(Integer.parseInt(limit));
        }
        target.setApplicationName(this.query("applicationName"));
        if (target.isDirect()) {
            URI uri = URI.create(this.required("target"));
            boolean extra = uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null;
            if (extra || !InterAddress.checkFormat(uri)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_DIRECT_MANAGEMENT_ADDRESS);
            }
            target.setTarget(new InterAddress(uri));
            if (StringUtils.isBlank(this.managementToken) || this.managementToken.length() > 32768) {
                throw new ConsoleHttpException(403, ConsoleMessageKey.ERROR_DIRECT_TOKEN_REQUIRED);
            }
            String token;
            try {
                token = new String(Base64.getDecoder().decode(this.managementToken), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException malformed) {
                throw new ConsoleHttpException(403, ConsoleMessageKey.ERROR_INVALID_DIRECT_TOKEN_FORMAT);
            }
            if (StringUtils.isBlank(token) || token.length() > 4096) {
                throw new ConsoleHttpException(403, ConsoleMessageKey.ERROR_DIRECT_TOKEN_REQUIRED);
            }
            target.setManagementToken(token);
        }
        return target;
    }

}
