/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.java;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.description.MetFieldDescription;
import net.hasor.meteor.registry.admin.description.MetMethodDescription;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;
import net.hasor.meteor.registry.admin.description.MetTypeDescription;

/** 在生成用副本上保留文件协议的实际名称和编号，不改变提供者上报的公共声明。 */
final class JavaWireMapping {
    private final MetServiceDescription original;
    private final MetServiceDescription result;
    private final JsonNode             types;
    private final Map<String, String>  mappedNames = new HashMap<>();
    private final Set<String>          visited     = new HashSet<>();

    private JavaWireMapping(MetServiceDescription original, MetServiceDescription result, MetProtocolDescription binding) {
        this.original = original;
        this.result = result;
        this.types = JsonUtils.valueToTree(binding.getTypes());
    }

    static MetServiceDescription map(String protocol, MetServiceDescription description, MetProtocolDescription binding) {
        if (!Set.of("thrift", "thrift-http", "oncrpc", "oncrpc-udp").contains(protocol)) {
            return description;
        }
        MetServiceDescription copy;
        try {
            copy = JsonUtils.readCompatibleValue(JsonUtils.writeValueAsBytes(description), MetServiceDescription.class);
        } catch (IOException failure) {
            throw new IllegalArgumentException("无法复制 Java 服务契约", failure);
        }
        JavaWireMapping mapping = new JavaWireMapping(description, copy, binding);
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            mapping.thrift(binding);
        } else {
            mapping.oncrpc(binding);
        }
        return copy;
    }

    private void thrift(MetProtocolDescription binding) {
        Set<String> signatures = new HashSet<>();
        for (Map<String, Object> operation : binding.getMethods()) {
            JsonNode wire = JsonUtils.valueToTree(operation);
            MetMethodDescription method = this.method(text(wire, "javaSignature"), signatures);
            method.setName(text(wire, "name"));
            method.setMessage(wire.path("message").asBoolean());
            JsonNode parameters = array(wire, "parameters");
            if (parameters.size() != method.getParameters().size()) {
                throw incomplete("Thrift 参数数量不一致");
            }
            Set<Integer> positions = new HashSet<>();
            for (JsonNode parameter : parameters) {
                int index = index(parameter, "argIndex", method.getParameters().size());
                if (!positions.add(index)) {
                    throw incomplete("Thrift 参数位置重复");
                }
                MetFieldDescription target = method.getParameters().get(index);
                this.field(target, parameter, true);
                this.thriftType(target.getType(), text(parameter, "type"));
            }
            boolean returnsVoid = this.isVoid(method.getReturns().getType());
            if (!method.isMessage()) {
                JsonNode response = this.type(text(wire, "responseType"));
                JsonNode success = null;
                for (JsonNode field : array(response, "fields")) {
                    if (number(field, "fieldId") == 0) {
                        if (success != null) {
                            throw incomplete("Thrift 成功结果字段重复");
                        }
                        success = field;
                    } else {
                        String reference = this.exception(method, this.type(text(field, "type")));
                        this.thriftType(reference, text(field, "type"));
                    }
                }
                if (returnsVoid != (success == null)) {
                    throw incomplete("Thrift 返回值与成功结果字段不一致");
                }
                if (success != null) {
                    this.thriftType(method.getReturns().getType(), text(success, "type"));
                }
            } else if (!returnsVoid) {
                throw incomplete("Thrift 单向方法不能返回结果");
            }
        }
        this.complete(signatures);
    }

    private void thriftType(String reference, String wireReference) {
        MetTypeDescription declaration = this.declaration(reference);
        JsonNode wire = this.type(wireReference);
        if (!this.visited.add(reference + '\n' + wireReference)) {
            return;
        }
        String kind = declaration.getKind();
        if (StringUtils.equals(kind, "object") || StringUtils.equals(kind, "enum")) {
            this.name(reference, text(wire, "name"));
        }
        if (StringUtils.equals(kind, "enum")) {
            this.enumeration(reference, array(wire, "values"));
        } else if (StringUtils.equals(kind, "object")) {
            List<MetFieldDescription> source = this.original.getTypes().get(reference).getProperties();
            JsonNode fields = array(wire, "fields");
            if (source.size() != fields.size()) {
                throw incomplete("Thrift DTO 属性数量不一致：" + reference);
            }
            Set<Integer> positions = new HashSet<>();
            for (JsonNode field : fields) {
                int position = this.property(source, field, true);
                if (!positions.add(position)) {
                    throw incomplete("Thrift DTO 属性映射重复：" + reference);
                }
                MetFieldDescription target = declaration.getProperties().get(position);
                this.field(target, field, true);
                this.thriftType(target.getType(), text(field, "type"));
                if (StringUtils.equals("required", field.path("requirement").asText())
                        && StringUtils.equals("object", this.declaration(target.getType()).getKind())) {
                    target.getExtensions().put("exampleConstructObject", true);
                }
            }
        } else if (StringUtils.equals(kind, "array")) {
            if (wire.hasNonNull("elementType")) {
                this.thriftType(declaration.getElementType(), text(wire, "elementType"));
            }
        } else if (StringUtils.equals(kind, "collection")) {
            this.thriftType(argument(declaration, 0), text(wire, "elementType"));
        } else if (StringUtils.equals(kind, "map")) {
            this.thriftType(argument(declaration, 0), text(wire, "keyType"));
            this.thriftType(argument(declaration, 1), text(wire, "elementType"));
        }
    }

    private void oncrpc(MetProtocolDescription binding) {
        Object program = binding.getExtensions().get("program");
        if (!(program instanceof Number) || ((Number) program).longValue() <= 0) {
            throw incomplete("ONC RPC 程序编号缺失");
        }
        this.result.setContractServiceId(((Number) program).longValue());
        Set<String> signatures = new HashSet<>();
        for (Map<String, Object> operation : binding.getMethods()) {
            JsonNode wire = JsonUtils.valueToTree(operation);
            MetMethodDescription method = this.method(text(wire, "javaSignature"), signatures);
            method.setName(text(wire, "name"));
            method.setMethodId(number(wire, "procedureId"));
            JsonNode order = array(wire, "argIndexes");
            int count = method.getParameters().size();
            if (order.size() != count) {
                throw incomplete("ONC RPC 参数位置数量不一致");
            }
            String request = text(wire, "requestType");
            if (count == 1) {
                if (!order.get(0).canConvertToInt() || order.get(0).intValue() != 0) {
                    throw incomplete("ONC RPC 参数位置不正确");
                }
                this.xdrType(method.getParameters().get(0).getType(), request);
            } else if (count > 1) {
                JsonNode input = this.type(request);
                if (!StringUtils.equals("struct", text(input, "kind"))) {
                    throw incomplete("ONC RPC 多参数输入不是结构体");
                }
                JsonNode members = array(input, "members");
                if (members.size() != count) {
                    throw incomplete("ONC RPC 多参数输入字段数量不一致");
                }
                Set<Integer> positions = new HashSet<>();
                for (int wireIndex = 0; wireIndex < count; wireIndex++) {
                    JsonNode entry = order.get(wireIndex);
                    if (!entry.canConvertToInt() || entry.intValue() < 0 || entry.intValue() >= count || !positions.add(entry.intValue())) {
                        throw incomplete("ONC RPC 参数位置不是完整排列");
                    }
                    MetFieldDescription parameter = method.getParameters().get(entry.intValue());
                    JsonNode member = members.get(wireIndex);
                    this.field(parameter, member, false);
                    this.xdrType(parameter.getType(), text(member, "type"));
                }
            } else if (!StringUtils.equals("void", text(this.type(request), "kind"))) {
                throw incomplete("ONC RPC 无参数方法的输入不是 void");
            }
            this.xdrType(method.getReturns().getType(), text(wire, "responseType"));
        }
        this.complete(signatures);
    }

    private void xdrType(String reference, String wireReference) {
        MetTypeDescription declaration = this.declaration(reference);
        JsonNode wire = this.type(wireReference);
        if (!this.visited.add(reference + '\n' + wireReference)) {
            return;
        }
        String kind = text(wire, "kind");
        if (StringUtils.equals(kind, "optional")) {
            this.xdrType(reference, text(wire, "elementType"));
        } else if (StringUtils.equals(kind, "struct")) {
            if (!StringUtils.equals(declaration.getKind(), "object")) {
                throw incomplete("XDR 结构体没有对应 Java DTO：" + reference);
            }
            this.name(reference, text(wire, "name"));
            List<MetFieldDescription> source = this.original.getTypes().get(reference).getProperties();
            Set<Integer> positions = new HashSet<>();
            for (JsonNode member : array(wire, "members")) {
                int position = this.property(source, member, false);
                if (!positions.add(position)) {
                    throw incomplete("XDR DTO 属性映射重复：" + reference);
                }
                MetFieldDescription property = declaration.getProperties().get(position);
                this.field(property, member, false);
                this.xdrType(property.getType(), text(member, "type"));
                if (StringUtils.equals("object", this.declaration(property.getType()).getKind())
                        && StringUtils.equals("struct", this.type(text(member, "type")).path("kind").asText())) {
                    property.getExtensions().put("exampleConstructObject", true);
                }
            }
        } else if (StringUtils.equals(kind, "enum")) {
            if (!StringUtils.equals(declaration.getKind(), "enum")) {
                throw incomplete("XDR 枚举没有对应 Java 枚举：" + reference);
            }
            this.name(reference, text(wire, "name"));
            this.enumeration(reference, array(wire, "values"));
        } else if (StringUtils.equals(kind, "array")) {
            String element;
            if (StringUtils.equals(declaration.getKind(), "array")) {
                element = declaration.getElementType();
            } else if (StringUtils.equals(declaration.getKind(), "collection")) {
                element = argument(declaration, 0);
            } else {
                throw incomplete("XDR 数组没有对应 Java 数组或 List：" + reference);
            }
            if (wire.path("fixed").asBoolean()) {
                this.fixedArray(declaration, wire);
            }
            this.xdrType(element, text(wire, "elementType"));
        } else if (StringUtils.equals(kind, "union")) {
            throw incomplete("XDR union 需要业务判别值，当前不能生成可靠的 Java 样本");
        } else if (StringUtils.equals(kind, "opaque")) {
            if (!StringUtils.equals(declaration.getJavaType(), "byte[]") || !wire.has("length")) {
                throw incomplete("不透明 XDR 编码没有可重建的 Java 契约：" + reference);
            }
            if (wire.path("fixed").asBoolean()) {
                this.fixedArray(declaration, wire);
            }
        } else if (StringUtils.equals(kind, "quadruple")) {
            if (!StringUtils.equals(declaration.getJavaType(), "byte[]")) {
                throw incomplete("XDR quadruple 没有对应 Java byte[]");
            }
            this.arrayLength(declaration, 16);
        }
    }

    private void fixedArray(MetTypeDescription declaration, JsonNode wire) {
        long length = number(wire, "length");
        if (length < 0 || length > 65536) {
            throw incomplete("XDR 固定数组长度无法生成为可运行样本");
        }
        if (length > 0 && !StringUtils.equals(declaration.getJavaType(), "byte[]")) {
            if (!StringUtils.equals(declaration.getKind(), "array")) {
                throw incomplete("XDR 固定 List 的业务样本无法从类型声明推导");
            }
            String element = this.declaration(declaration.getElementType()).getJavaType();
            if (!Set.of("byte", "short", "int", "long", "boolean", "char", "float", "double").contains(element)) {
                throw incomplete("XDR 固定引用数组的业务样本无法从类型声明推导");
            }
        }
        this.arrayLength(declaration, (int) length);
    }

    private void arrayLength(MetTypeDescription declaration, int length) {
        Object previous = declaration.getExtensions().put("exampleArrayLength", length);
        if (previous instanceof Number && ((Number) previous).longValue() != length) {
            throw incomplete("同一 Java 类型对应不同 XDR 固定数组长度");
        }
    }

    private void enumeration(String reference, JsonNode values) {
        List<MetFieldDescription> originalValues = this.original.getTypes().get(reference).getValues();
        List<MetFieldDescription> targetValues = this.result.getTypes().get(reference).getValues();
        if (originalValues.size() != values.size()) {
            throw incomplete("枚举值数量不一致：" + reference);
        }
        Set<Integer> positions = new HashSet<>();
        for (JsonNode value : values) {
            // 文件模式按枚举符号定位；编号来自当前线上契约，不能由 Java ordinal 推导。
            int position = this.property(originalValues, value, false);
            if (!positions.add(position)) {
                throw incomplete("枚举值映射重复：" + reference);
            }
            this.field(targetValues.get(position), value, true);
        }
    }

    private int property(List<MetFieldDescription> properties, JsonNode wire, boolean numbered) {
        String name = text(wire, "name");
        long id = numbered ? number(wire, "fieldId") : 0;
        int position = -1;
        for (int index = 0; index < properties.size(); index++) {
            MetFieldDescription property = properties.get(index);
            boolean matches;
            if (numbered && property.getFieldId() != null && property.getFieldId() != 0) {
                matches = property.getFieldId() == id;
            } else {
                matches = StringUtils.equals(property.getName(), name);
            }
            if (matches) {
                if (position >= 0) {
                    throw incomplete("线上字段无法唯一映射 Java 属性：" + name);
                }
                position = index;
            }
        }
        if (position < 0) {
            throw incomplete("线上字段没有可确认的 Java 属性映射：" + name);
        }
        return position;
    }

    private void field(MetFieldDescription target, JsonNode wire, boolean numbered) {
        target.setName(text(wire, "name"));
        if (numbered) {
            target.setFieldId(number(wire, "fieldId"));
        }
    }

    private void name(String reference, String name) {
        String previous = this.mappedNames.putIfAbsent(reference, name);
        if (previous != null && !StringUtils.equals(previous, name)) {
            throw incomplete("同一 Java DTO 对应多个不同线上类型名称：" + reference);
        }
        this.declaration(reference).setName(name);
    }

    private String exception(MetMethodDescription method, JsonNode wire) {
        String name = text(wire, "name");
        String selected = null;
        for (String exception : method.getExceptions()) {
            MetTypeDescription type = this.original.getTypes().get(exception);
            if (type != null && StringUtils.equals(type.getName(), name)) {
                if (selected != null) {
                    throw incomplete("Thrift 异常类型名称不唯一：" + name);
                }
                selected = exception;
            }
        }
        if (selected == null) {
            throw incomplete("无法还原 Thrift 异常类型：" + name);
        }
        return selected;
    }

    private MetMethodDescription method(String signature, Set<String> signatures) {
        if (!signatures.add(signature)) {
            throw incomplete("协议方法签名重复：" + signature);
        }
        MetMethodDescription selected = null;
        for (MetMethodDescription method : this.result.getMethods()) {
            if (StringUtils.equals(method.getJavaSignature(), signature)) {
                if (selected != null) {
                    throw incomplete("Java 方法签名不唯一：" + signature);
                }
                selected = method;
            }
        }
        if (selected == null) {
            throw incomplete("线上方法没有公共 Java 声明：" + signature);
        }
        return selected;
    }

    private void complete(Set<String> signatures) {
        if (signatures.size() != this.result.getMethods().size()) {
            throw incomplete("线上方法没有覆盖完整 Java 接口");
        }
    }

    private MetTypeDescription declaration(String reference) {
        MetTypeDescription value = this.result.getTypes().get(reference);
        if (value == null) {
            throw incomplete("缺少公共 Java 类型：" + reference);
        }
        return value;
    }

    private boolean isVoid(String reference) {
        String type = this.declaration(reference).getJavaType();
        return StringUtils.equals(type, "void") || StringUtils.equals(type, "java.lang.Void");
    }

    private JsonNode type(String reference) {
        JsonNode type = this.types.get(reference);
        if (type == null || !type.isObject()) {
            throw incomplete("缺少线上类型：" + reference);
        }
        return type;
    }

    private static String argument(MetTypeDescription type, int index) {
        if (type.getArguments().size() <= index) {
            throw incomplete("Java 容器类型参数不完整");
        }
        return type.getArguments().get(index);
    }

    private static int index(JsonNode node, String field, int count) {
        long value = number(node, field);
        if (value < 0 || value >= count) {
            throw incomplete("Java 参数位置超出范围");
        }
        return (int) value;
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw incomplete("缺少线上编号：" + field);
        }
        return value.longValue();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw incomplete("缺少线上名称或引用：" + field);
        }
        return value.textValue();
    }

    private static JsonNode array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw incomplete("缺少线上列表：" + field);
        }
        return value;
    }

    private static IllegalArgumentException incomplete(String detail) {
        return new IllegalArgumentException("无法从当前线上契约生成可靠的 Java 示例：" + detail);
    }
}
