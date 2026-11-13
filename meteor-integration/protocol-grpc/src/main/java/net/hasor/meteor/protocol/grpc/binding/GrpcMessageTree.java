/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc.binding;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

/** Compiled ordinary-message tree conversion; special ProtoJSON forms keep the official JsonFormat path. */
final class GrpcMessageTree {
    private static final Object                                UNSUPPORTED = new Object();
    private final        Descriptor                            descriptor;
    private final        Map<String, FieldDescriptor>          fields      = new HashMap<>();
    private final        Map<FieldDescriptor, GrpcMessageTree> children    = new HashMap<>();

    private GrpcMessageTree(Descriptor descriptor) {
        this.descriptor = descriptor;
    }

    static GrpcMessageTree compile(Descriptor descriptor, Map<Descriptor, GrpcMessageTree> compiled) {
        GrpcMessageTree existing = compiled.get(descriptor);
        if (existing != null) {
            return existing;
        }
        GrpcMessageTree result = new GrpcMessageTree(descriptor);
        compiled.put(descriptor, result);
        for (FieldDescriptor field : descriptor.getFields()) {
            result.fields.put(field.getName(), field);
            result.fields.put(field.getJsonName(), field);
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                result.children.put(field, compile(field.getMessageType(), compiled));
            }
        }
        return result;
    }

    DynamicMessage tryEncode(JsonNode source) throws IOException {
        return this.encode(source, 0);
    }

    private DynamicMessage encode(JsonNode source, int depth) throws IOException {
        if (depth >= 100 || !source.isObject() || this.descriptor.getFullName().startsWith("google.protobuf.")) {
            return null;
        }
        DynamicMessage.Builder result = DynamicMessage.newBuilder(this.descriptor);
        boolean[] seen = new boolean[this.descriptor.getFields().size()];
        boolean[] oneofs = new boolean[this.descriptor.getOneofs().size()];
        Iterator<Map.Entry<String, JsonNode>> members = source.fields();
        while (members.hasNext()) {
            Map.Entry<String, JsonNode> member = members.next();
            FieldDescriptor field = this.fields.get(member.getKey());
            JsonNode value = member.getValue();
            if (field == null || field.isMapField() || value.isNull() || seen[field.getIndex()]) {
                return null;
            }
            seen[field.getIndex()] = true;
            OneofDescriptor oneof = field.getContainingOneof();
            if (oneof != null) {
                if (oneofs[oneof.getIndex()]) {
                    return null;
                }
                oneofs[oneof.getIndex()] = true;
            }
            if (field.isRepeated()) {
                if (!value.isArray()) {
                    return null;
                }
                for (JsonNode item : value) {
                    Object mapped = this.encodeValue(field, item, depth);
                    if (mapped == UNSUPPORTED) {
                        return null;
                    }
                    result.addRepeatedField(field, mapped);
                }
            } else {
                Object mapped = this.encodeValue(field, value, depth);
                if (mapped == UNSUPPORTED) {
                    return null;
                }
                result.setField(field, mapped);
            }
        }
        return result.build();
    }

    private Object encodeValue(FieldDescriptor field, JsonNode value, int depth) throws IOException {
        if (value.isNull()) {
            return UNSUPPORTED;
        }
        switch (field.getJavaType()) {
            case BOOLEAN:
                return value.isBoolean() ? value.booleanValue() : UNSUPPORTED;
            case STRING:
                return value.isTextual() ? value.textValue() : UNSUPPORTED;
            case BYTE_STRING:
                return value.isBinary() ? ByteString.copyFrom(value.binaryValue()) : UNSUPPORTED;
            case INT:
                if (value.isIntegralNumber()) {
                    boolean unsigned = field.getType() == FieldDescriptor.Type.UINT32 || field.getType() == FieldDescriptor.Type.FIXED32;
                    if (unsigned && value.canConvertToLong()) {
                        long number = value.longValue();
                        return number >= 0 && number <= 0xffffffffL ? (int) number : UNSUPPORTED;
                    }
                    if (!unsigned && value.canConvertToInt()) {
                        return value.intValue();
                    }
                }
                return UNSUPPORTED;
            case LONG:
                if (value.isIntegralNumber()) {
                    boolean unsigned = field.getType() == FieldDescriptor.Type.UINT64 || field.getType() == FieldDescriptor.Type.FIXED64;
                    if (unsigned) {
                        BigInteger number = value.bigIntegerValue();
                        return number.signum() >= 0 && number.bitLength() <= 64 ? number.longValue() : UNSUPPORTED;
                    }
                    if (value.canConvertToLong()) {
                        return value.longValue();
                    }
                }
                return UNSUPPORTED;
            case FLOAT:
                boolean numeric = value.isFloat() || value.isDouble() || value.isIntegralNumber();
                if (numeric && (value.isFloat() || Math.abs(value.doubleValue()) <= Float.MAX_VALUE)) {
                    return value.floatValue();
                }
                return UNSUPPORTED;
            case DOUBLE:
                if (value.isNumber() && (value.isDouble() || Double.isFinite(value.doubleValue()))) {
                    return value.doubleValue();
                }
                return UNSUPPORTED;
            case ENUM:
                if (field.getEnumType().getFullName().startsWith("google.protobuf.")) {
                    return UNSUPPORTED;
                }
                EnumValueDescriptor enumeration = value.isTextual() ? field.getEnumType().findValueByName(value.textValue()) : null;
                if (value.isIntegralNumber() && value.canConvertToInt()) {
                    enumeration = field.getEnumType().findValueByNumber(value.intValue());
                }
                return enumeration == null ? UNSUPPORTED : enumeration;
            case MESSAGE:
                DynamicMessage nested = this.children.get(field).encode(value, depth + 1);
                return nested == null ? UNSUPPORTED : nested;
            default:
                return UNSUPPORTED;
        }
    }

    ObjectNode tryDecode(Message source) {
        return this.decode(source, 0);
    }

    private ObjectNode decode(Message source, int depth) {
        if (depth >= 100 || this.descriptor.getFullName().startsWith("google.protobuf.")) {
            return null;
        }
        ObjectNode result = GrpcValueMapping.MAPPER.createObjectNode();
        for (Map.Entry<FieldDescriptor, Object> entry : source.getAllFields().entrySet()) {
            FieldDescriptor field = entry.getKey();
            if (field.isMapField() || field.isExtension()) {
                return null;
            }
            JsonNode value;
            if (field.isRepeated()) {
                ArrayNode items = GrpcValueMapping.MAPPER.createArrayNode();
                for (Object item : (List<?>) entry.getValue()) {
                    JsonNode mapped = this.decodeValue(field, item, depth);
                    if (mapped == null) {
                        return null;
                    }
                    items.add(mapped);
                }
                value = items;
            } else {
                value = this.decodeValue(field, entry.getValue(), depth);
                if (value == null) {
                    return null;
                }
            }
            result.set(field.getJsonName(), value);
        }
        return result;
    }

    private JsonNode decodeValue(FieldDescriptor field, Object value, int depth) {
        return switch (field.getJavaType()) {
            case BOOLEAN -> BooleanNode.valueOf((Boolean) value);
            case STRING -> TextNode.valueOf((String) value);
            case BYTE_STRING -> TextNode.valueOf(Base64.getEncoder().encodeToString(((ByteString) value).toByteArray()));
            case INT -> {
                long number = field.getType() == FieldDescriptor.Type.UINT32 || field.getType() == FieldDescriptor.Type.FIXED32 ? Integer.toUnsignedLong((Integer) value) : (Integer) value;
                yield number <= Integer.MAX_VALUE ? IntNode.valueOf((int) number) : LongNode.valueOf(number);
            }
            case LONG -> TextNode.valueOf(field.getType() == FieldDescriptor.Type.UINT64 || field.getType() == FieldDescriptor.Type.FIXED64 ? Long.toUnsignedString((Long) value) : Long.toString((Long) value));
            case FLOAT -> Float.isFinite((Float) value) ? DoubleNode.valueOf(Double.parseDouble(Float.toString((Float) value))) : TextNode.valueOf(value.toString());
            case DOUBLE -> Double.isFinite((Double) value) ? DoubleNode.valueOf((Double) value) : TextNode.valueOf(value.toString());
            case ENUM -> {
                EnumValueDescriptor enumeration = (EnumValueDescriptor) value;
                if (field.getEnumType().getFullName().startsWith("google.protobuf.")) {
                    yield null;
                }
                yield enumeration.getIndex() < 0 ? IntNode.valueOf(enumeration.getNumber()) : TextNode.valueOf(enumeration.getName());
            }
            case MESSAGE -> this.children.get(field).decode((Message) value, depth + 1);
        };
    }
}
