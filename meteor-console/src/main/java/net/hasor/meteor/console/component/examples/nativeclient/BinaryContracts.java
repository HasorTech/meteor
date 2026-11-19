/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE.txt or https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 从实际端点的线上类型图导出独立契约，不根据公共 Java 类型猜测编号或布局。 */
public final class BinaryContracts {
    private BinaryContracts() {
    }

    public static String thrift(MetProtocolDescription binding) {
        return new ThriftWriter(binding).write();
    }

    public static String oncrpc(MetProtocolDescription binding) {
        return new XdrWriter(binding).write();
    }

    static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CONTRACT_CONTAINS_AN_IDENTIFIER_THAT, value);
        }
        return value;
    }

    static JsonNode required(JsonNode types, String reference) {
        JsonNode value = types.get(reference);
        if (value == null || !value.isObject()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CONTRACT_IS_MISSING_A_WIRE, reference);
        }
        return value;
    }

    private static long unsigned(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() < 0 || value.longValue() > 0xffffffffL) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ONC_RPC_CONTRACT_IS_MISSING, field);
        }
        return value.longValue();
    }

    public static String thriftServiceName(MetProtocolDescription binding) {
        Object original = binding.getExtensions().get("idlServiceName");
        return original instanceof String name ? name : binding.getName();
    }

    private static final class ThriftWriter {
        private final MetProtocolDescription binding;
        private final JsonNode               types;
        private final JsonNode               methods;
        private final Map<String, String>    names      = new LinkedHashMap<>();
        private final Set<String>            visited    = new HashSet<>();
        private final Set<String>            exceptions = new HashSet<>();

        private ThriftWriter(MetProtocolDescription binding) {
            this.binding = binding;
            this.types = JsonUtils.valueToTree(binding.getTypes());
            this.methods = JsonUtils.valueToTree(binding.getMethods());
        }

        private String write() {
            if (this.methods.isEmpty()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_THRIFT_CONTRACT_HAS_NO_CALLABLE);
            }
            for (JsonNode method : this.methods) {
                for (JsonNode parameter : method.path("parameters")) {
                    this.collect(parameter.path("type").asText());
                }
                String response = method.path("responseType").asText(null);
                if (response != null) {
                    for (JsonNode field : required(this.types, response).path("fields")) {
                        String reference = field.path("type").asText();
                        if (field.path("fieldId").asInt() != 0) {
                            this.exceptions.add(reference);
                        }
                        this.collect(reference);
                    }
                }
            }
            StringBuilder text = new StringBuilder("namespace py meteor_example\n\n");
            for (Map.Entry<String, String> entry : this.names.entrySet()) {
                JsonNode type = required(this.types, entry.getValue());
                if (!type.path("values").isEmpty()) {
                    text.append("enum ").append(entry.getKey()).append(" {\n");
                    for (JsonNode value : type.path("values")) {
                        text.append("  ").append(identifier(value.path("name").asText()));
                        text.append(" = ").append(value.path("fieldId").asInt()).append(",\n");
                    }
                } else {
                    String kind = "struct";
                    if (type.path("union").asBoolean()) {
                        kind = "union";
                    } else if (this.exceptions.contains(entry.getValue())) {
                        kind = "exception";
                    }
                    text.append(kind).append(' ').append(entry.getKey()).append(" {\n");
                    for (JsonNode field : type.path("fields")) {
                        text.append("  ").append(this.field(field, true)).append(";\n");
                    }
                }
                text.append("}\n\n");
            }
            text.append("service ").append(identifier(thriftServiceName(this.binding))).append(" {\n");
            for (JsonNode method : this.methods) {
                JsonNode result = this.types.get(method.path("responseType").asText());
                String success = "void";
                List<JsonNode> failures = new ArrayList<>();
                if (result != null) {
                    for (JsonNode field : result.path("fields")) {
                        if (field.path("fieldId").asInt() == 0) {
                            success = this.type(field.path("type").asText());
                        } else {
                            failures.add(field);
                        }
                    }
                }
                text.append("  ");
                if (method.path("message").asBoolean()) {
                    text.append("oneway ");
                }
                text.append(success).append(' ').append(identifier(method.path("name").asText())).append('(');
                this.fields(text, method.path("parameters"));
                text.append(')');
                if (!failures.isEmpty()) {
                    text.append(" throws (");
                    this.fields(text, failures);
                    text.append(')');
                }
                text.append(";\n");
            }
            return text.append("}\n").toString();
        }

        private void collect(String reference) {
            if (!this.visited.add(reference)) {
                return;
            }
            JsonNode type = required(this.types, reference);
            int wire = type.path("wireType").asInt(-1);
            if (wire == 12 || !type.path("values").isEmpty()) {
                String name = identifier(type.path("name").asText());
                String previous = this.names.putIfAbsent(name, reference);
                if (previous != null && !StringUtils.equals(previous, reference)) {
                    if (!required(this.types, previous).equals(type)) {
                        throw new ErrorMessageException(ConsoleMessageKey.ERROR_CONFLICTING_THRIFT_TYPE_NAME, name);
                    }
                }
                for (JsonNode field : type.path("fields")) {
                    this.collect(field.path("type").asText());
                }
            }
            if (type.hasNonNull("keyType")) {
                this.collect(type.path("keyType").asText());
            }
            if (type.hasNonNull("elementType")) {
                this.collect(type.path("elementType").asText());
            }
            // 同时验证所有叶节点，未知 wire 编号不能落入猜测的默认类型。
            this.type(reference);
        }

        private String type(String reference) {
            JsonNode type = required(this.types, reference);
            if (!type.path("values").isEmpty()) {
                return identifier(type.path("name").asText());
            }
            return switch (type.path("wireType").asInt(-1)) {
                case 2 -> "bool";
                case 3 -> "byte";
                case 4 -> "double";
                case 6 -> "i16";
                case 8 -> "i32";
                case 10 -> "i64";
                case 11 -> type.path("binary").asBoolean() ? "binary" : "string";
                case 12 -> identifier(type.path("name").asText());
                case 13 -> "map<" + this.type(type.path("keyType").asText()) + ", " + this.type(type.path("elementType").asText()) + ">";
                case 14 -> "set<" + this.type(type.path("elementType").asText()) + ">";
                case 15 -> "list<" + this.type(type.path("elementType").asText()) + ">";
                case 16 -> "uuid";
                default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_UNSUPPORTED_THRIFT_WIRE_TYPE, reference);
            };
        }

        private String field(JsonNode field, boolean requirement) {
            if (!field.has("fieldId") || !field.path("fieldId").canConvertToInt()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_THRIFT_FIELD_ID);
            }
            int id = field.path("fieldId").asInt();
            if (id < Short.MIN_VALUE || id > Short.MAX_VALUE) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THRIFT_FIELD_ID_IS_OUT_OF, id);
            }
            String prefix = "";
            String policy = field.path("requirement").asText();
            if (requirement && StringUtils.equals(policy, "required")) {
                prefix = "required ";
            } else if (requirement && StringUtils.equals(policy, "optional")) {
                prefix = "optional ";
            }
            return id + ": " + prefix + this.type(field.path("type").asText()) + " " + identifier(field.path("name").asText());
        }

        private void fields(StringBuilder text, Iterable<JsonNode> fields) {
            int count = 0;
            for (JsonNode field : fields) {
                if (count++ > 0) {
                    text.append(", ");
                }
                text.append(this.field(field, false));
            }
        }
    }

    static final class XdrWriter {
        private final MetProtocolDescription binding;
        private final JsonNode               types;
        private final JsonNode               methods;
        private final Map<String, String>    names      = new LinkedHashMap<>();
        private final Set<String>            usedNames  = new HashSet<>();
        private final Set<String>            visited    = new HashSet<>();
        private final Set<String>            collecting = new HashSet<>();
        private final List<String>           ordered    = new ArrayList<>();

        XdrWriter(MetProtocolDescription binding) {
            this.binding = binding;
            this.types = JsonUtils.valueToTree(binding.getTypes());
            this.methods = JsonUtils.valueToTree(binding.getMethods());
        }

        String write() {
            if (this.methods.isEmpty()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ONC_RPC_CONTRACT_HAS_NO);
            }
            for (JsonNode method : this.methods) {
                this.collect(method.path("requestType").asText());
                this.collect(method.path("responseType").asText());
            }
            StringBuilder text = new StringBuilder();
            for (String reference : this.ordered) {
                JsonNode type = required(this.types, reference);
                String name = this.names.get(reference);
                String kind = type.path("kind").asText();
                if (StringUtils.equals(kind, "struct")) {
                    text.append("struct ").append(name).append(" {\n");
                    for (JsonNode member : type.path("members")) {
                        text.append("  ").append(this.name(member.path("type").asText())).append(' ');
                        text.append(identifier(member.path("name").asText())).append(";\n");
                    }
                    text.append("};\n\n");
                } else if (StringUtils.equals(kind, "enum")) {
                    text.append("enum ").append(name).append(" {\n");
                    int index = 0;
                    for (JsonNode value : type.path("values")) {
                        if (index++ > 0) {
                            text.append(",\n");
                        }
                        text.append("  ").append(identifier(value.path("name").asText())).append(" = ");
                        text.append(value.path("fieldId").asInt());
                    }
                    text.append("\n};\n\n");
                } else if (StringUtils.equals(kind, "union")) {
                    JsonNode discriminator = type.path("discriminator");
                    text.append("union ").append(name).append(" switch (");
                    text.append(this.name(discriminator.path("type").asText())).append(' ');
                    text.append(identifier(discriminator.path("name").asText())).append(") {\n");
                    for (JsonNode member : type.path("members")) {
                        for (JsonNode value : member.path("cases")) {
                            text.append("case ").append(value.asLong()).append(":\n");
                        }
                        if (member.path("defaultArm").asBoolean()) {
                            text.append("default:\n");
                        }
                        String memberType = this.name(member.path("type").asText());
                        text.append("  ").append(memberType);
                        if (!StringUtils.equals(memberType, "void")) {
                            text.append(' ').append(identifier(member.path("name").asText()));
                        }
                        text.append(";\n");
                    }
                    text.append("};\n\n");
                } else {
                    text.append("typedef ").append(this.declaration(reference, name)).append(";\n\n");
                }
            }
            JsonNode extra = JsonUtils.valueToTree(this.binding.getExtensions());
            long program = unsigned(extra, "program");
            long version = unsigned(extra, "version");
            String programName = identifier(this.binding.getName());
            text.append("program ").append(programName).append(" {\n  version ").append(programName);
            text.append("_VERSION {\n");
            Set<Long> procedures = new LinkedHashSet<>();
            for (JsonNode method : this.methods) {
                long id = unsigned(method, "procedureId");
                if (!procedures.add(id)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_CONFLICTING_ONC_RPC_PROCEDURE_NUMBER, id);
                }
                text.append("    ").append(this.name(method.path("responseType").asText())).append(' ');
                text.append(identifier(method.path("name").asText())).append('(');
                text.append(this.name(method.path("requestType").asText())).append(") = ").append(id).append(";\n");
            }
            text.append("  } = ").append(version).append(";\n} = ").append(program).append(";\n");
            return text.toString();
        }

        String name(String reference) {
            JsonNode type = required(this.types, reference);
            if (StringUtils.equals(type.path("kind").asText(), "void")) {
                return "void";
            }
            String name = this.names.get(reference);
            if (name == null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_XDR_TYPE_WAS_NOT_EXPORTED, reference);
            }
            return name;
        }

        JsonNode types() {
            return this.types;
        }

        private void collect(String reference) {
            JsonNode type = required(this.types, reference);
            String kind = type.path("kind").asText();
            if (StringUtils.equals(kind, "void") || this.visited.contains(reference)) {
                return;
            }
            if (!this.collecting.add(reference)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_RECURSIVE_XDR_TYPE_REQUIRES_THE, reference);
            }
            if (!this.names.containsKey(reference)) {
                String declared = type.path("name").asText("");
                String candidate = "Type" + (this.names.size() + 1);
                boolean named = !declared.isEmpty() && !StringUtils.equals(declared, kind) && !StringUtils.equals(declared, "optional") && !StringUtils.equals(declared, "array");
                if (named) {
                    candidate = identifier(declared);
                }
                if (!this.usedNames.add(candidate)) {
                    if (named) {
                        throw new ErrorMessageException(ConsoleMessageKey.ERROR_CONFLICTING_XDR_NAMED_TYPE, candidate);
                    }
                    candidate = "Type" + (this.names.size() + 1);
                    while (!this.usedNames.add(candidate)) {
                        candidate += "_";
                    }
                }
                this.names.put(reference, candidate);
            }
            if (StringUtils.equals(kind, "opaque") && !type.has("length")) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CUSTOM_XDR_CODEC_HAS_NOT);
            }
            if (type.hasNonNull("elementType")) {
                this.collect(type.path("elementType").asText());
            }
            if (type.hasNonNull("discriminator")) {
                this.collect(type.path("discriminator").path("type").asText());
            }
            for (JsonNode member : type.path("members")) {
                this.collect(member.path("type").asText());
            }
            this.collecting.remove(reference);
            this.visited.add(reference);
            this.ordered.add(reference);
        }

        private String declaration(String reference, String name) {
            JsonNode type = required(this.types, reference);
            String kind = type.path("kind").asText();
            String bound = "";
            long length = type.path("length").asLong(-1);
            if (length >= 0) {
                bound = Long.toString(length);
            }
            boolean fixed = type.path("fixed").asBoolean();
            String bounds = "<" + bound + ">";
            if (fixed) {
                if (length < 0) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_FIXED_LENGTH_XDR_ARRAY_IS, reference);
                }
                bounds = "[" + bound + "]";
            }
            return switch (kind) {
                case "array" -> this.name(type.path("elementType").asText()) + " " + name + bounds;
                case "opaque", "string" -> kind + " " + name + bounds;
                case "optional" -> this.name(type.path("elementType").asText()) + " *" + name;
                case "unsigned_int" -> "unsigned int " + name;
                case "unsigned_hyper" -> "unsigned hyper " + name;
                case "int", "hyper", "bool", "float", "double", "quadruple" -> kind + " " + name;
                default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_XDR_TYPE_CANNOT_BE_EXPORTED, kind);
            };
        }
    }
}
