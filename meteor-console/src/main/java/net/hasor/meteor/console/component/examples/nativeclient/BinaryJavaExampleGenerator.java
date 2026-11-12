/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.utils.JsonUtils;

/** 原生 Java 二进制 SDK；请求字段和结果解码均依据当前提供者的线上类型图。 */
public final class BinaryJavaExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (!Set.of("thrift", "thrift-http", "oncrpc", "oncrpc-udp").contains(protocol)) {
            return List.of();
        }
        ExampleConnections.validate(target);
        var binding = target.getEndpoint().getDescription();
        JsonNode types = JsonUtils.valueToTree(binding.getTypes());
        JsonNode method = JsonUtils.valueToTree(binding.getMethods()).get(0);
        URI uri = URI.create(target.getAddress());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("host", literal(StringUtils.isBlank(address) ? "请填写服务地址" : uri.getHost()));
        values.put("port", Integer.toString(uri.getPort()));
        String template;
        String dependency;
        StringBuilder write = new StringBuilder();
        if (protocol.startsWith("thrift")) {
            String transport = "new TSocket(" + values.get("host") + ", " + uri.getPort() + ", 5000)";
            if (StringUtils.equals(protocol, "thrift-http")) {
                transport = "new THttpClient(" + literal(HttpRequestExampleGenerator.endpoint(target, address, "http")) + ")";
            } else if (Boolean.TRUE.equals(binding.getExtensions().get("framed"))) {
                transport = "new TFramedTransport(" + transport + ")";
            }
            values.put("transport", transport);
            String serializer = StringUtils.defaultIfBlank(binding.getSerializeType(), "Binary");
            values.put("protocol", switch (serializer.toLowerCase(Locale.ROOT)) {
                case "compact" -> "TCompactProtocol";
                case "json" -> "TJSONProtocol";
                default -> "TBinaryProtocol";
            });
            String name = method.path("name").asText();
            if (Boolean.TRUE.equals(binding.getExtensions().get("multiplexed"))) {
                name = binding.getExtensions().get("multiplexedName") + ":" + name;
            }
            values.put("method", literal(name));
            for (JsonNode parameter : method.path("parameters")) {
                this.thriftField(types, parameter, write, new HashSet<>());
            }
            dependency = "`org.apache.thrift:libthrift:0.25.0`";
            template = "thrift";
        } else {
            this.xdrWrite(types, method.path("requestType").asText(), write, new HashSet<>());
            Map<String, String> readers = new LinkedHashMap<>();
            types.fieldNames().forEachRemaining(name -> readers.put(name, "read" + readers.size()));
            StringBuilder methods = new StringBuilder();
            types.fieldNames().forEachRemaining(name -> methods.append(this.xdrReader(types, name, readers)));
            values.put("client", StringUtils.equals(protocol, "oncrpc-udp") ? "OncRpcUdpClient" : "OncRpcTcpClient");
            values.put("program", "(int) " + binding.getExtensions().get("program") + "L");
            values.put("version", "(int) " + binding.getExtensions().get("version") + "L");
            values.put("procedure", method.path("procedureId").asText());
            values.put("read", readers.get(method.path("responseType").asText()) + "(stream)");
            values.put("readers", methods.toString());
            dependency = "`org.acplt.remotetea:remotetea-oncrpc:1.1.6`";
            template = "oncrpc";
        }
        values.put("write", write.toString().stripTrailing());
        String code = ExampleTemplates.render("binary-java/" + template + ".java.tpl", values);
        return List.of(new ExampleDocument("java-sdk-direct", "java-sdk", "direct", "依赖：" + dependency + "。请求中的编号、名称和字段来自当前服务契约；结果按线上结构输出。\n\n" + ExampleTemplates.codeBlock("java", code)));
    }

    private void thriftField(JsonNode types, JsonNode field, StringBuilder code, Set<String> parents) {
        String reference = field.path("type").asText();
        JsonNode type = BinaryContracts.required(types, reference);
        if (parents.contains(reference) && !StringUtils.equals(field.path("requirement").asText(), "required")) {
            return;
        }
        code.append("            protocol.writeFieldBegin(new TField(").append(literal(field.path("name").asText())).append(", (byte) ").append(type.path("wireType").asInt()).append(", (short) ").append(field.path("fieldId").asInt()).append("));\n");
        this.thriftWrite(types, reference, code, parents);
        code.append("            protocol.writeFieldEnd();\n");
    }

    private void thriftWrite(JsonNode types, String reference, StringBuilder code, Set<String> parents) {
        JsonNode type = BinaryContracts.required(types, reference);
        int wire = type.path("wireType").asInt();
        if (!parents.add(reference)) {
            throw new IllegalArgumentException("Thrift 必填递归参数无法构造有限样本");
        }
        switch (wire) {
            case 2 -> code.append("            protocol.writeBool(false);\n");
            case 3 -> code.append("            protocol.writeByte((byte) 1);\n");
            case 4 -> code.append("            protocol.writeDouble(1.0);\n");
            case 6 -> code.append("            protocol.writeI16((short) 1);\n");
            case 8 -> {
                int value = 1;
                if (!type.path("values").isEmpty()) {
                    value = type.path("values").get(0).path("fieldId").asInt();
                }
                code.append("            protocol.writeI32(").append(value).append(");\n");
            }
            case 10 -> code.append("            protocol.writeI64(1L);\n");
            case 11 -> code.append("            protocol.writeString(\"example\");\n");
            case 12 -> {
                code.append("            protocol.writeStructBegin(new TStruct(").append(literal(type.path("name").asText())).append("));\n");
                for (JsonNode field : type.path("fields")) {
                    this.thriftField(types, field, code, new HashSet<>(parents));
                    if (type.path("union").asBoolean()) {
                        break;
                    }
                }
                code.append("            protocol.writeFieldStop();\n            protocol.writeStructEnd();\n");
            }
            case 13 -> code.append("            protocol.writeMapBegin(new TMap((byte) ").append(BinaryContracts.required(types, type.path("keyType").asText()).path("wireType").asInt()).append(", (byte) ").append(BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt()).append(", 0));\n            protocol.writeMapEnd();\n");
            case 14 -> code.append("            protocol.writeSetBegin(new TSet((byte) ").append(BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt()).append(", 0));\n            protocol.writeSetEnd();\n");
            case 15 -> code.append("            protocol.writeListBegin(new TList((byte) ").append(BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt()).append(", 0));\n            protocol.writeListEnd();\n");
            case 16 -> code.append("            protocol.writeUuid(new UUID(0, 1));\n");
            default -> throw new IllegalArgumentException("未识别 Thrift 类型 " + wire);
        }
    }

    private void xdrWrite(JsonNode types, String reference, StringBuilder code, Set<String> parents) {
        JsonNode type = BinaryContracts.required(types, reference);
        String kind = type.path("kind").asText();
        if (!parents.add(reference)) {
            throw new IllegalArgumentException("XDR 必填递归参数无法构造有限样本");
        }
        String value = switch (kind) {
            case "string" -> "stream.xdrEncodeString(\"example\");";
            case "bool", "boolean" -> "stream.xdrEncodeBoolean(false);";
            case "hyper", "unsignedHyper" -> "stream.xdrEncodeLong(1L);";
            case "float" -> "stream.xdrEncodeFloat(1F);";
            case "double" -> "stream.xdrEncodeDouble(1D);";
            case "int", "unsignedInt", "enum" -> "stream.xdrEncodeInt(1);";
            case "void" -> "";
            default -> null;
        };
        if (StringUtils.equals(kind, "enum") && !type.path("values").isEmpty()) {
            value = "stream.xdrEncodeInt(" + type.path("values").get(0).path("fieldId").asInt() + ");";
        }
        if (value != null) {
            code.append("                    ").append(value).append('\n');
        } else if (StringUtils.equals(kind, "struct")) {
            for (JsonNode member : type.path("members")) {
                this.xdrWrite(types, member.path("type").asText(), code, new HashSet<>(parents));
            }
        } else if (StringUtils.equals(kind, "optional")) {
            code.append("                    stream.xdrEncodeBoolean(false);\n");
        } else if (StringUtils.equals(kind, "opaque")) {
            int length = type.path("length").asInt();
            if (type.path("fixed").asBoolean()) {
                code.append("                    stream.xdrEncodeOpaque(new byte[").append(length).append("]);\n");
            } else {
                code.append("                    stream.xdrEncodeDynamicOpaque(new byte[0]);\n");
            }
        } else if (StringUtils.equals(kind, "array")) {
            if (type.path("fixed").asBoolean()) {
                int count = type.path("length").asInt();
                if (count > 1024) {
                    throw new IllegalArgumentException("固定数组样本过大");
                }
                for (int index = 0; index < count; index++) {
                    this.xdrWrite(types, type.path("elementType").asText(), code, new HashSet<>(parents));
                }
            } else {
                code.append("                    stream.xdrEncodeInt(0);\n");
            }
        } else {
            throw new IllegalArgumentException("该 XDR 类型需要显式业务样本：" + kind);
        }
    }

    private String xdrReader(JsonNode types, String reference, Map<String, String> readers) {
        JsonNode type = BinaryContracts.required(types, reference);
        String kind = type.path("kind").asText();
        String expression = switch (kind) {
            case "string" -> "stream.xdrDecodeString()";
            case "bool", "boolean" -> "stream.xdrDecodeBoolean()";
            case "hyper", "unsignedHyper" -> "stream.xdrDecodeLong()";
            case "float" -> "stream.xdrDecodeFloat()";
            case "double" -> "stream.xdrDecodeDouble()";
            case "int", "unsignedInt", "enum" -> "stream.xdrDecodeInt()";
            case "void" -> "null";
            default -> null;
        };
        StringBuilder code = new StringBuilder("    private static Object ").append(readers.get(reference)).append("(XdrDecodingStream stream) throws OncRpcException, IOException {\n");
        if (expression != null) {
            code.append("        return ").append(expression).append(";\n");
        } else if (StringUtils.equals(kind, "struct")) {
            code.append("        Map<String, Object> result = new LinkedHashMap<>();\n");
            for (JsonNode member : type.path("members")) {
                code.append("        result.put(").append(literal(member.path("name").asText())).append(", ").append(readers.get(member.path("type").asText())).append("(stream));\n");
            }
            code.append("        return result;\n");
        } else if (StringUtils.equals(kind, "optional")) {
            code.append("        if (!stream.xdrDecodeBoolean()) return null;\n        return ").append(readers.get(type.path("elementType").asText())).append("(stream);\n");
        } else if (StringUtils.equals(kind, "array")) {
            String count = "stream.xdrDecodeInt()";
            if (type.path("fixed").asBoolean()) {
                count = type.path("length").asText();
            }
            code.append("        int count = ").append(count).append(";\n        List<Object> result = new ArrayList<>();\n").append("        for (int index = 0; index < count; index++) result.add(").append(readers.get(type.path("elementType").asText())).append("(stream));\n        return result;\n");
        } else if (StringUtils.equals(kind, "opaque")) {
            String read = "stream.xdrDecodeDynamicOpaque()";
            if (type.path("fixed").asBoolean()) {
                read = "stream.xdrDecodeOpaque(" + type.path("length").asInt() + ")";
            }
            code.append("        return Base64.getEncoder().encodeToString(").append(read).append(");\n");
        } else {
            throw new IllegalArgumentException("该 XDR 类型没有通用示例解码器：" + kind);
        }
        return code.append("    }\n").toString();
    }

    private static String literal(String value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalArgumentException("无法编码 Java 示例", failure);
        }
    }
}
