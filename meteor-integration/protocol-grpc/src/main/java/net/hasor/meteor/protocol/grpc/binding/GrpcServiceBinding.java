/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc.binding;
import java.lang.reflect.Method;
import java.util.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.connector.ProtocolOperationBinder.Binding;
import net.hasor.meteor.connector.ProtocolOperationBinder.Target;
import net.hasor.meteor.connector.transport.http.HttpSchemaResource;
import net.hasor.meteor.domain.MetRuntimeUtils;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.MethodMeta;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.meta.ServiceMeta;
import net.hasor.meteor.domain.meta.ServiceMetaParser;
import net.hasor.meteor.protocol.grpc.GrpcProtoResource;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;

/** gRPC 家族共享的静态服务绑定；不持有会话、网络资源或业务对象。 */
public final class GrpcServiceBinding {
    private final HttpSchemaResource               schemaResource;
    private final String                           serviceName;
    private final GrpcProtoSchema                  schema;
    private final ServiceDescriptor                service;
    private final Map<String, Method>              javaMethods;
    private final Map<String, Method>              remoteMethods = new LinkedHashMap<>();
    private final Map<Method, MethodDescriptor>    descriptors   = new LinkedHashMap<>();
    private final Map<Method, GrpcMethodShape>     shapes        = new LinkedHashMap<>();
    private final Map<Method, GrpcValueMapping>    requests      = new LinkedHashMap<>();
    private final Map<Method, GrpcValueMapping>    responses     = new LinkedHashMap<>();
    private final Map<Descriptor, GrpcMessageTree> messageTrees  = new IdentityHashMap<>();
    private final JsonFormat.TypeRegistry          registry;

    private GrpcServiceBinding(GrpcProtoSchema schema, String serviceName, Map<String, Method> methods, Class<?> type) {
        this.serviceName = serviceName;
        this.schema = schema;
        this.service = schema == null ? null : schema.service(serviceName);
        if (this.service == null || schema.getSources().isEmpty()) {
            this.schemaResource = null;
        } else {
            this.schemaResource = new HttpSchemaResource("proto", this.service.getFile().getName(), schema.getSources());
        }
        this.javaMethods = methods;
        Map<String, GrpcValueMapping> mappings = new LinkedHashMap<>();
        for (Method method : methods.values()) {
            this.shapes.put(method, GrpcMethodShape.fromMethod(method, type));
            String remoteName = MetRuntimeUtils.getProtocolMethodName(method);
            this.remoteMethods.put(remoteName, method);
            if (this.service == null) {
                continue;
            }

            MethodDescriptor descriptor = this.service.findMethodByName(remoteName);
            this.descriptors.put(method, descriptor);
            GrpcMessageTree.compile(descriptor.getInputType(), this.messageTrees);
            GrpcMessageTree.compile(descriptor.getOutputType(), this.messageTrees);
            JavaType request = GrpcValueMapping.messageType(type, method, true);
            JavaType response = GrpcValueMapping.messageType(type, method, false);
            this.requests.put(method, GrpcValueMapping.compile(request, descriptor.getInputType(), mappings));
            this.responses.put(method, GrpcValueMapping.compile(response, descriptor.getOutputType(), mappings));
        }

        JsonFormat.TypeRegistry.Builder registry = JsonFormat.TypeRegistry.newBuilder();
        if (schema != null) {
            Set<String> visited = new HashSet<>();
            for (FileDescriptor file : schema.fileDescriptors()) {
                addTypes(file, registry, visited);
            }
        }
        this.registry = registry.build();
    }

    /** 无文件时按公共编号编译；显式资源类型或映射冲突立即报错。 */
    public static GrpcServiceBinding prepare(ProtocolConfig config, ProtocolMeta meta, String defaultCoding) {
        MetBindInfo<?> service = meta.getService();
        if (service.isMessage()) {
            return null;
        }
        String serviceName = MetRuntimeUtils.getProtocolServiceName(service, config.name());
        if (serviceName == null || !serviceName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid " + config.name() + " service name: " + serviceName);
        }
        Object source = meta.getBindResource();
        if (source != null && !(source instanceof GrpcProtoResource)) {
            throw new IllegalArgumentException("Protocol " + config.name() + " requires a GrpcProtoResource");
        }
        String coding = service.getSerializeType();
        if (StringUtils.isBlank(coding)) {
            coding = config.option("serializeType", defaultCoding);
        }
        GrpcProtoSchema schema = source instanceof GrpcProtoResource resource //
                ? GrpcProtoSchema.fromResource(resource) : null;
        ServiceMeta description = meta.getServiceMeta();
        Map<String, Method> methods = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (MethodMeta methodMeta : description.getMethods()) {
            Method method = methodMeta.getJavaMethod();
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            String name = methodMeta.getName();
            boolean shape = GrpcMethodShape.supports(method, service.getBindType());
            if (!shape || methodMeta.isMessage() //
                    || methods.putIfAbsent(method.getName(), method) != null || !names.add(name)) {
                return null;
            }
            GrpcProtoCompiler.identifier(name);
        }
        if (schema == null && StringUtils.equalsIgnoreCase(coding, "Protobuf")) {
            schema = GrpcProtoCompiler.compile(serviceName, methods.values(), description);
            if (schema == null) {
                return null;
            }
        }
        if (schema != null) {
            ServiceDescriptor declared = schema.service(serviceName);
            if (declared == null) {
                throw new IllegalArgumentException("Service is not declared by proto: " + serviceName);
            }
            for (Method method : methods.values()) {
                MethodDescriptor target = declared.findMethodByName(MetRuntimeUtils.getProtocolMethodName(method));
                if (target == null) {
                    String message = "Method is not declared by proto service " + serviceName + ": " + method;
                    throw new IllegalArgumentException(message);
                }
                boolean request = GrpcValueMapping.streaming(method, true) == target.isClientStreaming();
                boolean response = GrpcValueMapping.streaming(method, false) == target.isServerStreaming();
                if (!request || !response) {
                    String message = "Streaming mode differs from proto service " + serviceName + ": " + method;
                    throw new IllegalArgumentException(message);
                }
            }
        }
        return new GrpcServiceBinding(schema, serviceName, methods, service.getBindType());
    }

    public String serviceName() {
        return this.serviceName;
    }

    public HttpSchemaResource schemaResource() {
        return this.schemaResource;
    }

    public GrpcProtoSchema schema() {
        return this.schema;
    }

    /** 从已编译的操作和消息图导出端点契约，外部 proto 和自动生成入口共用。 */
    public ObjectNode describeContract(ObjectNode description) {
        description.put("name", this.serviceName);
        if (this.schema != null) {
            description.put("protobufDescriptors", Base64.getEncoder().encodeToString(this.schema.descriptorSet()));
        }

        ArrayNode methods = description.putArray("methods");
        ObjectNode types = description.putObject("types");
        for (Method method : this.javaMethods.values()) {
            ObjectNode operation = methods.addObject();
            operation.put("name", this.remoteName(method));
            operation.put("javaSignature", ServiceMetaParser.method(method).getJavaSignature());
            GrpcMethodShape shape = this.shape(method);
            MethodDescriptor descriptor = this.descriptors.get(method);
            if (descriptor == null) {
                operation.put("requestType", shape.requestType().getTypeName());
                operation.put("responseType", shape.responseType().getTypeName());
                operation.put("clientStreaming", shape.streamingRequest());
                operation.put("serverStreaming", shape.streamingResponse());
            } else {
                operation.put("requestType", describeMessage(descriptor.getInputType(), types));
                operation.put("responseType", describeMessage(descriptor.getOutputType(), types));
                operation.put("clientStreaming", descriptor.isClientStreaming());
                operation.put("serverStreaming", descriptor.isServerStreaming());
            }
        }
        return description;
    }

    private static String describeMessage(Descriptor descriptor, ObjectNode types) {
        String name = descriptor.getFullName();
        if (types.has(name)) {
            return name;
        }
        ObjectNode message = types.putObject(name);
        message.put("kind", "message");
        ArrayNode fields = message.putArray("fields");
        for (FieldDescriptor field : descriptor.getFields()) {
            ObjectNode member = fields.addObject();
            member.put("fieldId", field.getNumber());
            member.put("name", field.getName());
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                member.put("type", describeMessage(field.getMessageType(), types));
            } else if (field.getJavaType() == FieldDescriptor.JavaType.ENUM) {
                EnumDescriptor enumeration = field.getEnumType();
                String enumName = enumeration.getFullName();
                member.put("type", enumName);
                if (!types.has(enumName)) {
                    ObjectNode values = types.putObject(enumName);
                    values.put("kind", "enum");
                    ArrayNode constants = values.putArray("values");
                    for (EnumValueDescriptor constant : enumeration.getValues()) {
                        ObjectNode value = constants.addObject();
                        value.put("fieldId", constant.getNumber());
                        value.put("name", constant.getName());
                    }
                }
            } else {
                member.put("type", field.getType().name());
            }
            member.put("jsonName", field.getJsonName());
            member.put("repeated", field.isRepeated());
            member.put("required", field.isRequired());
            if (field.getContainingOneof() != null) {
                member.put("oneof", field.getContainingOneof().getName());
            }
        }
        return name;
    }

    public ServiceDescriptor service() {
        return this.service;
    }

    public Collection<Method> methods() {
        return this.javaMethods.values();
    }

    public GrpcMethodShape shape(Method method) {
        return this.shapes.get(method);
    }

    public Method javaMethod(String name) {
        Method method = this.javaMethods.get(name);
        if (method == null) {
            throw new UnsupportedOperationException("Unknown Java RPC method: " + name);
        }
        return method;
    }

    public Method remoteMethod(String name) {
        Method method = this.remoteMethods.get(name);
        if (method == null) {
            throw new UnsupportedOperationException("Unknown protocol RPC method: " + name);
        }
        return method;
    }

    public String remoteName(Method method) {
        MethodDescriptor descriptor = this.descriptors.get(method);
        return descriptor == null ? MetRuntimeUtils.getProtocolMethodName(method) : descriptor.getName();
    }

    public MethodDescriptor descriptor(Method method) {
        MethodDescriptor descriptor = this.descriptors.get(method);
        if (descriptor == null) {
            throw new IllegalArgumentException("Protobuf service must bind proto file or annotated message fields.");
        }
        return descriptor;
    }

    public GrpcOperation operation(Method method) {
        return new GrpcOperation(this.shape(method), this.descriptors.get(method), this.registry, (value, request) -> this.encode(method, value, request), //
                (bytes, request) -> this.decode(method, bytes, request));
    }

    public static void prepareOperation(JsonNode contract, Target operation, Binding<? super GrpcOperation> binding) {
        String descriptors = contract.path("protobufDescriptors").textValue();
        TypeFactory types = TypeFactory.defaultInstance();
        if (descriptors == null) {
            if (StringUtils.equalsIgnoreCase("Protobuf", contract.path("serializeType").textValue())) {
                throw new IllegalArgumentException("Published protobuf descriptor set is required");
            }
            binding.bind(operationPath(contract, operation), //
                    new GrpcOperation(GrpcMethodShape.unary(Object.class, Object.class), null, null, null, null));
            return;
        }

        GrpcProtoSchema schema = GrpcProtoSchema.fromDescriptorSet(Base64.getDecoder().decode(descriptors));
        ServiceDescriptor declared = schema.service(operation.serviceName());
        MethodDescriptor method = declared == null ? null : declared.findMethodByName(operation.methodName());
        if (method == null || method.isClientStreaming() || method.isServerStreaming()) {
            throw new IllegalArgumentException("Published unary protobuf operation is required: " + operation.methodName());
        }

        Map<Descriptor, GrpcMessageTree> trees = new IdentityHashMap<>();
        GrpcMessageTree.compile(method.getInputType(), trees);
        GrpcMessageTree.compile(method.getOutputType(), trees);
        Map<String, GrpcValueMapping> mappings = new HashMap<>();
        JavaType objectType = types.constructType(Object.class);
        GrpcValueMapping input = GrpcValueMapping.compile(objectType, method.getInputType(), mappings);
        GrpcValueMapping output = GrpcValueMapping.compile(objectType, method.getOutputType(), mappings);
        JsonFormat.TypeRegistry.Builder registry = JsonFormat.TypeRegistry.newBuilder();
        Set<String> visited = new HashSet<>();
        schema.fileDescriptors().forEach(file -> addTypes(file, registry, visited));
        JsonFormat.TypeRegistry linked = registry.build();

        GrpcOperation bound = new GrpcOperation(GrpcMethodShape.unary(Object.class, Object.class), method, linked, //
                (value, request) -> {
                    Descriptor descriptor = request ? method.getInputType() : method.getOutputType();
                    return encode(descriptor, request ? input : output, trees.get(descriptor), linked, value);
                }, (bytes, request) -> {
            Descriptor descriptor = request ? method.getInputType() : method.getOutputType();
            return decode(descriptor, request ? input : output, trees.get(descriptor), linked, bytes);
        });
        binding.bind(operationPath(contract, operation), bound);
    }

    private static String operationPath(JsonNode contract, Target operation) {
        String path = operation.requestPath();
        if (contract.path("contextExact").asBoolean()) {
            return path;
        }
        return (path.endsWith("/") ? path : path + "/") + operation.serviceName() + "/" + operation.methodName();
    }

    public DynamicMessage encode(Method method, Object value, boolean request) throws Exception {
        Descriptor descriptor = this.message(method, request);
        return encode(descriptor, this.mapping(method, request), this.messageTrees.get(descriptor), this.registry, value);
    }

    private static DynamicMessage encode(Descriptor descriptor, GrpcValueMapping mapping, GrpcMessageTree tree, JsonFormat.TypeRegistry registry, Object value) throws Exception {
        if (value instanceof Message message) {
            if (!StringUtils.equals(message.getDescriptorForType().getFullName(), descriptor.getFullName())) {
                throw new IllegalArgumentException("Protobuf value has a different message type");
            }
            return DynamicMessage.parseFrom(descriptor, message.toByteArray());
        }

        JsonNode json = mapping.toProto(GrpcValueMapping.MAPPER.valueToTree(value));
        DynamicMessage encoded = tree.tryEncode(json);
        if (encoded != null) {
            return encoded;
        }

        DynamicMessage.Builder result = DynamicMessage.newBuilder(descriptor);
        GrpcProtoSchema.mergeJson(GrpcValueMapping.MAPPER.writeValueAsString(json), result, registry);
        return result.build();
    }

    public DynamicMessage parseJson(Method method, String json, boolean request) throws Exception {
        DynamicMessage.Builder result = DynamicMessage.newBuilder(this.message(method, request));
        GrpcProtoSchema.mergeJson(json, result, this.registry);
        return result.build();
    }

    public String json(DynamicMessage message) throws Exception {
        return JsonFormat.printer().usingTypeRegistry(this.registry) //
                .omittingInsignificantWhitespace().sortingMapKeys().print(message);
    }

    public Object decode(Method method, byte[] bytes, boolean request) throws Exception {
        Descriptor descriptor = this.message(method, request);
        return decode(descriptor, this.mapping(method, request), this.messageTrees.get(descriptor), this.registry, bytes);
    }

    private static Object decode(Descriptor descriptor, GrpcValueMapping mapping, GrpcMessageTree tree, JsonFormat.TypeRegistry registry, byte[] bytes) throws Exception {
        Class<?> type = mapping.javaType().getRawClass();
        if (Message.class.isAssignableFrom(type) && type != DynamicMessage.class && type != Message.class) {
            return type.getMethod("parseFrom", byte[].class).invoke(null, bytes);
        }
        DynamicMessage message = DynamicMessage.parseFrom(descriptor, bytes);
        if (type == DynamicMessage.class || type == Message.class) {
            return message;
        }

        JsonNode json = tree.tryDecode(message);
        if (json == null) {
            json = GrpcValueMapping.MAPPER.readTree(JsonFormat.printer().usingTypeRegistry(registry).print(message));
        }

        JsonNode javaValue = mapping.fromProto(json);
        return GrpcValueMapping.MAPPER.readerFor(mapping.javaType()).readValue(javaValue);
    }

    public DynamicMessage parse(Method method, byte[] bytes, boolean request) throws Exception {
        return DynamicMessage.parseFrom(this.message(method, request), bytes);
    }

    private Descriptor message(Method method, boolean request) {
        MethodDescriptor descriptor = this.descriptor(method);
        return request ? descriptor.getInputType() : descriptor.getOutputType();
    }

    private GrpcValueMapping mapping(Method method, boolean request) {
        this.descriptor(method);
        return request ? this.requests.get(method) : this.responses.get(method);
    }

    private static void addTypes(FileDescriptor file, JsonFormat.TypeRegistry.Builder registry, Set<String> visited) {
        if (!visited.add(file.getName())) {
            return;
        }
        for (FileDescriptor dependency : file.getDependencies()) {
            addTypes(dependency, registry, visited);
        }
        registry.add(file.getMessageTypes());
    }
}
