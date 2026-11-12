/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.caucho.hessian.io.Hessian2Output;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.utils.JsonUtils;

/** 为没有通用动态 SDK 的语言提供一次完整线上报文调用；不会伪造业务类型或依赖生成工程。 */
public final class WireExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (!Set.of("thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "hessian").contains(protocol)) {
            return List.of();
        }
        ExampleConnections.validate(target);
        var binding = target.getEndpoint().getDescription();
        JsonNode method = JsonUtils.valueToTree(binding.getMethods()).get(0);
        String operation = method.path("name").asText();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            if (protocol.startsWith("thrift")) {
                // 示例只生成 TBinaryProtocol；Compact/JSON 由原生 Java SDK 文档处理。
                String coding = binding.getSerializeType();
                if (StringUtils.equalsIgnoreCase(coding, "compact") || StringUtils.equalsIgnoreCase(coding, "json")) {
                    return List.of();
                }
                String name = operation;
                if (Boolean.TRUE.equals(binding.getExtensions().get("multiplexed"))) {
                    name = binding.getExtensions().get("multiplexedName") + ":" + name;
                }
                output.writeInt(0x80010001);
                this.text(output, name);
                output.writeInt(1);
                JsonNode types = JsonUtils.valueToTree(binding.getTypes());
                for (JsonNode field : method.path("parameters")) {
                    this.thriftField(types, field, output, new HashSet<>());
                }
                output.writeByte(0);
                if (StringUtils.equals(protocol, "thrift-http")) {
                    return http(HttpRequestExampleGenerator.endpoint(target, address, "http"), bytes.toByteArray(), Map.of("Content-Type", "application/x-thrift"), operation, List.of("python", "javascript", "go", "c", "cpp", "browser", "shell"));
                }
                if (!Boolean.TRUE.equals(binding.getExtensions().get("framed"))) {
                    return List.of(); // 无帧传输需使用能按类型流读取的 SDK。
                }
                return this.socket(target, address, frame(bytes.toByteArray(), false), operation);
            }
            if (protocol.startsWith("oncrpc")) {
                // 固定事务号只用于本次独占连接；不经 rpcbind，直接访问发布端口。
                output.writeInt(1);
                output.writeInt(0);
                output.writeInt(2);
                output.writeInt(((Number) binding.getExtensions().get("program")).intValue());
                output.writeInt(((Number) binding.getExtensions().get("version")).intValue());
                output.writeInt(method.path("procedureId").asInt());
                for (int index = 0; index < 4; index++) {
                    output.writeInt(0); // AUTH_NONE credentials and verifier
                }
                this.xdr(JsonUtils.valueToTree(binding.getTypes()), method.path("requestType").asText(), output, new HashSet<>());
                byte[] body = bytes.toByteArray();
                if (!StringUtils.equals(protocol, "oncrpc-udp")) {
                    body = frame(body, true);
                }
                return this.socket(target, address, body, operation);
            }
            Map<String, Object> selected = binding.getMethods().get(0);
            Map<String, Object> call = HttpExampleGenerator.call(target.getDescription(), binding, selected, protocol);
            List<?> arguments = (List<?>) call.get("args");
            if (StringUtils.equals(protocol, "hprose")) {
                output.writeByte('C');
                this.hprose(JsonUtils.valueToTree(call.get("method")), output);
                this.hprose(JsonUtils.valueToTree(arguments), output);
                output.writeByte('z');
            } else {
                Hessian2Output hessian = new Hessian2Output(bytes);
                hessian.call(operation, arguments.toArray());
                hessian.flush();
            }
            String mediaType = "application/hprose";
            if (StringUtils.equals(protocol, "hessian")) {
                mediaType = "application/x-hessian";
            }
            return http(HttpRequestExampleGenerator.endpoint(target, address, "http"), bytes.toByteArray(), Map.of("Content-Type", mediaType), operation, List.of("python", "javascript", "go", "cpp", "browser", "shell"));
        } catch (IOException failure) {
            throw new IllegalArgumentException("无法构造原生请求样本", failure);
        }
    }

    static byte[] frame(byte[] body, boolean last) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(body.length | (last ? 0x80000000 : 0));
        output.write(body);
        return bytes.toByteArray();
    }

    static List<ExampleDocument> http(String url, byte[] body, Map<String, String> headers, String operation, List<String> languages) {
        Map<String, String> values = values(body, operation);
        values.put("url", literal(url));
        values.put("headers", literal(headers));
        StringBuilder curl = new StringBuilder();
        StringBuilder shell = new StringBuilder();
        headers.forEach((name, value) -> {
            curl.append("    headers = curl_slist_append(headers, ").append(literal(name + ": " + value)).append(");\n");
            shell.append("  --header ").append(quote(name + ": " + value)).append(" \\\n");
        });
        values.put("curlHeaders", curl.toString().stripTrailing());
        values.put("shellHeaders", shell.toString().stripTrailing());
        values.put("shellUrl", quote(url));
        return documents("http", values, languages);
    }

    private List<ExampleDocument> socket(ExampleTarget target, String address, byte[] body, String operation) {
        URI uri = URI.create(target.getAddress());
        Map<String, String> values = values(body, operation);
        values.put("host", literal(StringUtils.isBlank(address) ? "请填写服务地址" : uri.getHost()));
        values.put("port", Integer.toString(uri.getPort()));
        boolean udp = StringUtils.equals(target.getEndpoint().getTransport(), "udp");
        values.put("udp", Boolean.toString(udp));
        values.put("thrift", Boolean.toString(StringUtils.equals(target.getEndpoint().getProtocolName(), "thrift")));
        values.put("network", udp ? "udp" : "tcp");
        values.put("socketKind", udp ? "SOCK_DGRAM" : "SOCK_STREAM");
        return documents("socket", values, List.of("python", "javascript", "go", "c", "cpp"));
    }

    private static Map<String, String> values(byte[] body, String operation) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("body", Base64.getEncoder().encodeToString(body));
        List<String> bytes = new ArrayList<>();
        for (byte value : body) {
            bytes.add(Integer.toString(value & 255));
        }
        values.put("bytes", String.join(", ", bytes));
        values.put("operation", operation);
        return values;
    }

    private static List<ExampleDocument> documents(String transport, Map<String, String> values, List<String> languages) {
        List<ExampleDocument> result = new ArrayList<>();
        for (String language : languages) {
            String extension = switch (language) {
                case "python" -> "py";
                case "go" -> "go";
                case "c", "cpp" -> "c";
                case "shell" -> "sh";
                default -> "mjs";
            };
            Map<String, String> context = new LinkedHashMap<>(values);
            if (StringUtils.equals(language, "python")) {
                context.computeIfPresent("udp", (key, value) -> StringUtils.equals(value, "true") ? "True" : "False");
                context.computeIfPresent("thrift", (key, value) -> {
                    if (StringUtils.equals(value, "true")) {
                        return "True";
                    }
                    return "False";
                });
            }
            String syntax = language;
            if (StringUtils.equals(language, "browser")) {
                syntax = "javascript";
            } else if (StringUtils.equals(language, "shell")) {
                syntax = "bash";
            }
            String dependency = "使用标准网络 API";
            if (StringUtils.equals(transport, "http") && List.of("c", "cpp").contains(language)) {
                dependency = "依赖 libcurl（启用 HTTP/2）";
            } else if (StringUtils.equals(language, "shell")) {
                dependency = "依赖支持 HTTP/2 的 curl、base64、od";
            }
            String code = ExampleTemplates.render("wire/" + transport + "." + extension + ".tpl", context);
            String markdown = dependency + "。以下是当前方法的最小报文调用，内嵌请求已按服务契约编码，响应以十六进制展示。" + " 修改业务数据应使用该协议的类型绑定重新编码。\n\n" + ExampleTemplates.codeBlock(syntax, code);
            result.add(new ExampleDocument(language + "-direct", language, "direct", markdown));
        }
        return result;
    }

    private void thriftField(JsonNode types, JsonNode field, DataOutputStream output, Set<String> parents) throws IOException {
        String reference = field.path("type").asText();
        JsonNode type = BinaryContracts.required(types, reference);
        if (parents.contains(reference) && !StringUtils.equals(field.path("requirement").asText(), "required")) {
            return;
        }
        output.writeByte(type.path("wireType").asInt());
        output.writeShort(field.path("fieldId").asInt());
        this.thrift(types, reference, output, parents);
    }

    private void thrift(JsonNode types, String reference, DataOutputStream output, Set<String> parents) throws IOException {
        if (!parents.add(reference)) {
            throw new IllegalArgumentException("Thrift 必填递归参数需要业务样本");
        }
        JsonNode type = BinaryContracts.required(types, reference);
        switch (type.path("wireType").asInt()) {
            case 2, 3 -> output.writeByte(0);
            case 4 -> output.writeDouble(1);
            case 6 -> output.writeShort(1);
            case 8 -> {
                int value = 1;
                if (!type.path("values").isEmpty()) {
                    value = type.path("values").get(0).path("fieldId").asInt();
                }
                output.writeInt(value);
            }
            case 10 -> output.writeLong(1);
            case 11 -> this.text(output, "example");
            case 12 -> {
                for (JsonNode field : type.path("fields")) {
                    this.thriftField(types, field, output, new HashSet<>(parents));
                    if (type.path("union").asBoolean()) {
                        break;
                    }
                }
                output.writeByte(0);
            }
            case 13 -> {
                output.writeByte(BinaryContracts.required(types, type.path("keyType").asText()).path("wireType").asInt());
                output.writeByte(BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt());
                output.writeInt(0);
            }
            case 14, 15 -> {
                output.writeByte(BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt());
                output.writeInt(0);
            }
            case 16 -> {
                output.writeLong(0);
                output.writeLong(1);
            }
            default -> throw new IllegalArgumentException("不支持的 Thrift 样本类型");
        }
    }

    private void xdr(JsonNode types, String reference, DataOutputStream output, Set<String> parents) throws IOException {
        if (!parents.add(reference)) {
            throw new IllegalArgumentException("XDR 必填递归参数需要业务样本");
        }
        JsonNode type = BinaryContracts.required(types, reference);
        switch (type.path("kind").asText()) {
            case "int", "unsignedInt" -> output.writeInt(1);
            case "enum" -> output.writeInt(type.path("values").get(0).path("fieldId").asInt());
            case "bool", "boolean", "optional" -> output.writeInt(0);
            case "hyper", "unsignedHyper" -> output.writeLong(1);
            case "float" -> output.writeFloat(1);
            case "double" -> output.writeDouble(1);
            case "void" -> {
            }
            case "string" -> {
                byte[] text = "example".getBytes(StandardCharsets.UTF_8);
                output.writeInt(text.length);
                output.write(text);
                output.write(new byte[(4 - text.length % 4) % 4]);
            }
            case "struct" -> {
                for (JsonNode member : type.path("members")) {
                    this.xdr(types, member.path("type").asText(), output, new HashSet<>(parents));
                }
            }
            case "array" -> {
                if (type.path("fixed").asBoolean()) {
                    int length = type.path("length").asInt();
                    if (length > 1024) {
                        throw new IllegalArgumentException("XDR 固定数组样本过大");
                    }
                    for (int index = 0; index < length; index++) {
                        this.xdr(types, type.path("elementType").asText(), output, new HashSet<>(parents));
                    }
                } else {
                    output.writeInt(0);
                }
            }
            case "opaque" -> {
                int length = 0;
                if (type.path("fixed").asBoolean()) {
                    length = type.path("length").asInt();
                    if (length > 65536 || length < 0) {
                        throw new IllegalArgumentException("XDR 固定 opaque 样本过大");
                    }
                } else {
                    output.writeInt(0);
                }
                output.write(new byte[length + (4 - length % 4) % 4]);
            }
            default -> throw new IllegalArgumentException("当前 XDR 类型需要显式业务样本");
        }
    }

    private void hprose(JsonNode value, DataOutputStream output) throws IOException {
        if (value.isTextual()) {
            String text = value.asText();
            output.writeByte('s');
            output.writeBytes(Integer.toString(text.length()));
            output.writeByte('"');
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.writeByte('"');
        } else if (value.isArray() || value.isObject()) {
            output.writeByte(value.isArray() ? 'a' : 'm');
            output.writeBytes(Integer.toString(value.size()));
            output.writeByte('{');
            if (value.isArray()) {
                for (JsonNode item : value) {
                    this.hprose(item, output);
                }
            } else {
                var fields = value.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    this.hprose(JsonUtils.valueToTree(field.getKey()), output);
                    this.hprose(field.getValue(), output);
                }
            }
            output.writeByte('}');
        } else if (value.isBoolean()) {
            output.writeByte(value.asBoolean() ? 't' : 'f');
        } else if (value.isNumber()) {
            output.writeByte(value.isIntegralNumber() ? 'l' : 'd');
            output.writeBytes(value.asText());
            output.writeByte(';');
        } else {
            output.writeByte('n');
        }
    }

    private void text(DataOutputStream output, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }

    private static String literal(Object value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalArgumentException("无法编码调用文档", failure);
        }
    }
}
