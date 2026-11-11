/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.shared.storage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.shared.storage.domain.DirectoryQuery;
import net.hasor.meteor.registry.shared.storage.domain.EntServiceMethodData;
import net.hasor.meteor.registry.shared.storage.domain.EntServiceRuntimeData;
import net.hasor.meteor.registry.shared.storage.domain.RefServiceDescriptionData;
import net.hasor.meteor.registry.shared.utils.RegistryJson;

/** 从提供者快照提取可独立读取的方法，不持有实例或存储连接。 */
public final class ServiceMethodUtils {
    private ServiceMethodUtils() {
    }

    /** 服务和方法分别散列；保留前缀查询能力，不因完整名称过长截断业务 ID。 */
    public static String prefix(String serviceId) {
        return identity(serviceId) + "/";
    }

    public static String path(String serviceId, String methodName) {
        return prefix(serviceId) + identity(methodName);
    }

    private static String identity(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Service ID and method name must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public static Map<String, EntServiceMethodData> extract(EntServiceRuntimeData service) {
        Map<String, EntServiceMethodData> result = new TreeMap<>();
        ObjectNode source = RegistryJson.copy(service.getDescription(), ObjectNode.class);
        if (service.getRole() != MetServiceType.Provider || source == null) {
            return result;
        }
        for (JsonNode method : source.path("methods")) {
            String name = method.path("name").asText();
            if (StringUtils.isBlank(name)) {
                name = method.path("javaName").asText();
            }
            if (StringUtils.isBlank(name)) {
                continue;
            }
            // 去重依据是方法及可达类型；根接口名、实例端点不参与方法定义。
            ObjectNode definition = JsonNodeFactory.instance.objectNode();
            definition.putArray("methods").add(method.deepCopy());
            // 沿参数、结果和异常引用收集类型，访问集合终止自引用和互相引用。
            ObjectNode types = definition.putObject("types");
            Set<String> visited = new HashSet<>();
            collectTypes(method, source.path("types"), types, visited);
            EntServiceMethodData entry = result.computeIfAbsent(name, key -> {
                EntServiceMethodData value = new EntServiceMethodData();
                value.setServiceId(service.getServiceId());
                value.setName(key);
                return value;
            });
            if (entry.getDefinitions().stream().noneMatch(existing -> sameDefinition(RegistryJson.copy(existing, ObjectNode.class), definition))) {
                entry.getDefinitions().add(RegistryJson.copy(definition, RefServiceDescriptionData.class));
            }
        }
        return result;
    }

    private static void collectTypes(JsonNode node, JsonNode available, ObjectNode types, Set<String> visited) {
        if (node.isTextual()) {
            String reference = node.asText();
            JsonNode type = available.get(reference);
            if (type != null && visited.add(reference)) {
                types.set(reference, type.deepCopy());
                collectTypes(type, available, types, visited);
            }
        } else if (node.isContainerNode()) {
            for (JsonNode child : node) {
                collectTypes(child, available, types, visited);
            }
        }
    }

    public static EntServiceMethodData merge(EntServiceMethodData previous, EntServiceMethodData added) {
        EntServiceMethodData merged = new EntServiceMethodData();
        merged.setServiceId(added.getServiceId());
        merged.setName(added.getName());
        if (previous != null) {
            appendUnique(merged, previous);
        }
        appendUnique(merged, added);
        return merged;
    }

    private static void appendUnique(EntServiceMethodData target, EntServiceMethodData source) {
        for (RefServiceDescriptionData definition : source.getDefinitions()) {
            if (target.getDefinitions().stream().noneMatch(existing -> sameDefinition(RegistryJson.copy(existing, ObjectNode.class), RegistryJson.copy(definition, ObjectNode.class)))) {
                target.getDefinitions().add(RegistryJson.copy(definition, RefServiceDescriptionData.class));
            }
        }
    }

    private static boolean sameDefinition(JsonNode left, JsonNode right) {
        // JSON 整数不区分 Java int/long；持久化往返不能改变方法定义的身份。
        return left.equals((first, second) -> {
            if (first.isIntegralNumber() && second.isIntegralNumber()) {
                return first.bigIntegerValue().compareTo(second.bigIntegerValue());
            }
            return first.equals(second) ? 0 : 1;
        }, right);
    }

    public static void validate(DirectoryQuery query) {
        if (query == null || query.getLimit() < 1 || query.getLimit() > 1000) {
            throw new IllegalArgumentException("Page limit must be between 1 and 1000");
        }
    }
}
