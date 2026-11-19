/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.oncrpc.binding;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.protocol.oncrpc.codec.*;

/** Compiles the published XDR declaration to the same scalar and aggregate codecs used by local Java bindings. */
final class XdrContractCompiler {
    private final JsonNode                      declarations;
    private final int                           maxBytes;
    private final int                           maxElements;
    private final Map<String, XdrCodec<Object>> compiled = new HashMap<>();

    XdrContractCompiler(JsonNode declarations, int maxBytes, int maxElements) {
        this.declarations = declarations;
        this.maxBytes = maxBytes;
        this.maxElements = maxElements;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    XdrCodec<Object> compile(String reference) {
        XdrCodec<Object> existing = this.compiled.get(reference);
        if (existing != null) {
            return existing;
        }
        JsonNode type = this.declaration(reference);
        String kind = type.path("kind").asText();
        if (StringUtils.equals(kind, "struct")) {
            XdrStructCodec struct = new XdrStructCodec(LinkedHashMap.class);
            struct.name = type.path("name").asText();
            this.compiled.put(reference, struct);
            for (JsonNode member : type.path("members")) {
                struct.fields.add(this.field(member));
            }
            return struct;
        }
        if (StringUtils.equals(kind, "union")) {
            XdrUnionCodec union = new XdrUnionCodec(LinkedHashMap.class);
            this.compiled.put(reference, union);
            JsonNode discriminator = type.path("discriminator");
            union.discriminator = this.field(discriminator);
            JsonNode tag = this.declaration(discriminator.path("type").textValue());
            String kindOfTag = tag.path("kind").asText();
            if (StringUtils.equals("int", kindOfTag) || StringUtils.equals("unsigned_int", kindOfTag)) {
                union.discriminator.valueBinding = value -> value instanceof Number number ? discriminator(tag, new BigDecimal(number.toString()).longValueExact()) : value;
            }
            for (JsonNode arm : type.path("members")) {
                XdrPropertyBinding field = null;
                if (!StringUtils.equals("void", this.declaration(arm.path("type").textValue()).path("kind").asText())) {
                    field = this.field(arm);
                    union.members.add(field);
                }
                if (arm.path("defaultArm").asBoolean()) {
                    union.defaultDeclared = true;
                    union.fallback = field;
                }
                for (JsonNode value : arm.path("cases")) {
                    union.cases.put(discriminator(tag, value.longValue()), field);
                }
            }
            return union;
        }

        XdrCodec<?> value = switch (kind) {
            case "int" -> numeric(XdrCodecs.INT, BigDecimal::intValueExact);
            case "unsigned_int" -> numeric(XdrCodecs.UNSIGNED_INT, BigDecimal::longValueExact);
            case "hyper" -> numeric(XdrCodecs.LONG, BigDecimal::longValueExact);
            case "unsigned_hyper" -> numeric(XdrCodecs.UNSIGNED_LONG, BigDecimal::toBigIntegerExact);
            case "float" -> numeric(XdrCodecs.FLOAT, BigDecimal::floatValue);
            case "double" -> numeric(XdrCodecs.DOUBLE, BigDecimal::doubleValue);
            case "bool" -> XdrCodecs.BOOLEAN;
            case "void" -> XdrCodecs.VOID;
            case "string" -> XdrCodecs.string(bound(type, this.maxBytes));
            case "opaque" -> {
                if (!type.has("length")) {
                    throw new IllegalArgumentException("No published declaration for custom XDR codec " + reference);
                }
                yield this.binary(type);
            }
            case "quadruple" -> XdrCodecs.QUADRUPLE;
            case "optional" -> XdrCodecs.optional(this.compile(type.path("elementType").textValue()));
            case "array" -> {
                XdrCodec<Object> element = this.compile(type.path("elementType").textValue());
                yield type.path("fixed").asBoolean() ? XdrCodecs.fixedArray(element, fixedLength(type, this.maxElements)) : XdrCodecs.list(element, bound(type, this.maxElements));
            }
            case "enum" -> enumeration(type);
            default -> throw new IllegalArgumentException("Unsupported published XDR type: " + kind);
        };
        XdrCodec<Object> codec = (XdrCodec) value;
        this.compiled.put(reference, codec);
        return codec;
    }

    JsonNode declaration(String name) {
        JsonNode result = this.declarations.path(StringUtils.defaultString(name));
        if (!result.isObject()) {
            throw new IllegalArgumentException("Missing XDR type declaration: " + name);
        }
        return result;
    }

    private XdrPropertyBinding field(JsonNode member) {
        XdrPropertyBinding binding = new XdrPropertyBinding();
        binding.name = member.path("name").asText();
        binding.codec = this.compile(member.path("type").textValue());
        return binding;
    }

    private XdrCodec<byte[]> binary(JsonNode type) {
        return type.path("fixed").asBoolean() ? XdrCodecs.fixedOpaque(fixedLength(type, this.maxBytes)) : XdrCodecs.opaque(bound(type, this.maxBytes));
    }

    private static int bound(JsonNode type, int maximum) {
        long length = type.path("length").asLong(-1);
        return length >= 0 ? (int) Math.min(maximum, length) : maximum;
    }

    private static int fixedLength(JsonNode type, int maximum) {
        long length = type.path("length").asLong(-1);
        if (length < 0 || length > maximum) {
            throw new IllegalArgumentException("XDR fixed length exceeds the configured bound");
        }
        return (int) length;
    }

    private static XdrCodec<String> enumeration(JsonNode type) {
        Map<Integer, String> values = new HashMap<>();
        Map<String, Integer> names = new HashMap<>();
        for (JsonNode value : type.path("values")) {
            String name = value.path("name").asText();
            int number = value.path("fieldId").intValue();
            values.put(number, name);
            names.put(name, number);
        }
        return XdrCodecs.enumeration(values::get, name -> {
            Integer number = names.get(name);
            if (number == null) {
                throw new IllegalArgumentException("Unknown XDR enum value: " + name);
            }
            return number;
        });
    }

    private static Object discriminator(JsonNode type, long value) {
        return switch (type.path("kind").asText()) {
            case "bool" -> value != 0;
            case "int" -> Math.toIntExact(value);
            case "unsigned_int" -> value;
            case "enum" -> {
                String name = null;
                for (JsonNode constant : type.path("values")) {
                    if (value == constant.path("fieldId").longValue()) {
                        name = constant.path("name").asText();
                        break;
                    }
                }
                if (name == null) {
                    throw new IllegalArgumentException("Unknown XDR enum discriminator " + value);
                }
                yield name;
            }
            default -> throw new IllegalArgumentException("Invalid XDR discriminator type");
        };
    }

    private static <T extends Number> XdrCodec<Object> numeric(XdrCodec<T> codec, Function<BigDecimal, T> convert) {
        return new XdrCodec<>() {
            @Override
            public Object read(XdrInput input) {
                return codec.read(input);
            }

            @Override
            public void write(XdrOutput output, Object value) {
                if (!(value instanceof Number number)) {
                    throw new IllegalArgumentException("XDR number required");
                }
                BigDecimal decimal = number instanceof BigInteger integer ? new BigDecimal(integer) : new BigDecimal(number.toString());
                codec.write(output, convert.apply(decimal));
            }
        };
    }
}
