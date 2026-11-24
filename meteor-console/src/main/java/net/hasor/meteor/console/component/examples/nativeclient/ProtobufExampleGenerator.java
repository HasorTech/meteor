/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;

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
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_NATIVE_PROTOBUF_EXAMPLES_CURRENTLY_SUPPORT_UNARY);
        }

        try {
            boolean protoSource = Set.of("grpc", "grpc-web").contains(protocol);
            String requestType = method.path("requestType").asText();
            String methodName = method.path("name").asText();
            DynamicMessage request;
            String schemaMarkdown;
            if (protoSource) {
                if (!(binding.getExtensions().get("protobufDescriptors") instanceof String definition)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_EXAMPLE_MISSING_ENDPOINT_CONTRACT, binding.getName(), protocol);
                }
                var schema = GrpcProtoSchema.fromDescriptorSet(Base64.getDecoder().decode(definition));
                var rpc = schema.method(binding.getName(), methodName);
                schemaMarkdown = new GrpcExampleContract(rpc.getService(), List.of(rpc)).markdown();
                request = this.sample(rpc.getInputType(), new HashSet<>());
            } else {
                ObjectNode methodTypes = JsonNodeFactory.instance.objectNode();
                this.addType(types, methodTypes, requestType);
                this.addType(types, methodTypes, method.path("responseType").asText());
                Map<String, FileDescriptorProto.Builder> files = this.files(methodTypes);
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
                request = this.sample(messages.get(requestType), new HashSet<>());
                schemaMarkdown = ExampleTemplates.codeBlock("json service.json", JsonFormat.printer().print(descriptors.build()));
            }
            JsonFormat.Printer json = JsonFormat.printer();
            if (protoSource) {
                // protobuf.js loads the field names from the descriptor as declared in the .proto.
                json = json.preservingProtoFieldNames();
            }
            String requestMarkdown = ExampleTemplates.codeBlock("json request.json", json.print(request));
            String filesMarkdown = schemaMarkdown + "\n\n" + requestMarkdown;
            if (protoSource) {
                filesMarkdown += "\n\n" + ExampleTemplates.codeBlock("bash", "protoc -I . --include_imports --descriptor_set_out=service.pb service.proto");
            }
            Map<String, String> values = new LinkedHashMap<>();
            descriptorReaders(values, protoSource);
            values.put("requestType", requestType);
            values.put("responseType", method.path("responseType").asText());
            String path = target.getEndpoint().getContextPath();
            path = StringUtils.defaultIfBlank(path, "");
            String methodPath = path.replaceAll("/+$", "") + "/" + binding.getName() + "/" + method.path("name").asText();
            URI address = URI.create(target.getAddress());
            values.put("host", StringUtils.isBlank(providedAddress) ? ExampleConnections.ADDRESS_PLACEHOLDER : address.getHost());
            values.put("port", Integer.toString(address.getPort()));
            values.put("methodPath", methodPath);
            values.put("url", JsonUtils.writeValueAsString(StringUtils.isBlank(providedAddress) ? ExampleConnections.ADDRESS_PLACEHOLDER : "http://" + address.getRawAuthority() + methodPath));
            values.put("baseUrl", JsonUtils.writeValueAsString(StringUtils.isBlank(providedAddress) ? ExampleConnections.ADDRESS_PLACEHOLDER : "http://" + address.getRawAuthority() + path));
            values.put("methodName", binding.getName() + "/" + method.path("name").asText());
            boolean triple = StringUtils.equals(protocol, "triple");
            String group = JsonUtils.writeValueAsString(target.getService().getGroup());
            String version = JsonUtils.writeValueAsString(target.getService().getVersion());
            values.put("pythonMetadata", triple ? ", metadata=((\"tri-service-group\", " + group + "), (\"tri-service-version\", " + version + "))" : "");
            values.put("goMetadataImport", triple ? "    \"google.golang.org/grpc/metadata\"\n" : "");
            values.put("goMetadata", triple ? "    ctx = metadata.AppendToOutgoingContext(ctx, \"tri-service-group\", " + group + ", \"tri-service-version\", " + version + ")\n" : "");
            values.put("jsMetadata", triple ? "const metadata = new grpc.Metadata();\nmetadata.set('tri-service-group', " + group
                    + ");\nmetadata.set('tri-service-version', " + version + ");\n" : "");
            values.put("jsMetadataArgument", triple ? ", metadata" : "");
            values.put("javaMetadataImports", triple ? "import io.grpc.Metadata;\nimport io.grpc.stub.MetadataUtils;\n" : "");
            values.put("javaMetadata", triple ? "        var headers = new Metadata();\n"
                    + "        headers.put(Metadata.Key.of(\"tri-service-group\", Metadata.ASCII_STRING_MARSHALLER), " + group + ");\n"
                    + "        headers.put(Metadata.Key.of(\"tri-service-version\", Metadata.ASCII_STRING_MARSHALLER), " + version + ");\n" : "");
            values.put("javaInterceptor", triple ? "\n                .intercept(MetadataUtils.newAttachHeadersInterceptor(headers))" : "");
            values.put("network", StringUtils.equals(protocol, "grpc-web") ? "GRPC_WEB" : "CONNECT");
            List<ExampleDocument> result = new ArrayList<>();
            if (Set.of("grpc", "triple").contains(protocol)) {
                result.add(this.document("java-sdk", "java", "protobuf/java-grpc.java.tpl", values, filesMarkdown, ExampleTemplates.gradleDependencies(List.of("io.grpc:grpc-netty-shaded:1.84.1", "io.grpc:grpc-protobuf:1.84.1", "io.grpc:grpc-stub:1.84.1", "com.google.protobuf:protobuf-java-util:4.33.0"))));
                result.add(this.document("python", "python", "protobuf/grpc.py.tpl", values, filesMarkdown, ExampleTemplates.codeBlock("bash", "python -m pip install grpcio==1.76.0 protobuf==6.33.0")));
                result.add(this.document("javascript", "javascript", "protobuf/grpc.mjs.tpl", values, filesMarkdown, ExampleTemplates.codeBlock("bash", "npm install @grpc/grpc-js@1.14.1 protobufjs@7.5.4")));
                result.add(this.document("go", "go", "protobuf/grpc.go.tpl", values, filesMarkdown, ExampleTemplates.codeBlock("bash", "go get google.golang.org/grpc@v1.76.0 google.golang.org/protobuf@v1.36.10")));
            } else {
                String setup = ExampleTemplates.gradleDependencies(List.of("com.connectrpc:connect-kotlin-okhttp:0.5.1",
                        "com.connectrpc:connect-kotlin-google-java-ext:0.5.1", "com.google.protobuf:protobuf-java-util:4.33.0"));
                String codec = ExampleTemplates.render("protobuf/ProtobufCodec.java.tpl", values);
                String support = filesMarkdown + "\n\n" + ExampleTemplates.codeBlock("java ProtobufCodec.java", codec);
                if (StringUtils.equals(protocol, "connect")) {
                    result.add(this.document("java-sdk", "java", "protobuf/java-connect.java.tpl", values, requestMarkdown, setup));
                    result.add(new ExampleDocument("java-sdk-files", "java-sdk", "files", support));
                } else {
                    result.add(this.document("java-sdk", "java", "protobuf/java-connect.java.tpl", values, support, setup));
                }
            }
            String url = ExampleConnections.ADDRESS_PLACEHOLDER;
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
                if (triple) {
                    headers.put("tri-service-group", target.getService().getGroup());
                    headers.put("tri-service-version", target.getService().getVersion());
                }
                List<String> languages = List.of("c", "cpp", "shell");
                if (StringUtils.equals(protocol, "grpc-web")) {
                    headers.put("Content-Type", "application/grpc-web+proto");
                    headers.put("X-Grpc-Web", "1");
                    languages = List.of("python", "javascript", "go", "browser", "shell");
                }
                String setup = ExampleTemplates.codeBlock("bash", "python3 -m pip install protobuf==6.33.0")
                        + "\n\n" + filesMarkdown
                        + "\n\n" + WireExampleGenerator.requestScript(ExampleTemplates.render("protobuf/request.py.tpl", values));
                result.addAll(WireExampleGenerator.http(url, setup, headers, languages));
            }
            return result;
        } catch (ErrorMessageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_GENERATE_AN_INVOCATION_DOCUMENT, failure);
        }
    }

    private static void descriptorReaders(Map<String, String> values, boolean protoSource) {
        if (protoSource) {
            values.put("javaDescriptors", "var definition = FileDescriptorSet.parseFrom(Files.readAllBytes(Path.of(\"service.pb\")));");
            values.put("pythonDescriptors", "descriptor_pb2.FileDescriptorSet.FromString(Path(\"service.pb\").read_bytes())");
            values.put("goDescriptorImport", "    \"google.golang.org/protobuf/proto\"\n");
            values.put("goDescriptorFile", "service.pb");
            values.put("goDescriptorUnmarshal", "proto.Unmarshal");
            values.put("jsDescriptorImport", "import 'protobufjs/ext/descriptor/index.js';");
            values.put("jsDescriptors", "const root = protobuf.Root.fromDescriptor(readFileSync('service.pb'));");
        } else {
            values.put("javaDescriptors", "var definition = FileDescriptorSet.newBuilder();\n"
                    + "        JsonFormat.parser().merge(Files.readString(Path.of(\"service.json\")), definition);");
            values.put("pythonDescriptors", "json_format.Parse(Path(\"service.json\").read_text(encoding=\"utf-8\"), descriptor_pb2.FileDescriptorSet())");
            values.put("goDescriptorImport", "");
            values.put("goDescriptorFile", "service.json");
            values.put("goDescriptorUnmarshal", "protojson.Unmarshal");
            values.put("jsDescriptorImport", "import definitions from 'protobufjs/ext/descriptor/index.js';");
            values.put("jsDescriptors", "const schema = JSON.parse(readFileSync('service.json', 'utf8'));\n"
                    + "const root = protobuf.Root.fromDescriptor(definitions.FileDescriptorSet.fromObject(schema));");
        }
    }

    private ExampleDocument document(String language, String syntax, String template, Map<String, String> values, String filesMarkdown, String setup) {
        String code = ExampleTemplates.render(template, values);
        String markdown = setup + "\n\n" + filesMarkdown + "\n\n" + ExampleTemplates.codeBlock(syntax, code);
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
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_DESCRIPTOR_GRAPH_CONTAINS_A_DEPENDENCY);
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
        if (StringUtils.equals(type.getFullName(), "google.protobuf.Any")) {
            return message.build();
        }
        parents.add(type.getFullName());
        for (var field : type.getFields()) {
            if (field.isRepeated() || (field.getContainingOneof() != null && message.hasOneof(field.getContainingOneof()))) {
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

    private void addType(JsonNode types, ObjectNode selected, String name) {
        JsonNode type = types.get(name);
        if (type == null || selected.has(name)) {
            return;
        }
        selected.set(name, type);
        for (JsonNode field : type.path("fields")) {
            this.addType(types, selected, field.path("type").asText());
        }
    }

    private static String namespace(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(0, separator);
    }

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
