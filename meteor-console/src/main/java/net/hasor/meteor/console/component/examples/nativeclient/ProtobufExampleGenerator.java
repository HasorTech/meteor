/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.utils.JsonUtils;

/** 从已发布的 Protobuf 类型图生成原生 SDK 文档，编号和线上名称均取自提供者。 */
public final class ProtobufExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String providedAddress) {
        String protocol = target.getEndpoint().getProtocolName();
        if (!Set.of("grpc", "grpc-web", "connect", "triple").contains(protocol)) {
            return List.of();
        }
        ExampleConnections.validate(target);
        var binding = target.getEndpoint().getDescription();
        JsonNode types = JsonUtils.valueToTree(binding.getTypes());
        JsonNode methods = JsonUtils.valueToTree(binding.getMethods());
        JsonNode method = methods.get(0);
        if (method == null || !StringUtils.equals(types.path(method.path("requestType").asText()).path("kind").asText(), "message")) {
            return List.of(); // Triple 的 POJO/Hessian 模式由独立 SDK 和普通 HTTP 示例处理。
        }
        if (method.path("clientStreaming").asBoolean() || method.path("serverStreaming").asBoolean()) {
            throw new IllegalArgumentException("当前原生 Protobuf 示例仅选择一元方法");
        }
        try {
            Map<String, FileDescriptorProto.Builder> files = this.files(types);
            Map<String, Descriptors.FileDescriptor> linked = new LinkedHashMap<>();
            for (String name : files.keySet()) {
                this.link(name, files, linked, new HashSet<>());
            }
            Map<String, Descriptors.Descriptor> messages = new LinkedHashMap<>();
            FileDescriptorSet.Builder descriptors = FileDescriptorSet.newBuilder();
            for (var file : linked.values()) {
                descriptors.addFile(file.toProto());
                for (var message : file.getMessageTypes()) {
                    messages.put(message.getFullName(), message);
                }
            }
            String requestType = method.path("requestType").asText();
            DynamicMessage request = this.sample(messages.get(requestType), new HashSet<>());
            Map<String, String> values = new LinkedHashMap<>();
            values.put("descriptors", Base64.getEncoder().encodeToString(descriptors.build().toByteArray()));
            values.put("requestType", requestType);
            values.put("responseType", method.path("responseType").asText());
            values.put("requestJson", JsonUtils.writeValueAsString(JsonFormat.printer().print(request)));
            values.put("requestBase64", Base64.getEncoder().encodeToString(request.toByteArray()));
            values.put("requestBytes", this.bytes(request.toByteArray()));
            String path = target.getEndpoint().getContextPath();
            path = StringUtils.defaultIfBlank(path, "");
            String methodPath = path.replaceAll("/+$", "") + "/" + binding.getName() + "/" + method.path("name").asText();
            URI address = URI.create(target.getAddress());
            values.put("host", StringUtils.isBlank(providedAddress) ? "请填写服务地址" : address.getHost());
            values.put("port", Integer.toString(address.getPort()));
            values.put("methodPath", methodPath);
            values.put("url", JsonUtils.writeValueAsString(StringUtils.isBlank(providedAddress) ? "请填写服务地址" : "http://" + address.getRawAuthority() + methodPath));
            values.put("baseUrl", JsonUtils.writeValueAsString(StringUtils.isBlank(providedAddress) ? "请填写服务地址" : "http://" + address.getRawAuthority() + path));
            values.put("methodName", binding.getName() + "/" + method.path("name").asText());
            values.put("group", JsonUtils.writeValueAsString(target.getService().getGroup()));
            values.put("version", JsonUtils.writeValueAsString(target.getService().getVersion()));
            values.put("network", StringUtils.equals(protocol, "grpc-web") ? "GRPC_WEB" : "CONNECT");
            List<ExampleDocument> result = new ArrayList<>();
            if (Set.of("grpc", "triple").contains(protocol)) {
                result.add(this.document("java-sdk", "java", "protobuf/java-grpc.java.tpl", values, "`io.grpc:grpc-netty-shaded:1.84.1`、`io.grpc:grpc-protobuf:1.84.1`、`io.grpc:grpc-stub:1.84.1`、`com.google.protobuf:protobuf-java-util:4.33.0`"));
                result.add(this.document("python", "python", "protobuf/grpc.py.tpl", values, "`grpcio==1.76.0`、`protobuf==6.33.0`"));
                result.add(this.document("javascript", "javascript", "protobuf/grpc.mjs.tpl", values, "`@grpc/grpc-js@1.14.1`、`protobufjs@7.5.4`"));
                result.add(this.document("go", "go", "protobuf/grpc.go.tpl", values, "`google.golang.org/grpc v1.76.0`、`google.golang.org/protobuf v1.36.10`"));
            } else {
                result.add(this.document("java-sdk", "java", "protobuf/java-connect.java.tpl", values, "`com.connectrpc:connect-kotlin-okhttp:0.5.1`、`com.connectrpc:connect-kotlin-google-java-ext:0.5.1`、`com.google.protobuf:protobuf-java-util:4.33.0`"));
            }
            String url = "请填写服务地址";
            if (StringUtils.isNotBlank(providedAddress)) {
                url = "http://" + address.getRawAuthority() + methodPath;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            if (StringUtils.equals(protocol, "connect")) {
                headers.put("Content-Type", "application/json");
                headers.put("Connect-Protocol-Version", "1");
                result.addAll(HttpRequestExampleGenerator.documents(url, JsonFormat.printer().print(request), headers, List.of("python", "javascript", "go", "cpp", "browser", "shell")));
            } else {
                headers.put("Content-Type", "application/grpc+proto");
                headers.put("TE", "trailers");
                headers.put("tri-service-group", target.getService().getGroup());
                headers.put("tri-service-version", target.getService().getVersion());
                List<String> languages = List.of("c", "cpp", "shell");
                if (StringUtils.equals(protocol, "grpc-web")) {
                    headers.put("Content-Type", "application/grpc-web+proto");
                    headers.put("X-Grpc-Web", "1");
                    languages = List.of("python", "javascript", "go", "browser", "shell");
                }
                byte[] body = new byte[request.getSerializedSize() + 5];
                ByteBuffer.wrap(body).put((byte) 0).putInt(request.getSerializedSize()).put(request.toByteArray());
                result.addAll(WireExampleGenerator.http(url, body, headers, method.path("name").asText(), languages));
            }
            return result;
        } catch (Exception failure) {
            throw new IllegalArgumentException("无法从提供者 Protobuf 契约生成调用文档：" + failure.getMessage(), failure);
        }
    }

    private ExampleDocument document(String language, String syntax, String template, Map<String, String> values, String dependency) {
        String code = ExampleTemplates.render(template, values);
        String markdown = "依赖：" + dependency + "。文档内嵌当前服务的描述数据，不需要另建示例工程。\n\n" + ExampleTemplates.codeBlock(syntax, code);
        return new ExampleDocument(language + "-direct", language, "direct", markdown);
    }

    private Map<String, FileDescriptorProto.Builder> files(JsonNode types) {
        Map<String, FileDescriptorProto.Builder> files = new LinkedHashMap<>();
        types.fieldNames().forEachRemaining(name -> files.computeIfAbsent(namespace(name), key -> {
            String filename = "service.proto";
            if (!key.isEmpty()) {
                filename = key.replace('.', '/') + ".proto";
            }
            return FileDescriptorProto.newBuilder().setName(filename).setPackage(key).setSyntax("proto2");
        }));
        types.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode type = entry.getValue();
            FileDescriptorProto.Builder file = files.get(namespace(name));
            if (StringUtils.equals(type.path("kind").asText(), "enum")) {
                EnumDescriptorProto.Builder enumeration = EnumDescriptorProto.newBuilder().setName(simple(name));
                for (JsonNode constant : type.path("values")) {
                    enumeration.addValue(EnumValueDescriptorProto.newBuilder().setName(constant.path("name").asText()).setNumber(constant.path("fieldId").asInt()));
                }
                file.addEnumType(enumeration);
                return;
            }
            DescriptorProto.Builder message = DescriptorProto.newBuilder().setName(simple(name));
            for (JsonNode member : type.path("fields")) {
                String reference = member.path("type").asText();
                FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder().setName(member.path("name").asText()).setNumber(member.path("fieldId").asInt()).setJsonName(member.path("jsonName").asText());
                if (member.path("repeated").asBoolean()) {
                    field.setLabel(FieldDescriptorProto.Label.LABEL_REPEATED);
                } else if (member.path("required").asBoolean()) {
                    field.setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED);
                } else {
                    field.setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
                }
                if (types.has(reference)) {
                    field.setType(StringUtils.equals(types.path(reference).path("kind").asText(), "enum") ? FieldDescriptorProto.Type.TYPE_ENUM : FieldDescriptorProto.Type.TYPE_MESSAGE);
                    field.setTypeName("." + reference);
                    String dependency = namespace(reference);
                    if (!StringUtils.equals(namespace(name), dependency)) {
                        String dependencyName = files.get(dependency).getName();
                        if (!file.getDependencyList().contains(dependencyName)) {
                            file.addDependency(dependencyName);
                        }
                    }
                } else {
                    field.setType(FieldDescriptorProto.Type.valueOf("TYPE_" + reference));
                }
                message.addField(field);
            }
            file.addMessageType(message);
        });
        return files;
    }

    private Descriptors.FileDescriptor link(String namespace, Map<String, FileDescriptorProto.Builder> files, Map<String, Descriptors.FileDescriptor> linked, Set<String> visiting) throws Descriptors.DescriptorValidationException {
        if (linked.containsKey(namespace)) {
            return linked.get(namespace);
        }
        if (!visiting.add(namespace)) {
            throw new IllegalArgumentException("描述图包含跨命名空间循环引用");
        }
        FileDescriptorProto file = files.get(namespace).build();
        List<Descriptors.FileDescriptor> dependencies = new ArrayList<>();
        for (String dependency : file.getDependencyList()) {
            String key = files.entrySet().stream().filter(entry -> StringUtils.equals(entry.getValue().getName(), dependency)).map(Map.Entry::getKey).findFirst().orElseThrow();
            dependencies.add(this.link(key, files, linked, visiting));
        }
        var result = Descriptors.FileDescriptor.buildFrom(file, dependencies.toArray(Descriptors.FileDescriptor[]::new));
        visiting.remove(namespace);
        linked.put(namespace, result);
        return result;
    }

    private DynamicMessage sample(Descriptors.Descriptor type, Set<String> parents) {
        DynamicMessage.Builder message = DynamicMessage.newBuilder(type);
        parents.add(type.getFullName());
        for (var field : type.getFields()) {
            if (field.isRepeated()) {
                continue;
            }
            Object value = switch (field.getJavaType()) {
                case STRING -> "example";
                case INT -> 1;
                case LONG -> 1L;
                case FLOAT -> 1F;
                case DOUBLE -> 1D;
                case BOOLEAN -> false;
                case BYTE_STRING -> ByteString.EMPTY;
                case ENUM -> field.getEnumType().getValues().get(0);
                case MESSAGE -> parents.contains(field.getMessageType().getFullName()) ? null : this.sample(field.getMessageType(), new HashSet<>(parents));
            };
            if (value != null) {
                message.setField(field, value);
            }
        }
        return message.build();
    }

    private String bytes(byte[] bytes) {
        List<String> result = new ArrayList<>();
        for (byte value : bytes) {
            result.add(Integer.toString(value & 255));
        }
        return String.join(", ", result);
    }

    private static String namespace(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(0, separator);
    }

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
