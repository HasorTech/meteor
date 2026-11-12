/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.ObjectUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
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
        String lastFailure = "当前接口没有可调用的方法";
        for (Map<String, Object> operation : binding.getMethods()) {
            try {
                call = call(description, binding, operation, protocol);
                break;
            } catch (IllegalArgumentException unavailable) {
                lastFailure = unavailable.getMessage();
            }
        }
        if (call == null) {
            throw new IllegalArgumentException(lastFailure);
        }
        try {
            String sample = JsonUtils.valueToTree(call).toPrettyString();
            String url = JsonUtils.writeValueAsString(httpAddress(target, address));
            List<ExampleDocument> documents = new ArrayList<>();
            if (StringUtils.equals(protocol, "jsonrpc")) {
                documents.add(document("python", "jsonrpc.py", url, sample, "Python 3.9+，使用标准库。"));
                documents.add(document("javascript", "jsonrpc.mjs", url, sample, "Node.js 22+，使用内置 fetch。"));
            } else if (StringUtils.equals(protocol, "xmlrpc")) {
                documents.add(document("python", "xmlrpc.py", url, sample, "Python 3.9+，使用标准库。"));
            } else {
                documents.add(document("javascript", "hprose.mjs", url, sample, "Node.js 22+；依赖 `hprose@2.0.51`。"));
            }
            return documents;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("无法生成调用参数", failure);
        }
    }

    public static Map<String, Object> call(MetServiceDescription description, MetProtocolDescription binding,
            Map<String, Object> operation, String protocol) {
        MetMethodDescription method = description.getMethods().stream()
                .filter(item -> ObjectUtils.equals(item.getJavaSignature(), operation.get("javaSignature")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("线上方法缺少对应接口声明"));
        String name = Objects.toString(operation.get("name"), "");
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("线上方法名为空");
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

    private static ExampleDocument document(String language, String template, String url, String sample, String dependency) throws java.io.IOException {
        // Python 通过 JSON 字符串读取，避免 JSON 的 true/null 被误当作 Python 标识符。
        String arguments = sample;
        if (StringUtils.equals(language, "python")) {
            arguments = JsonUtils.writeValueAsString(sample);
        }
        String code = ExampleTemplates.render("http/" + template, Map.of("url", url, "call", arguments));
        String markdown = ExampleTemplates.render("http/document.md.tpl", Map.of("dependency", dependency,
                "code", ExampleTemplates.codeBlock(language, code)));
        return new ExampleDocument(language + "-direct", language, "direct", markdown);
    }

    private static String httpAddress(ExampleTarget target, String providedAddress) {
        if (StringUtils.isBlank(providedAddress)) {
            return "请填写服务地址";
        }
        URI address = URI.create(providedAddress);
        String scheme = StringUtils.equalsIgnoreCase(address.getScheme(), "https") ? "https" : "http";
        String path = target.getEndpoint().getContextPath();
        if (StringUtils.isBlank(path)) {
            path = "/";
        }
        URI context = URI.create(path);
        if (!path.startsWith("/") || !StringUtils.equals(context.getRawPath(), path)) {
            throw new IllegalArgumentException("服务 HTTP 挂载路径格式不正确");
        }
        try {
            URI authority = new URI(scheme, null, address.getHost(), address.getPort(), null, null, null);
            // context 是经过验证的原始路径，不能由多参数 URI 构造器再次编码其中的百分号。
            return URI.create(authority.toASCIIString() + path).toASCIIString();
        } catch (java.net.URISyntaxException failure) {
            throw new IllegalArgumentException("服务 HTTP 地址格式不正确", failure);
        }
    }

    public static Object sample(MetServiceDescription service, String reference, String protocol, Set<String> visiting) {
        MetTypeDescription type = service.getTypes().get(reference);
        if (type == null) {
            throw new IllegalArgumentException("缺少参数类型声明：" + reference);
        }
        String java = type.getJavaType();
        if (StringUtils.equals(java, "byte[]")) {
            return StringUtils.equals(protocol, "jsonrpc") ? "" : Map.of("$binary", "");
        }
        if (!visiting.add(reference)) {
            throw new IllegalArgumentException("递归参数需要业务数据，无法生成完整默认参数：" + reference);
        }
        try {
            return switch (type.getKind()) {
                case "array", "collection" -> List.of();
                case "map" -> Map.of();
                case "enum" -> {
                    if (type.getValues().isEmpty()) {
                        throw new IllegalArgumentException("枚举没有值：" + reference);
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
                default -> throw new IllegalArgumentException("此参数类型不能自动生成原生语言样例：" + reference);
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
            case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
                 "java.math.BigInteger" -> 1;
            case "float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" -> 1.0;
            default -> throw new IllegalArgumentException("此标量类型需要业务数据：" + java);
        };
    }
}
