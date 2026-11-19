/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.service;
import java.nio.file.Path;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.core.Inject;
import net.hasor.meteor.console.component.debug.DebugInvoker;
import net.hasor.meteor.console.component.protocols.ConsoleTypes;
import net.hasor.meteor.console.component.settings.ConsoleCapabilities;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;

/** 调试必须绑定一个服务、一个提供者，并重新核验实际发布端点。 */
public final class ServiceDebugService {
    @Inject
    private GovernanceService governance;
    @Inject
    private MeteorConfig      config;

    @Inject
    private ConsoleTypes        consoleTypes;
    @Inject
    private ConsoleCapabilities capabilities;

    public Object describe(ManagementTarget target) {
        target.requireServiceId();
        target.requireInstanceId();
        var service = this.governance.serviceInfo(target);
        List<Object> endpoints = new ArrayList<>();
        for (var endpoint : this.governance.exampleTargets(target, null, null, null).getTargets()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", endpoint.getId());
            item.put("application", endpoint.getApplication());
            item.put("unit", endpoint.getUnit());
            item.put("address", endpoint.getAddress());
            item.put("endpoint", endpoint.getEndpoint());
            item.put("parameterTypes", this.consoleTypes.types(endpoint.getEndpoint().getProtocolName()));
            endpoints.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", service.info());
        result.put("description", service.description());
        result.put("endpoints", endpoints);
        return result;
    }

    public Object invoke(ManagementTarget target, JsonNode request, Locale locale) {
        target.requireServiceId();
        target.requireInstanceId();
        String protocol = required(request, "protocol");
        this.capabilities.requireDebugProtocol(protocol);
        String address = required(request, "address");
        String method = required(request, "method");
        JsonNode arguments = request.get("arguments");
        if (arguments == null || !arguments.isArray())
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_ARGUMENTS_MUST_BE_A_JSON_ARRAY);
        int timeout = request.path("timeout").asInt(5000);
        if (timeout < 100 || timeout > 30000)
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_DEBUG_TIMEOUT);
        String context = null;
        if (request.hasNonNull("contextPath"))
            context = request.get("contextPath").asText();
        var targets = this.governance.exampleTargets(target, protocol, address, context).getTargets();
        if (targets.size() != 1 || !StringUtils.equals(targets.get(0).getInstanceId(), target.getInstanceId())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_PROVIDER_IS_OFFLINE_OR_DISABLED);
        }
        long started = System.nanoTime();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("address", targets.get(0).getAddress());
        try {
            Object value = new DebugInvoker().invoke(targets.get(0), method, arguments, timeout, Path.of(this.config.getDataDirectory(), "debug"));
            response.put("success", true);
            response.put("value", value);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            response.put("success", false);
            response.put("errors", List.of(Map.of("type", failure.getClass().getName(), "message", ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_DEBUG_INTERRUPTED, locale))));
        } catch (Exception failure) {
            response.put("success", false);
            List<Object> errors = new ArrayList<>();
            Throwable cause = failure;
            for (int i = 0; cause != null && i < 16; i++, cause = cause.getCause()) {
                errors.add(Map.of("type", cause.getClass().getName(), "message", ConsoleErrorMessages.getMessage(cause, locale)));
            }
            response.put("errors", errors);
        }
        response.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
        return response;
    }

    private static String required(JsonNode request, String name) {
        JsonNode value = request.get(name);
        if (value == null || !value.isTextual() || StringUtils.isBlank(value.asText())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_FIELD, name);
        }
        return value.asText();
    }
}
