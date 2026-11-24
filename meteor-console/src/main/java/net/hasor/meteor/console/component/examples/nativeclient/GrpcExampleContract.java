/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.util.*;
import com.google.protobuf.Descriptors.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.utils.JsonUtils;

/** Renders the selected RPCs and their reachable Protobuf declarations. */
final class GrpcExampleContract {
    private final ServiceDescriptor                        service;
    private final List<MethodDescriptor>                   methods;
    private final Set<Descriptor>                          messages = new LinkedHashSet<>();
    private final Set<EnumDescriptor>                      enums    = new LinkedHashSet<>();
    private final Map<FileDescriptor, String>              files    = new LinkedHashMap<>();
    private final Map<FileDescriptor, Set<FileDescriptor>> imports  = new LinkedHashMap<>();

    GrpcExampleContract(ServiceDescriptor service, List<MethodDescriptor> methods) {
        this.service = service;
        this.methods = methods;
        FileDescriptor file = service.getFile();
        this.files.put(file, "service.proto");
        for (MethodDescriptor method : methods) {
            this.include(file, method.getInputType());
            this.include(file, method.getOutputType());
        }
    }

    String markdown() throws Exception {
        List<String> blocks = new ArrayList<>();
        for (var entry : this.files.entrySet()) {
            FileDescriptor file = entry.getKey();
            if (isStandard(file)) {
                continue;
            }
            String syntax = StringUtils.defaultIfBlank(file.toProto().getSyntax(), "proto2");
            StringBuilder out = new StringBuilder();
            if (file.toProto().hasEdition()) {
                out.append("edition = \"").append(file.toProto().getEdition().name().substring("EDITION_".length())).append("\";\n");
            } else {
                out.append("syntax = \"").append(syntax).append("\";\n");
            }
            if (!file.getPackage().isEmpty()) {
                out.append("\npackage ").append(file.getPackage()).append(";\n");
            }
            Set<FileDescriptor> dependencies = this.imports.getOrDefault(file, Set.of());
            if (!dependencies.isEmpty()) {
                out.append('\n');
                for (FileDescriptor dependency : dependencies) {
                    out.append("import \"").append(this.files.get(dependency)).append("\";\n");
                }
            }
            if (file == this.service.getFile()) {
                out.append("\nservice ").append(this.service.getName()).append(" {\n");
                for (MethodDescriptor method : this.methods) {
                    String input = this.rpcType(method.getInputType(), method.isClientStreaming());
                    String output = this.rpcType(method.getOutputType(), method.isServerStreaming());
                    out.append("  rpc ").append(method.getName()).append('(').append(input).append(") returns (").append(output).append(");\n");
                }
                out.append("}\n");
            }
            for (EnumDescriptor enumeration : file.getEnumTypes()) {
                this.enumeration(out, enumeration, "");
            }
            for (Descriptor message : file.getMessageTypes()) {
                this.message(out, message, "");
            }
            blocks.add(ExampleTemplates.codeBlock("protobuf " + entry.getValue(), out.toString()));
        }
        return String.join("\n\n", blocks);
    }

    private void include(FileDescriptor from, GenericDescriptor declaration) {
        FileDescriptor file = declaration.getFile();
        if (file != from) {
            this.imports.computeIfAbsent(from, ignored -> new LinkedHashSet<>()).add(file);
        }
        boolean standard = isStandard(file);
        this.files.computeIfAbsent(file, ignored -> standard ? file.getName() : "types-" + this.files.size() + ".proto");
        if (standard) {
            return;
        }
        if (declaration instanceof EnumDescriptor enumeration) {
            this.enums.add(enumeration);
        } else if (declaration instanceof Descriptor message && this.messages.add(message)) {
            for (FieldDescriptor field : message.getFields()) {
                if (field.isMapField()) {
                    this.includeField(file, field.getMessageType().findFieldByNumber(2));
                } else {
                    this.includeField(file, field);
                }
            }
        }
    }

    private void includeField(FileDescriptor from, FieldDescriptor field) {
        switch (field.getJavaType()) {
            case MESSAGE -> this.include(from, field.getMessageType());
            case ENUM -> this.include(from, field.getEnumType());
            default -> {
            }
        }
    }

    private boolean containsDeclaration(Descriptor message) {
        return this.messages.contains(message) || message.getEnumTypes().stream().anyMatch(this.enums::contains) || message.getNestedTypes().stream().anyMatch(this::containsDeclaration);
    }

    private void message(StringBuilder out, Descriptor message, String indent) throws Exception {
        if (!this.containsDeclaration(message)) {
            return;
        }
        out.append('\n').append(indent).append("message ").append(message.getName()).append(" {\n");
        String inside = indent + "  ";
        if (this.messages.contains(message)) {
            for (FieldDescriptor field : message.getFields()) {
                if (field.getRealContainingOneof() == null) {
                    field(out, field, inside);
                }
            }
            for (OneofDescriptor oneof : message.getRealOneofs()) {
                out.append(inside).append("oneof ").append(oneof.getName()).append(" {\n");
                for (FieldDescriptor field : oneof.getFields()) {
                    field(out, field, inside + "  ");
                }
                out.append(inside).append("}\n");
            }
        }
        for (EnumDescriptor enumeration : message.getEnumTypes()) {
            this.enumeration(out, enumeration, inside);
        }
        for (Descriptor nested : message.getNestedTypes()) {
            this.message(out, nested, inside);
        }
        out.append(indent).append("}\n");
    }

    private void enumeration(StringBuilder out, EnumDescriptor enumeration, String indent) {
        if (!this.enums.contains(enumeration)) {
            return;
        }
        out.append('\n').append(indent).append("enum ").append(enumeration.getName()).append(" {\n");
        if (enumeration.getOptions().getAllowAlias()) {
            out.append(indent).append("  option allow_alias = true;\n");
        }
        for (EnumValueDescriptor value : enumeration.getValues()) {
            out.append(indent).append("  ").append(value.getName()).append(" = ").append(value.getNumber()).append(";\n");
        }
        out.append(indent).append("}\n");
    }

    private static void field(StringBuilder out, FieldDescriptor field, String indent) throws Exception {
        out.append(indent);
        var proto = field.toProto();
        if (field.isMapField()) {
            var entry = field.getMessageType();
            out.append("map<").append(type(entry.findFieldByNumber(1))).append(", ").append(type(entry.findFieldByNumber(2))).append('>');
        } else {
            if (field.isRepeated()) {
                out.append("repeated ");
            } else if (field.isRequired()) {
                out.append("required ");
            } else if (field.getRealContainingOneof() == null && !field.getFile().toProto().hasEdition() && (proto.getProto3Optional() || !StringUtils.equals("proto3", field.getFile().toProto().getSyntax()))) {
                out.append("optional ");
            }
            out.append(type(field));
        }
        out.append(' ').append(field.getName()).append(" = ").append(field.getNumber());
        List<String> options = new ArrayList<>();
        if (!StringUtils.equals(defaultJsonName(field.getName()), field.getJsonName())) {
            options.add("json_name = " + JsonUtils.writeValueAsString(field.getJsonName()));
        }
        if (proto.hasDefaultValue()) {
            String value = proto.getDefaultValue();
            if (field.getType() == FieldDescriptor.Type.STRING) {
                value = JsonUtils.writeValueAsString(value);
            } else if (field.getType() == FieldDescriptor.Type.BYTES) {
                value = "\"" + value + "\"";
            }
            options.add("default = " + value);
        }
        if (field.getOptions().hasPacked()) {
            options.add("packed = " + field.getOptions().getPacked());
        }
        if (!options.isEmpty()) {
            out.append(" [").append(String.join(", ", options)).append(']');
        }
        out.append(";\n");
    }

    private static String type(FieldDescriptor field) {
        return switch (field.getJavaType()) {
            case MESSAGE -> "." + field.getMessageType().getFullName();
            case ENUM -> "." + field.getEnumType().getFullName();
            default -> field.getType().name().toLowerCase(Locale.ROOT);
        };
    }

    private String rpcType(Descriptor type, boolean streaming) {
        String name = "." + type.getFullName();
        if (type.getFile() == this.service.getFile() && type.getContainingType() == null) {
            name = type.getName();
        }
        return streaming ? "stream " + name : name;
    }

    private static String defaultJsonName(String fieldName) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char value : fieldName.toCharArray()) {
            if (value == '_') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(value) : value);
                upper = false;
            }
        }
        return name.toString();
    }

    private static boolean isStandard(FileDescriptor file) {
        return Set.of("any", "api", "descriptor", "duration", "empty", "field_mask", "source_context", "struct", "timestamp", "type", "wrappers").stream().anyMatch(name -> StringUtils.equals(file.getName(), "google/protobuf/" + name + ".proto"));
    }
}
