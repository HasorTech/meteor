/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.soap.codec;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import javax.xml.namespace.QName;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;

/** XML value shape resolved during operation binding. */
final class SoapLiteralType implements Type {
    final String         name;
    final Type           content;
    final boolean        map;
    final List<Property> properties = new ArrayList<>();

    private SoapLiteralType(String name, Type content, boolean map) {
        this.name = name;
        this.content = content;
        this.map = map;
    }

    @Override
    public String getTypeName() {
        return this.name;
    }

    static final class Property {
        final String  name;
        final QName   element;
        final QName   wrapper;
        final Type    type;
        final boolean attribute;
        final boolean text;
        final boolean required;
        final boolean nillable;

        Property(JsonNode field, Type type) {
            this.name = field.path("javaName").asText();
            this.element = new QName(field.path("namespace").asText(), field.path("name").asText());
            this.wrapper = field.has("wrapper") ? QName.valueOf(field.path("wrapper").asText()) : null;
            this.type = type;
            this.attribute = field.path("attribute").asBoolean();
            this.text = field.path("text").asBoolean();
            this.required = field.path("required").asBoolean();
            this.nillable = field.path("nillable").asBoolean();
        }
    }

    static final class Compiler {
        private final JsonNode                     declarations;
        private final Map<String, SoapLiteralType> structures = new HashMap<>();

        Compiler(JsonNode declarations) {
            this.declarations = declarations;
        }

        Type compile(String name, boolean encoded) {
            Class<?> scalar = switch (name) {
                case "java.lang.Object" -> Object.class;
                case "java.lang.String", "char", "java.lang.Character" -> String.class;
                case "boolean", "java.lang.Boolean" -> Boolean.class;
                case "byte", "java.lang.Byte" -> Byte.class;
                case "short", "java.lang.Short" -> Short.class;
                case "int", "java.lang.Integer" -> Integer.class;
                case "long", "java.lang.Long" -> Long.class;
                case "float", "java.lang.Float" -> Float.class;
                case "double", "java.lang.Double" -> Double.class;
                case "java.math.BigInteger" -> BigInteger.class;
                case "java.math.BigDecimal" -> BigDecimal.class;
                case "java.util.Date" -> Date.class;
                case "byte[]", "[B" -> byte[].class;
                case "char[]", "[C" -> char[].class;
                default -> null;
            };
            if (scalar != null) {
                return scalar;
            }
            if (encoded) {
                throw new UnsupportedOperationException("Published SOAP Encoding requires scalar parts: " + name);
            }
            if (name.endsWith("[]")) {
                return new SoapLiteralType(name, this.compile(name.substring(0, name.length() - 2), false), false);
            }
            int generic = name.indexOf('<');
            if (generic >= 0 && name.endsWith(">")) {
                String arguments = name.substring(generic + 1, name.length() - 1);
                boolean map = arguments.startsWith("java.lang.String,");
                String item = map ? arguments.substring("java.lang.String,".length()) : arguments;
                if (map || !this.declarations.has(name)) {
                    return new SoapLiteralType(name, this.compile(item, false), map);
                }
            }
            JsonNode declaration = this.declarations.path(name);
            if (!declaration.has("properties")) {
                if (StringUtils.isBlank(name)) {
                    throw new IllegalArgumentException("Published SOAP part type is missing");
                }
                return String.class; // XML enum values are lexical strings.
            }
            SoapLiteralType resolved = this.structures.get(name);
            if (resolved == null) {
                resolved = new SoapLiteralType(name, null, false);
                this.structures.put(name, resolved);
                for (JsonNode field : declaration.path("properties")) {
                    resolved.properties.add(new Property(field, this.compile(field.path("type").asText(), false)));
                }
            }
            return resolved;
        }
    }
}
