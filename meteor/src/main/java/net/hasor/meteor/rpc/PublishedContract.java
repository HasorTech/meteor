/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import net.hasor.cobble.StringUtils;

/** Service declarations and wire operation names read from a single published endpoint. */
final class PublishedContract {
    private static final ObjectMapper                   VALUE_MAPPER = JsonMapper.builder().build();
    private final        String                         protocol;
    private final        String                         serviceName;
    private final        String                         serializeType;
    private final        String                         mountPath;
    private final        String                         requestPath;
    private final        boolean                        message;
    private final        Map<String, MethodDeclaration> methods;
    private final        Map<String, WireOperation>     operationsByName;
    private final        Map<String, WireOperation>     operationsBySignature;
    private final        Map<String, TypeDeclaration>   types;

    PublishedContract(String protocol, String mountPath, JsonNode service, JsonNode endpoint) {
        this.protocol = protocol;
        this.serviceName = endpoint.path("name").textValue();
        this.serializeType = endpoint.path("serializeType").textValue();
        this.mountPath = mountPath;
        if (StringUtils.equals("hessian", protocol)) {
            this.requestPath = endpoint.path("endpoints").path(mountPath).path("http").asText(mountPath);
        } else {
            this.requestPath = mountPath;
        }
        this.message = service.path("message").asBoolean();
        this.methods = readOperations(service.path("methods"), "name", MethodDeclaration::new);
        this.operationsByName = readOperations(endpoint.path("methods"), "name", WireOperation::new);
        this.operationsBySignature = readOperations(endpoint.path("methods"), "javaSignature", WireOperation::new);

        Map<String, TypeDeclaration> declarations = new LinkedHashMap<>();
        service.path("types").fields().forEachRemaining(entry -> {
            declarations.put(entry.getKey(), new TypeDeclaration(entry.getValue()));
        });
        this.types = Collections.unmodifiableMap(declarations);
    }

    Call bindCall(String name, Object[] arguments) {
        MethodDeclaration method = requireOperation(this.methods, name);
        String signature = method.signature;
        WireOperation wire;
        if (signature == null) {
            wire = requireOperation(this.operationsByName, name);
        } else {
            wire = requireOperation(this.operationsBySignature, signature);
        }
        if (StringUtils.isBlank(wire.name) || this.message || method.message || wire.nonUnary) {
            throw new IllegalArgumentException("Generic calls require a published unary method: " + name);
        }
        if (arguments == null || method.parameterTypes == null || arguments.length != method.parameterTypes.size()) {
            throw new IllegalArgumentException("Argument count differs from the published method: " + name);
        }
        if (StringUtils.isBlank(this.serializeType)) {
            throw new UnsupportedOperationException("Unsupported generic serialization: " + this.serializeType);
        }

        String[] wireTypes = this.wireParameterTypes(signature, arguments.length);
        Object[] values = new Object[arguments.length];
        List<String> parameterTypes = new ArrayList<>(arguments.length);
        boolean xmlNumber = StringUtils.equals("xmlrpc", this.protocol);
        for (int index = 0; index < arguments.length; index++) {
            String type = method.parameterTypes.get(index);
            parameterTypes.add(wireTypes == null ? StringUtils.defaultString(type) : wireTypes[index]);
            values[index] = this.bindValue(arguments[index], type, xmlNumber, new IdentityHashMap<>());
        }
        String resultClass = this.type(method.returnType).javaType;
        Class<?> returnType = StringUtils.equals(resultClass, "void") ? void.class : Object.class;
        ProtocolOperation operation = new ProtocolOperation(this.serviceName, wire.name, this.requestPath, //
                returnType, javaBinaryType(resultClass));
        return new Call(operation, this.serializeType, parameterTypes, values);
    }

    private String[] wireParameterTypes(String signature, int count) {
        switch (this.protocol) {
            case "rsf":
                if (!StringUtils.equalsIgnoreCase(this.serializeType, "Json")) {
                    throw new UnsupportedOperationException("Unsupported RSF serialization: " + this.serializeType);
                }
                return rsfParameterTypes(signature, count);
            case "dubbo":
                return rsfParameterTypes(signature, count);
            case "oncrpc":
            case "oncrpc-udp":
            case "thrift":
                return null;
            case "jsonrpc":
            case "xmlrpc":
            case "hprose":
            case "hessian":
            case "grpc":
            case "grpc-web":
            case "connect":
            case "thrift-http":
            case "triple":
            case "soap":
                if (this.mountPath == null || !this.mountPath.startsWith("/") || this.mountPath.startsWith("//") //
                        || !StringUtils.equals(this.mountPath, URI.create(this.mountPath).getRawPath()) //
                        || StringUtils.isBlank(this.serviceName)) {
                    throw new IllegalArgumentException("HTTP RPC requires a published service path and alias");
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported generic protocol: " + this.protocol);
        }
        if (StringUtils.equals("triple", this.protocol)) {
            String[] types = signatureParameterTypes(signature, count);
            for (int index = 0; index < types.length; index++) {
                types[index] = javaBinaryType(types[index]);
            }
            return types;
        }
        return null;
    }

    private TypeDeclaration type(String reference) {
        return this.types.getOrDefault(StringUtils.defaultString(reference), TypeDeclaration.UNKNOWN);
    }

    private Object bindValue(Object value, String reference, boolean xmlNumber, //
            IdentityHashMap<Object, Boolean> parents) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode) {
            throw new IllegalArgumentException("RPC arguments must be Java values. Convert the JSON tree before calling.");
        }

        TypeDeclaration type = this.type(reference);
        switch (type.javaType) {
            case "byte[]":
                return VALUE_MAPPER.convertValue(value, byte[].class);
            case "java.util.Date":
                return VALUE_MAPPER.convertValue(value, Date.class);
            case "java.sql.Date":
                return VALUE_MAPPER.convertValue(value, java.sql.Date.class);
            case "java.sql.Timestamp":
                return VALUE_MAPPER.convertValue(value, java.sql.Timestamp.class);
            case "java.sql.Time":
                return VALUE_MAPPER.convertValue(value, java.sql.Time.class);
        }

        boolean container = value instanceof Map<?, ?> || value instanceof List<?>;
        if (container && parents.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic argument is not supported");
        }
        try {
            if (value instanceof Map<?, ?> fields) {
                return this.bindFields(fields, type, xmlNumber, parents);
            }
            if (value instanceof List<?> elements) {
                List<Object> result = new ArrayList<>(elements.size());
                for (Object element : elements) {
                    result.add(this.bindValue(element, type.elementType, xmlNumber, parents));
                }
                return result;
            }
        } finally {
            if (container) {
                parents.remove(value);
            }
        }
        if (xmlNumber && value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return value;
    }

    private Map<String, Object> bindFields(Map<?, ?> fields, TypeDeclaration type, boolean xmlNumber, //
            IdentityHashMap<Object, Boolean> parents) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> field : fields.entrySet()) {
            if (!(field.getKey() instanceof String key)) {
                throw new IllegalArgumentException("The contract requires string field names");
            }
            String reference = type.fieldTypes.getOrDefault(key, type.mapValueType);
            result.put(key, this.bindValue(field.getValue(), reference, xmlNumber, parents));
        }
        if (!type.structure) {
            return result;
        }

        for (FieldDeclaration field : type.fields) {
            if (!result.containsKey(field.javaName) && !result.containsKey(field.publicName)) {
                Object defaultValue = primitiveZero(this.type(field.type).javaType);
                if (defaultValue != null) {
                    result.put(field.javaName, defaultValue);
                }
            }
        }
        return new MetStruct(type.javaType, result);
    }

    private static <T> Map<String, T> readOperations(JsonNode methods, String identity, Function<JsonNode, T> reader) {
        Map<String, JsonNode> definitions = new LinkedHashMap<>();
        for (JsonNode method : methods) {
            String name = method.path(identity).textValue();
            if (StringUtils.isBlank(name)) {
                continue;
            }
            if (definitions.containsKey(name) && !method.equals(definitions.get(name))) {
                // Keep the ambiguity on this key so unrelated operations can still be called.
                definitions.put(name, null);
            } else {
                definitions.put(name, method);
            }
        }
        Map<String, T> index = new LinkedHashMap<>();
        definitions.forEach((name, method) -> index.put(name, method == null ? null : reader.apply(method)));
        return Collections.unmodifiableMap(index);
    }

    private static <T> T requireOperation(Map<String, T> operations, String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Missing published method identity");
        }
        if (!operations.containsKey(name)) {
            throw new IllegalArgumentException("Method is not published: " + name);
        }
        T operation = operations.get(name);
        if (operation == null) {
            throw new IllegalArgumentException("Ambiguous published method: " + name);
        }
        return operation;
    }

    private static Object primitiveZero(String name) {
        return switch (name) {
            case "boolean" -> false;
            case "char" -> '\0';
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0F;
            case "double" -> 0D;
            default -> null;
        };
    }

    private static String[] rsfParameterTypes(String signature, int count) {
        String[] types = signatureParameterTypes(signature, count);
        for (int index = 0; index < types.length; index++) {
            types[index] = rsfType(types[index]);
        }
        return types;
    }

    private static String[] signatureParameterTypes(String signature, int count) {
        int start = signature == null ? -1 : signature.indexOf('(');
        if (start <= 0 || !signature.endsWith(")")) {
            throw new IllegalArgumentException("A published Java signature is required");
        }
        String parameters = signature.substring(start + 1, signature.length() - 1);
        String[] types = parameters.isEmpty() ? new String[0] : parameters.split(",", -1);
        if (types.length != count) {
            throw new IllegalArgumentException("Invalid published Java signature: " + signature);
        }
        return types;
    }

    private static String javaBinaryType(String type) {
        int dimensions = 0;
        while (type.endsWith("[]")) {
            dimensions++;
            type = type.substring(0, type.length() - 2);
        }
        if (dimensions == 0) {
            return type;
        }
        String component = rsfType(type);
        return "[".repeat(dimensions) + (component.length() == 1 ? component : "L" + component + ";");
    }

    private static String rsfType(String name) {
        if (name.endsWith("[]")) {
            return "[" + rsfType(name.substring(0, name.length() - 2));
        }
        return switch (name) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "short" -> "S";
            case "char" -> "C";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> name;
        };
    }

    /** Prepared values for one invocation. All types are resolved without loading business classes. */
    static final class Call {
        final ProtocolOperation operation;
        final String            serializeType;
        final List<String>      parameterTypes;
        final Object[]          arguments;

        private Call(ProtocolOperation operation, String serializeType, //
                List<String> parameterTypes, Object[] arguments) {
            this.operation = operation;
            this.serializeType = serializeType;
            this.parameterTypes = parameterTypes;
            this.arguments = arguments;
        }
    }

    private static final class MethodDeclaration {
        final String       signature;
        final boolean      message;
        final List<String> parameterTypes;
        final String       returnType;

        MethodDeclaration(JsonNode method) {
            this.signature = method.path("javaSignature").textValue();
            this.message = method.path("message").asBoolean();
            this.returnType = method.path("returns").path("type").textValue();
            JsonNode parameters = method.path("parameters");
            if (parameters.isArray()) {
                List<String> types = new ArrayList<>(parameters.size());
                parameters.forEach(parameter -> types.add(parameter.path("type").textValue()));
                this.parameterTypes = Collections.unmodifiableList(types);
            } else {
                this.parameterTypes = null;
            }
        }
    }

    private static final class WireOperation {
        final String  name;
        final boolean nonUnary;

        WireOperation(JsonNode operation) {
            this.name = operation.path("name").textValue();
            this.nonUnary = operation.path("message").asBoolean() || operation.path("clientStreaming").asBoolean() //
                    || operation.path("serverStreaming").asBoolean();
        }
    }

    private static final class TypeDeclaration {
        static final TypeDeclaration UNKNOWN = new TypeDeclaration(MissingNode.getInstance());

        final String                 javaType;
        final boolean                structure;
        final String                 elementType;
        final String                 mapValueType;
        final List<FieldDeclaration> fields;
        final Map<String, String>    fieldTypes;

        TypeDeclaration(JsonNode type) {
            this.javaType = type.path("javaType").asText();
            String kind = type.path("kind").asText();
            this.structure = StringUtils.equals("object", kind) && StringUtils.isNotBlank(this.javaType);
            JsonNode arguments = type.path("arguments");
            this.elementType = type.path("elementType").asText(arguments.path(0).textValue());
            this.mapValueType = arguments.path(1).textValue();

            List<FieldDeclaration> fields = new ArrayList<>();
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            for (JsonNode property : type.path("properties")) {
                FieldDeclaration field = new FieldDeclaration(property);
                fields.add(field);
                fieldTypes.put(field.javaName, field.type);
                if (field.publicName != null) {
                    fieldTypes.put(field.publicName, field.type);
                }
            }
            this.fields = List.copyOf(fields);
            this.fieldTypes = Collections.unmodifiableMap(fieldTypes);
        }
    }

    private static final class FieldDeclaration {
        final String javaName;
        final String publicName;
        final String type;

        FieldDeclaration(JsonNode field) {
            this.javaName = field.path("javaName").asText();
            this.publicName = field.path("name").textValue();
            this.type = field.path("type").textValue();
        }
    }
}
