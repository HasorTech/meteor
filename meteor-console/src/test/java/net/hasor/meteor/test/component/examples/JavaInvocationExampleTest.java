/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.component.examples;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleRegistry;
import net.hasor.meteor.console.component.examples.java.JavaExampleGenerator;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.MetServiceEndpoint;
import net.hasor.meteor.registry.admin.MetServiceInfo;
import net.hasor.meteor.registry.admin.description.MetFieldDescription;
import net.hasor.meteor.registry.admin.description.MetMethodDescription;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;
import net.hasor.meteor.registry.admin.description.MetTypeDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 通过公开文档生成入口验证业务 API 引用、真实 wire 契约与快照边界。 */
class JavaInvocationExampleTest {
    private static final String SERVICE = "example.contract.OrderService";
    private static final String ORDER = "example.contract.Order";
    private static final String STATE = "example.contract.State";
    private static final String SIGNATURE = "exchange(" + ORDER + ")";

    @ParameterizedTest
    @ValueSource(strings = { "jdbc", "nacos", "zookeeper" })
    void registryCallerHasItsOwnDocument(String type) {
        // 从目标注册中心生成独立的发现调用。
        ExampleTarget target = target("thrift", declaration(), thriftContract());
        ExampleRegistry registry = new ExampleRegistry();
        registry.setType(type);
        registry.setAddress("registry.example.test");

        List<ExampleDocument> documents = new JavaExampleGenerator().generate(target, registry, target.getAddress());
        String markdown = documents.stream().filter(item -> item.getRole().equals("registry")).findFirst().orElseThrow().getMarkdown();
        assertTrue(markdown.contains("net.hasor:meteor-registry:"), markdown);
        assertFalse(markdown.contains("meteor-registry-" + type + ":"), markdown);
        assertTrue(markdown.contains("import net.hasor.meteor.registry.RegistryConfiguration;"), markdown);
        assertFalse(markdown.contains("import net.hasor.meteor.registry." + type + "."), markdown);
        String setup = """
                        // See the documentation for registry configuration.
                        RegistryConfiguration registry = ...;
                        configuration.addExtension(registry);
                """;
        assertTrue(markdown.contains(setup), markdown);
        assertFalse(markdown.contains("registry.example.test"), markdown);
        assertFalse(markdown.contains("commons-dbcp2"), markdown);
        assertFalse(markdown.contains("com.h2database:h2"), markdown);
        assertTrue(markdown.contains("context.getRsfClient().getRemote(binding)"), markdown);
        assertFalse(documents.get(0).getMarkdown().contains("configuration.addExtension(registry)"));
    }

    @Test
    void providerPublishesAnAnnotatedInterfaceFromAnOlderSnapshot() {
        // 用不同的监听和发布端口验证示例应使用实例本地的端口。
        ExampleTarget target = target("thrift", declaration(), thriftContract());
        target.getEndpoint().setListenAddress("thrift://127.0.0.1:19512/default");

        List<ExampleDocument> documents = new JavaExampleGenerator().generate(target, null, target.getAddress());

        String source = documents.stream().filter(item -> item.getRole().equals("provider")).findFirst().orElseThrow().getMarkdown();
        assertTrue(source.contains(".bind(\"0.0.0.0\", 19512)"), source);
        assertTrue(source.contains("context.publisher().rsfService(OrderService.class, new OrderServiceImpl())"), source);
        assertTrue(source.contains(".register();"), source);
        assertTrue(source.indexOf(".register();") < source.indexOf("context.start();"), source);
        assertTrue(source.contains("@MetService(group = \"example\", name = \"OrderService\""), source);
        assertTrue(source.contains("public interface OrderService {"), source);
        assertTrue(source.contains("@MetMethod(name = \"wire_exchange\")"), source);
        assertTrue(source.contains("@MetParam(id = 5, name = \"arg0\")"), source);
        assertTrue(source.contains("\nclass OrderServiceImpl implements OrderService {\n    // ...\n}"), source);
        for (String removed : List.of(".group(", ".name(", ".version(", ".serialize(")) {
            assertFalse(source.contains(removed), source);
        }
    }

    @Test
    void connectDocumentsOmitSettingsAlreadyProvidedByTheInterfaceAndProtocol() {
        // 普通 Connect 示例沿用框架的默认分组、版本及 Protobuf。
        MetServiceDescription description = declaration();
        MetProtocolDescription contract = new MetProtocolDescription();
        contract.setName("OrderService");
        contract.setSerializeType("Protobuf");
        ExampleTarget target = target("connect", description, contract);
        target.getService().setGroup("default");
        target.getEndpoint().setTransport("http");
        target.setAddress("connect://127.0.0.1:19200/");
        target.getEndpoint().setListenAddress(target.getAddress());

        // 连接器足以选择 Connect；发布和调用都沿用已知的默认配置。
        List<ExampleDocument> documents = new JavaExampleGenerator().generate(target, null, target.getAddress());
        for (ExampleDocument document : documents) {
            String code = document.getMarkdown();
            assertTrue(code.contains(".protocol(\"connect\");"), code);
            for (String removed : List.of("setDefaultProtocol", ".addressScheme(", ".contextPath(", ".group(",
                    ".name(", ".version(", ".aliasName(", ".serialize(")) {
                assertFalse(code.contains(removed), code);
            }
        }
    }

    @Test
    void thriftDocumentKeepsNonDefaultTransportAndUsesThePublishedValuesInTheAnnotation() {
        // 新接口的注解取实际发布配置，旧接口的注解不参与生成。
        MetServiceDescription description = declaration();
        description.getExtensions().put("javaDeclaration",
                Map.of("group", "another-group", "name", "AnotherService", "version", "2.0.0", "serializeType", "compact"));
        MetProtocolDescription contract = thriftContract();
        contract.setSerializeType("Binary");
        contract.getExtensions().put("framed", false);
        contract.getExtensions().put("multiplexed", true);
        ExampleTarget target = target("thrift", description, contract);
        target.getEndpoint().setScheme("orders");

        String code = new JavaExampleGenerator().generate(target, null, target.getAddress()).get(0).getMarkdown();

        assertTrue(code.contains(".addressScheme(\"orders\")"), code);
        assertTrue(code.contains(".option(\"framed\", false)"), code);
        assertTrue(code.contains(".option(\"multiplexed\", true)"), code);
        assertTrue(code.contains("@MetService(group = \"example\", name = \"OrderService\", serializeType = \"Binary\")"), code);
        for (String removed : List.of(".group(", ".name(", ".version(", ".serialize(")) {
            assertFalse(code.contains(removed), code);
        }
    }

    @Test
    void thriftDocumentKeepsWireAliasesAndDeclaresAnAnnotatedInterface() {
        // 原服务通过字段编号绑定 IDL 别名，Java 接口方法名保持 exchange。
        ExampleTarget target = target("thrift", declaration(), thriftContract());

        ExampleDocument document = new JavaExampleGenerator() //
                .generate(target, null, target.getAddress()) //
                .get(0);

        // IDL 保留实际上报的线上名称，Java 调用使用独立接口的原方法名。
        String markdown = document.getMarkdown();
        assertTrue(markdown.contains("rsfService(OrderService.class)"), markdown);
        assertTrue(markdown.contains(".exchange("), markdown);
        assertTrue(markdown.contains("service WireOrderService"), markdown);
        assertTrue(markdown.contains("WireOrder wire_exchange(5: WireOrder input_order)"), markdown);
        assertTrue(markdown.contains("1: string payload_text"), markdown);
        assertTrue(markdown.contains("READY = 3"), markdown);
        assertTrue(markdown.contains("\npublic interface OrderService"), markdown);
    }

    @Test
    void xdrDocumentKeepsTheActualTypeNamesWithAnAnnotatedInterface() {
        // XDR 文件按有效属性名和方法编号绑定，未显式声明的类型名允许与 IDL 不同。
        ExampleTarget target = target("oncrpc", xdrDeclaration(), xdrContract());

        ExampleDocument document = new JavaExampleGenerator() //
                .generate(target, null, target.getAddress()) //
                .get(0);

        // 内嵌 XDR 契约保留实际类型、枚举与过程编号。
        String markdown = document.getMarkdown();
        assertTrue(markdown.contains("rsfService(OrderService.class)"), markdown);
        assertTrue(markdown.contains(".exchange("), markdown);
        assertTrue(markdown.contains("struct xdr_order"), markdown);
        assertTrue(markdown.contains("XDR_READY = 3"), markdown);
        assertTrue(markdown.contains("WIRE_EXCHANGE(xdr_order) = 9"), markdown);
        assertTrue(markdown.contains("\npublic interface OrderService"), markdown);
    }

    @Test
    void generationDoesNotModifyTheProviderSnapshotOrItsProtocolGraph() throws Exception {
        // 目标快照和实际端点协议图均由调用方持有，生成器只能使用副本。
        MetServiceDescription declaration = declaration();
        MetProtocolDescription contract = thriftContract();
        ExampleTarget target = target("thrift", declaration, contract);
        String originalDescription = JsonUtils.writeValueAsString(declaration);
        String originalContract = JsonUtils.writeValueAsString(contract);

        ExampleDocument document = new JavaExampleGenerator() //
                .generate(target, null, target.getAddress()) //
                .get(0);

        // 文档内嵌契约已采用 wire 名称，原快照仍逐字保持原值。
        assertTrue(document.getMarkdown().contains("payload_text"));
        assertEquals(originalDescription, JsonUtils.writeValueAsString(declaration));
        assertEquals(originalContract, JsonUtils.writeValueAsString(contract));
    }

    @Test
    void thriftRejectsAnUnknownJavaMethodSignature() {
        // wire 操作没有对应 Java 方法时，不能按名字猜测绑定。
        MetProtocolDescription contract = thriftContract();
        contract.getMethods().get(0).put("javaSignature", "different(" + ORDER + ")");
        ExampleTarget target = target("thrift", declaration(), contract);

        assertThrows(IllegalArgumentException.class, () -> {
            new JavaExampleGenerator().generate(target, null, target.getAddress());
        });
    }

    @Test
    void thriftRejectsAFieldWhoseWireIdCannotBeMatched() {
        // 公共属性有明确编号，不能按列表位置替代未知的 wire 编号。
        MetProtocolDescription contract = thriftContract();
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(thriftField(99, "payload_text", "wire.text"));
        fields.add(thriftField(2, "wire_state", "wire.state"));
        contract.getTypes().put("wire.order", thriftType("WireOrder", 12, fields, List.of()));
        ExampleTarget target = target("thrift", declaration(), contract);

        assertThrows(IllegalArgumentException.class, () -> {
            new JavaExampleGenerator().generate(target, null, target.getAddress());
        });
    }

    @Test
    void xdrRejectsAnOpaqueCustomCodecShape() {
        // 自定义 codec 没有结构图，不能生成伪造的可运行 Java 文件。
        MetProtocolDescription contract = xdrContract();
        contract.getTypes().put("wire.order", Map.of("kind", "opaque"));
        ExampleTarget target = target("oncrpc", xdrDeclaration(), contract);

        assertThrows(IllegalArgumentException.class, () -> {
            new JavaExampleGenerator().generate(target, null, target.getAddress());
        });
    }

    @Test
    void xdrRejectsAnUnknownWireStructMember() {
        // IDL 成员找不到同名 Java 属性时，不能按编号或位置猜测。
        MetProtocolDescription contract = xdrContract();
        List<Map<String, Object>> members = new ArrayList<>();
        members.add(xdrMember("unknown_text", "wire.text"));
        members.add(xdrMember("xdr_state", "wire.state"));
        contract.getTypes().put("wire.order", xdrType("xdr_order", "struct", members, List.of()));
        ExampleTarget target = target("oncrpc", xdrDeclaration(), contract);

        assertThrows(IllegalArgumentException.class, () -> {
            new JavaExampleGenerator().generate(target, null, target.getAddress());
        });
    }

    @Test
    void xdrFixedOpaqueSamplesUseTheDeclaredByteLength() {
        // 固定 opaque 必须生成指定长度的 byte[]，零长样本无法通过 XDR 编码。
        MetServiceDescription declaration = xdrDeclaration();
        MetMethodDescription method = declaration.getMethods().get(0);
        method.setJavaSignature("exchange(byte[])");
        method.getParameters().get(0).setType("byte[]");
        method.getReturns().setType("byte[]");
        declaration.getTypes().clear();
        MetTypeDescription bytes = type("byte[]", "byte[]", "array");
        bytes.setElementType("byte");
        declaration.getTypes().put("byte[]", bytes);
        declaration.getTypes().put("byte", type("byte", "byte", "scalar"));
        MetProtocolDescription contract = xdrContract();
        Map<String, Object> operation = contract.getMethods().get(0);
        operation.put("javaSignature", "exchange(byte[])");
        operation.put("requestType", "wire.bytes");
        operation.put("responseType", "wire.bytes");
        contract.getTypes().clear();
        contract.getTypes().put("wire.bytes", Map.of("name", "fixed_bytes", "kind", "opaque", "fixed", true, "length", 4));
        ExampleTarget target = target("oncrpc", declaration, contract);

        ExampleDocument document = new JavaExampleGenerator() //
                .generate(target, null, target.getAddress()) //
                .get(0);

        // 主入口与配套 IDL 使用相同固定长度，同时不向原始类型图写入生成状态。
        String main = document.getMarkdown();
        assertTrue(main.contains("new byte[4]"));
        assertTrue(document.getMarkdown().contains("fixed_bytes[4]"));
        assertFalse(declaration.getTypes().get("byte[]").getExtensions().containsKey("exampleArrayLength"));
    }

    @Test
    void missingAddressUsesAnExplicitPlaceholderInsteadOfTheProviderSnapshot() {
        // 服务快照只描述契约；用户未传地址时不以当前提供者地址偷偷填充示例。
        ExampleTarget target = target("thrift", declaration(), thriftContract());

        List<ExampleDocument> documents = new JavaExampleGenerator().generate(target, null, null);

        String direct = documents.get(0).getMarkdown();
        assertTrue(direct.contains("getRsfClient(\"SERVICE_ADDRESS\")"), direct);
        assertFalse(direct.contains(target.getAddress()), direct);
        assertEquals(List.of("direct", "provider"), documents.stream().map(ExampleDocument::getRole).toList());
    }

    @ParameterizedTest(name = "构造证明 {0} 时使用显式业务参数")
    @ValueSource(strings = { "missing", "false" })
    void objectWithoutAProvenDefaultConstructorUsesTypedNull(String constructor) {
        // 旧元数据或明确不可构造的 DTO 都不能生成不可编译的 new 表达式。
        MetServiceDescription declaration = declaration();
        MetTypeDescription order = declaration.getTypes().get(ORDER);
        order.getExtensions().remove("javaDefaultConstructor");
        if (StringUtils.equals(constructor, "false")) {
            order.getExtensions().put("javaDefaultConstructor", false);
        }
        ExampleTarget target = target("thrift", declaration, thriftContract());

        String markdown = new JavaExampleGenerator() //
                .generate(target, null, target.getAddress()) //
                .get(0).getMarkdown();

        assertFalse(markdown.contains("new " + ORDER + "("), markdown);
        assertTrue(markdown.contains("(" + ORDER + ") null /* 按业务构造对象 */"), markdown);
    }

    @Test
    void legacyNestedServiceWithoutASourceNameIsRejected() {
        // 二进制名称里的美元符号不能证明接口嵌套关系，不擅自猜源码名称。
        MetServiceDescription declaration = declaration();
        declaration.setJavaType("example.contract.Outer$OrderService");
        ExampleTarget target = target("thrift", declaration, thriftContract());

        assertThrows(IllegalArgumentException.class, () -> {
            new JavaExampleGenerator().generate(target, null, target.getAddress());
        });
    }

    @Test
    void legacyNestedDtoWithoutASourceNameIsRejected() {
        // DTO 类型同样需要明确的 canonicalName，不能把美元符号机械替换为点。
        MetServiceDescription declaration = declaration();
        declaration.getTypes().get(ORDER).setJavaType("example.contract.Outer$Order");
        ExampleTarget target = target("thrift", declaration, thriftContract());

        assertThrows(IllegalArgumentException.class, () -> {
            new JavaExampleGenerator().generate(target, null, target.getAddress());
        });
    }

    @Test
    void topLevelDollarNamesKeepTheirAuthoritativeCanonicalIdentity() {
        // canonicalName 已明确证明美元符号属于合法顶层名称，源码应原样保留。
        MetServiceDescription declaration = declaration();
        String service = "example.contract.Dollar$Service";
        String order = "example.contract.Dollar$Order";
        declaration.setJavaType(service);
        declaration.getExtensions().put("javaSourceType", service);
        declaration.getTypes().get(ORDER).setJavaType(order);
        declaration.getTypes().get(ORDER).getExtensions().put("javaSourceType", order);
        ExampleTarget target = target("thrift", declaration, thriftContract());

        String markdown = new JavaExampleGenerator() //
                .generate(target, null, target.getAddress()) //
                .get(0).getMarkdown();

        assertTrue(markdown.contains("Dollar$Service.class"), markdown);
        assertTrue(markdown.contains("new " + order + "()"), markdown);
        assertFalse(markdown.contains("example.contract.Dollar.Service"), markdown);
        assertFalse(markdown.contains("example.contract.Dollar.Order"), markdown);
    }

    private static ExampleTarget target(String protocol, MetServiceDescription description, MetProtocolDescription contract) {
        String address = protocol + "://127.0.0.1:19200/default";
        MetServiceEndpoint endpoint = new MetServiceEndpoint();

        endpoint.setTransport("tcp");
        endpoint.setProtocolName(protocol);
        endpoint.setScheme(protocol);

        endpoint.setListenAddress(address);
        endpoint.setPublishedAddress(address);
        MetServiceInfo service = new MetServiceInfo();
        service.setGroup("example");
        service.setName("OrderService");
        service.setVersion("1.0.0");
        service.setServiceId("[example]OrderService-1.0.0");
        endpoint.setDescription(contract);
        ExampleTarget target = new ExampleTarget();
        target.setId("sample");
        target.setServiceId(service.getServiceId());
        target.setApplication("example-provider");
        target.setInstanceId("example-instance");
        target.setUnit("default");
        target.setAddress(address);
        target.setService(service);
        target.setEndpoint(endpoint);
        target.setDescription(description);
        return target;
    }

    private static MetServiceDescription declaration() {
        MetServiceDescription declaration = new MetServiceDescription();
        declaration.setJavaType(SERVICE);
        MetMethodDescription method = new MetMethodDescription();
        method.setJavaName("exchange");
        method.setJavaSignature(SIGNATURE);
        method.setName("wire_exchange");
        MetFieldDescription parameter = field("arg0", null, 5L, ORDER);
        parameter.setArgIndex(0);
        method.getParameters().add(parameter);
        method.setReturns(field("result", null, null, ORDER));
        declaration.getMethods().add(method);

        MetTypeDescription order = type("Order", ORDER, "object");
        // 属性顺序不代表编号顺序，映射时必须保留 javaName 并使用稳定编号。
        order.getExtensions().put("javaDefaultConstructor", true);
        order.getProperties().add(field("state", "state", 2L, STATE));
        order.getProperties().add(field("text", "text", 1L, "java.lang.String"));
        declaration.getTypes().put(ORDER, order);
        MetTypeDescription state = type("State", STATE, "enum");
        // 数值与 Java 声明顺序分别保存，wire 枚举符号可与 Java 常量名不同。
        state.getValues().add(field("READY", "FIRST", 3L, null));
        state.getValues().add(field("FINISHED", "SECOND", 7L, null));
        declaration.getTypes().put(STATE, state);
        declaration.getTypes().put("java.lang.String", type("String", "java.lang.String", "scalar"));
        return declaration;
    }

    private static MetServiceDescription xdrDeclaration() {
        MetServiceDescription declaration = declaration();
        declaration.getMethods().get(0).setName("exchange");
        declaration.getMethods().get(0).setMethodId(9L);
        MetTypeDescription order = declaration.getTypes().get(ORDER);
        property(order, "text").setName("xdr_text");
        property(order, "state").setName("xdr_state");
        MetTypeDescription state = declaration.getTypes().get(STATE);
        propertyValue(state, "FIRST").setName("XDR_READY");
        propertyValue(state, "SECOND").setName("XDR_FINISHED");
        return declaration;
    }

    private static MetProtocolDescription thriftContract() {
        MetProtocolDescription contract = new MetProtocolDescription();
        contract.setName("WireOrderService");
        contract.getExtensions().put("framed", true);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("name", "wire_exchange");
        operation.put("javaSignature", SIGNATURE);
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> argument = new LinkedHashMap<>(thriftField(5, "input_order", "wire.order"));
        argument.put("argIndex", 0);
        parameters.add(argument);
        operation.put("parameters", parameters);
        operation.put("responseType", "wire.result");
        operation.put("message", false);
        contract.getMethods().add(operation);

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(thriftField(1, "payload_text", "wire.text"));
        fields.add(thriftField(2, "wire_state", "wire.state"));
        contract.getTypes().put("wire.order", thriftType("WireOrder", 12, fields, List.of()));
        List<Map<String, Object>> constants = new ArrayList<>();
        constants.add(Map.of("fieldId", 7, "name", "FINISHED"));
        constants.add(Map.of("fieldId", 3, "name", "READY"));
        contract.getTypes().put("wire.state", thriftType("WireState", 8, List.of(), constants));
        contract.getTypes().put("wire.text", thriftType("string", 11, List.of(), List.of()));
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(thriftField(0, "success", "wire.order"));
        contract.getTypes().put("wire.result", thriftType("wire_exchange_result", 12, results, List.of()));
        return contract;
    }

    private static MetProtocolDescription xdrContract() {
        MetProtocolDescription contract = new MetProtocolDescription();
        contract.setName("ORDER_PROGRAM");
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("procedureId", 9);
        operation.put("name", "WIRE_EXCHANGE");
        operation.put("javaSignature", SIGNATURE);
        List<Integer> argumentOrder = new ArrayList<>();
        argumentOrder.add(0);
        operation.put("argIndexes", argumentOrder);
        operation.put("requestType", "wire.order");
        operation.put("responseType", "wire.order");
        contract.getMethods().add(operation);
        contract.getExtensions().put("program", 600001);
        contract.getExtensions().put("version", 1);

        List<Map<String, Object>> members = new ArrayList<>();
        // IDL 顺序与 Java 属性列表顺序不同，实际绑定依赖有效名称。
        members.add(xdrMember("xdr_text", "wire.text"));
        members.add(xdrMember("xdr_state", "wire.state"));
        contract.getTypes().put("wire.order", xdrType("xdr_order", "struct", members, List.of()));
        List<Map<String, Object>> constants = new ArrayList<>();
        constants.add(Map.of("fieldId", 7, "name", "XDR_FINISHED"));
        constants.add(Map.of("fieldId", 3, "name", "XDR_READY"));
        contract.getTypes().put("wire.state", xdrType("xdr_state_type", "enum", List.of(), constants));
        contract.getTypes().put("wire.text", xdrType("string", "string", List.of(), List.of()));
        return contract;
    }

    private static MetFieldDescription field(String name, String javaName, Long id, String type) {
        MetFieldDescription field = new MetFieldDescription();
        field.setName(name);
        field.setJavaName(javaName);
        field.setFieldId(id);
        field.setType(type);
        return field;
    }

    private static MetTypeDescription type(String name, String javaType, String kind) {
        MetTypeDescription type = new MetTypeDescription();
        type.setName(name);
        type.setJavaType(javaType);
        type.setKind(kind);
        return type;
    }

    private static Map<String, Object> thriftField(int id, String name, String type) {
        return Map.of("fieldId", id, "name", name, "type", type, "requirement", "default");
    }

    private static Map<String, Object> thriftType(String name, int wireType, //
            List<Map<String, Object>> fields, List<Map<String, Object>> values) {
        return Map.of("name", name, "wireType", wireType, "binary", false, "union", false, "fields", fields, "values", values);
    }

    private static Map<String, Object> xdrMember(String name, String type) {
        return Map.of("name", name, "type", type, "defaultArm", false, "cases", List.of());
    }

    private static Map<String, Object> xdrType(String name, String kind, //
            List<Map<String, Object>> members, List<Map<String, Object>> values) {
        return Map.of("name", name, "kind", kind, "members", members, "values", values, "fixed", false, "length", 0);
    }

    private static MetFieldDescription property(MetTypeDescription type, String javaName) {
        return type.getProperties().stream().filter(field -> StringUtils.equals(javaName, field.getJavaName()))
                .findFirst().orElseThrow();
    }

    private static MetFieldDescription propertyValue(MetTypeDescription type, String javaName) {
        return type.getValues().stream().filter(field -> StringUtils.equals(javaName, field.getJavaName()))
                .findFirst().orElseThrow();
    }
}
