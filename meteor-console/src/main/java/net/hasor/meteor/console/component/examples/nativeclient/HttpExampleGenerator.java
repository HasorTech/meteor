/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.ObjectUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.description.*;

/** 使用协议导出的线上方法名和实际 HTTP 挂载生成原生客户端。 */
public final class HttpExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (!Set.of("jsonrpc", "xmlrpc", "hprose").contains(protocol)) {
            return List.of();
        }

        ExampleConnections.validate(target);
        MetServiceDescription description = target.getDescription();
        MetProtocolDescription binding = target.getEndpoint().getDescription();
        Map<String, Object> call = null;
        IllegalArgumentException lastFailure = null;
        for (Map<String, Object> operation : binding.getMethods()) {
            try {
                call = call(description, binding, operation, protocol);
                break;
            } catch (IllegalArgumentException unavailable) {
                lastFailure = unavailable;
            }
        }
        if (call == null) {
            throw lastFailure == null ? new ErrorMessageException(ConsoleMessageKey.ERROR_NO_INVOCABLE_METHODS) : lastFailure;
        }
        try {
            JsonNode arguments = JsonUtils.valueToTree(call.get("args"));
            String method = JsonUtils.writeValueAsString(call.get("method"));
            String url = JsonUtils.writeValueAsString(httpAddress(target, address));
            List<ExampleDocument> documents = new ArrayList<>();
            if (StringUtils.equals(protocol, "jsonrpc")) {
                documents.add(document("python", "jsonrpc.py", url, method, arguments, ""));
                documents.add(document("javascript", "jsonrpc.mjs", url, method, arguments, ""));
            } else if (StringUtils.equals(protocol, "xmlrpc")) {
                documents.add(document("python", "xmlrpc.py", url, method, arguments, ""));
            } else {
                documents.add(document("javascript", "hprose.mjs", url, method, arguments, "npm install hprose@2.0.51"));
            }
            return documents;
        } catch (java.io.IOException failure) {
            throw ErrorMessageException.state(failure, ConsoleMessageKey.ERROR_COULD_NOT_GENERATE_INVOCATION_ARGUMENTS);
        }
    }

    public static Map<String, Object> call(MetServiceDescription description, MetProtocolDescription binding, Map<String, Object> operation, String protocol) {
        MetMethodDescription method = description.getMethods().stream().filter(item -> ObjectUtils.equals(item.getJavaSignature(), operation.get("javaSignature"))).findFirst().orElseThrow(() -> new ErrorMessageException(ConsoleMessageKey.ERROR_THE_WIRE_METHOD_HAS_NO_MATCHING));
        String name = Objects.toString(operation.get("name"), "");
        if (StringUtils.isBlank(name)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_WIRE_METHOD_NAME_IS_EMPTY);
        }
        if (!StringUtils.equals(protocol, "hprose")) {
            name = binding.getName() + "." + name;
        }
        List<Object> arguments = new ArrayList<>();
        List<MetFieldDescription> parameters = new ArrayList<>(method.getParameters());
        parameters.sort(Comparator.comparingInt(MetFieldDescription::getArgIndex));
        for (MetFieldDescription parameter : parameters) {
            arguments.add(sample(description, parameter.getType(), protocol, new HashSet<>()));
        }
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("method", name);
        call.put("args", arguments);
        return call;
    }

    private static ExampleDocument document(String language, String template, String url, String method, JsonNode arguments, String setup) {
        boolean python = StringUtils.equals(language, "python");
        String code = ExampleTemplates.render("http/" + template, Map.of(
                "url", url, "method", method, "arguments", nativeValue(arguments, python)));
        String markdown = ExampleTemplates.codeBlock(language, code);
        if (!setup.isEmpty()) {
            markdown = ExampleTemplates.codeBlock("bash", setup) + "\n\n" + markdown;
        }
        return new ExampleDocument(language + "-direct", language, "direct", markdown);
    }

    private static String nativeValue(JsonNode value, boolean python) {
        if (value.size() == 1 && value.path("$binary").isTextual()) {
            String bytes = value.path("$binary").asText();
            String hex = HexFormat.of().formatHex(Base64.getDecoder().decode(bytes));
            if (python) {
                return "bytes.fromhex(\"" + hex + "\")";
            }
            return "Buffer.from(\"" + hex + "\", \"hex\")";
        }
        if (value.isArray()) {
            StringJoiner items = new StringJoiner(", ", "[", "]");
            value.forEach(item -> items.add(nativeValue(item, python)));
            return items.toString();
        }
        if (value.isObject()) {
            StringJoiner fields = new StringJoiner(", ", "{", "}");
            value.fields().forEachRemaining(field -> {
                String key = JsonUtils.valueToTree(field.getKey()).toString();
                if (!python && StringUtils.equals(field.getKey(), "__proto__")) {
                    key = "[" + key + "]";
                }
                fields.add(key + ": " + nativeValue(field.getValue(), python));
            });
            return fields.toString();
        }
        if (python) {
            if (value.isBoolean()) {
                return value.asBoolean() ? "True" : "False";
            }
            if (value.isNull()) {
                return "None";
            }
        }
        return value.toString();
    }

    private static String httpAddress(ExampleTarget target, String providedAddress) {
        if (StringUtils.isBlank(providedAddress)) {
            return ExampleConnections.ADDRESS_PLACEHOLDER;
        }
        URI address = URI.create(providedAddress);
        String scheme = StringUtils.equalsIgnoreCase(address.getScheme(), "https") ? "https" : "http";
        String path = target.getEndpoint().getContextPath();
        if (StringUtils.isBlank(path)) {
            path = "/";
        }
        URI context = URI.create(path);
        if (!path.startsWith("/") || !StringUtils.equals(context.getRawPath(), path)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_SERVICE_HTTP_MOUNT_PATH);
        }
        try {
            URI authority = new URI(scheme, null, address.getHost(), address.getPort(), null, null, null);
            // context 是经过验证的原始路径，不能由多参数 URI 构造器再次编码其中的百分号。
            return URI.create(authority.toASCIIString() + path).toASCIIString();
        } catch (java.net.URISyntaxException failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_INVALID_SERVICE_HTTP_ADDRESS);
        }
    }

    public static Object sample(MetServiceDescription service, String reference, String protocol, Set<String> visiting) {
        MetTypeDescription type = service.getTypes().get(reference);
        if (type == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_PARAMETER_TYPE_DECLARATION, reference);
        }
        String java = type.getJavaType();
        if (StringUtils.equals(java, "byte[]")) {
            return StringUtils.equals(protocol, "jsonrpc") ? "" : Map.of("$binary", "");
        }
        if (!visiting.add(reference)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_RECURSIVE_ARGUMENT_REQUIRES_BUSINESS_DATA, reference);
        }
        try {
            return switch (type.getKind()) {
                case "array", "collection" -> List.of();
                case "map" -> Map.of();
                case "enum" -> {
                    if (type.getValues().isEmpty()) {
                        throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ENUM_HAS_NO_VALUES, reference);
                    }
                    yield type.getValues().get(0).getJavaName();
                }
                case "object" -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    for (MetFieldDescription property : type.getProperties()) {
                        value.put(property.getJavaName(), sample(service, property.getType(), protocol, visiting));
                    }
                    yield value;
                }
                case "scalar" -> scalar(java);
                default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_NATIVE_LANGUAGE_EXAMPLES_CANNOT_BE_GENERATED, reference);
            };
        } finally {
            visiting.remove(reference);
        }
    }

    private static Object scalar(String java) {
        return switch (java) {
            case "java.lang.String" -> "example";
            case "char", "java.lang.Character" -> "x";
            case "boolean", "java.lang.Boolean" -> false;
            case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.math.BigInteger" -> 1;
            case "float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" -> 1.0;
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_SCALAR_TYPE_REQUIRES_BUSINESS_DATA, java);
        };
    }
}
