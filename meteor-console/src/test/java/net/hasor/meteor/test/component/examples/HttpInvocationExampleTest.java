/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.component.examples;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.nativeclient.HttpExampleGenerator;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.MetServiceEndpoint;
import net.hasor.meteor.registry.admin.description.MetFieldDescription;
import net.hasor.meteor.registry.admin.description.MetMethodDescription;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;
import net.hasor.meteor.registry.admin.description.MetTypeDescription;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 通过公开文档入口验证原生客户端只保留一份实际可构造参数的调用。 */
class HttpInvocationExampleTest {
    @ParameterizedTest(name = "{0} 文档跳过不可生成参数的方法")
    @ValueSource(strings = { "python", "javascript" })
    void documentUsesTheFirstCallableWireMethodAndInlinesOnlyItsArguments(String language) throws Exception {
        // 首个方法的参数递归；第二个方法可调用，之后仍有其他可调用和缺少类型声明的方法。
        ExampleTarget target = target();

        ExampleDocument document = new HttpExampleGenerator() //
                .generate(target, target.getAddress()).stream() //
                .filter(item -> StringUtils.equals(language, item.getLanguage())) //
                .findFirst().orElseThrow();

        // 文档直接内联选中的线上方法和位置参数，不暴露其他方法或外部 calls 文件。
        String markdown = document.getMarkdown();
        String code = code(markdown, language);
        String key = StringUtils.equals("python", language) ? "\"%s\"" : "%s";
        var payload = Pattern.compile(key.formatted("method") + ": (\"[^\"]+\"), " + key.formatted("params") + ": (\\[[^]]*])").matcher(code);
        assertTrue(payload.find(), code);
        assertEquals("orders-live.wire_selected", JsonUtils.readTree(payload.group(1)).asText());
        assertEquals(JsonUtils.readTree("[\"example\",1]"), JsonUtils.readTree(payload.group(2)));
        assertTrue(code.contains("http://127.0.0.1:19206/rpc/live"), code);
        assertFalse(markdown.contains("wire_recursive"), markdown);
        assertFalse(markdown.contains("wire_later"), markdown);
        assertFalse(markdown.contains("wire_missing"), markdown);
        assertFalse(markdown.contains("calls.json"), markdown);
        assertFalse(markdown.contains("运行命令"), markdown);
        assertFalse(Pattern.compile("(?m)^(?:const )?calls\\s*=").matcher(code).find(), code);
        assertFalse(code.contains("calls[0]"), code);
    }

    @ParameterizedTest(name = "{0} 文档使用显式地址和实际挂载路径")
    @ValueSource(strings = { "python", "javascript" })
    void providedAddressOverridesTheProviderSnapshotAndKeepsTheContextPath(String language) {
        // 显式地址与服务快照不同，地址中的路径也不同于实际 HTTP 挂载。
        ExampleTarget target = target();
        String address = "http://client.example:19207/ignored";

        ExampleDocument document = new HttpExampleGenerator().generate(target, address).stream() //
                .filter(item -> StringUtils.equals(language, item.getLanguage())) //
                .findFirst().orElseThrow();

        // 主机和端口来自显式地址，调用路径仍使用服务当前的真实挂载。
        String code = code(document.getMarkdown(), language);
        assertTrue(code.contains("http://client.example:19207/rpc/live"), code);
        assertFalse(code.contains("127.0.0.1:19206"), code);
        assertFalse(code.contains("/ignored"), code);
    }

    @ParameterizedTest(name = "地址为空时保留明确占位符：{0}")
    @NullAndEmptySource
    @ValueSource(strings = { " \t " })
    void missingAddressUsesAnExplicitPlaceholderInsteadOfTheProviderSnapshot(String address) {
        // 服务快照含有可用地址，但用户没有提供示例的调用地址。
        ExampleTarget target = target();

        List<ExampleDocument> documents = new HttpExampleGenerator().generate(target, address);

        // 同一轮生成的各语言文档都保留占位符，不隐式回退到快照地址。
        assertEquals(2, documents.size());
        for (ExampleDocument document : documents) {
            String code = code(document.getMarkdown(), document.getLanguage());
            assertTrue(code.contains("SERVICE_ADDRESS"), code);
            assertFalse(code.contains("127.0.0.1:19206"), code);
        }
    }

    private static ExampleTarget target() {
        MetServiceDescription description = new MetServiceDescription();
        MetTypeDescription recursive = type("example.Recursive", "object");
        MetFieldDescription child = parameter(0, "example.Recursive");
        child.setJavaName("child");
        recursive.getProperties().add(child);
        description.getTypes().put("example.Recursive", recursive);
        description.getTypes().put("java.lang.String", type("java.lang.String", "scalar"));
        description.getTypes().put("int", type("int", "scalar"));

        MetProtocolDescription binding = new MetProtocolDescription();
        binding.setName("orders-live");
        // 操作顺序决定示例的候选优先级，参数列表顺序则不应改变实际参数位置。
        MetFieldDescription recursiveArgument = parameter(0, "example.Recursive");
        MetMethodDescription first = method("recursive", "recursive(example.Recursive)", recursiveArgument);
        description.getMethods().add(first);
        binding.getMethods().add(Map.of("javaSignature", first.getJavaSignature(), "name", "wire_recursive"));
        MetMethodDescription selected = method("accept", "accept(java.lang.String,int)", //
                parameter(1, "int"), parameter(0, "java.lang.String"));
        description.getMethods().add(selected);
        binding.getMethods().add(Map.of("javaSignature", selected.getJavaSignature(), "name", "wire_selected"));
        MetMethodDescription later = method("later", "later(java.lang.String)", parameter(0, "java.lang.String"));
        description.getMethods().add(later);
        binding.getMethods().add(Map.of("javaSignature", later.getJavaSignature(), "name", "wire_later"));
        MetMethodDescription missing = method("missing", "missing(example.Unknown)", parameter(0, "example.Unknown"));
        description.getMethods().add(missing);
        binding.getMethods().add(Map.of("javaSignature", missing.getJavaSignature(), "name", "wire_missing"));

        MetServiceEndpoint endpoint = new MetServiceEndpoint();

        endpoint.setDescription(binding);
        endpoint.setTransport("http");
        endpoint.setProtocolName("jsonrpc");
        endpoint.setScheme("jsonrpc");
        endpoint.setContextPath("/rpc/live");
        ExampleTarget target = new ExampleTarget();
        target.setDescription(description);
        target.setEndpoint(endpoint);
        target.setAddress("jsonrpc://127.0.0.1:19206");
        return target;
    }

    private static MetTypeDescription type(String javaType, String kind) {
        MetTypeDescription type = new MetTypeDescription();
        type.setJavaType(javaType);
        type.setKind(kind);
        return type;
    }

    private static MetMethodDescription method(String javaName, String signature, MetFieldDescription... parameters) {
        MetMethodDescription method = new MetMethodDescription();
        method.setJavaName(javaName);
        method.setJavaSignature(signature);
        for (MetFieldDescription parameter : parameters) {
            method.getParameters().add(parameter);
        }
        return method;
    }

    private static MetFieldDescription parameter(int position, String type) {
        MetFieldDescription parameter = new MetFieldDescription();
        parameter.setArgIndex(position);
        parameter.setType(type);
        return parameter;
    }

    private static String code(String markdown, String language) {
        String expression = "(?ms)^```" + Pattern.quote(language) + "[ \\t]*\\R(.*?)^```[ \\t]*$";
        var matcher = Pattern.compile(expression).matcher(markdown);
        assertTrue(matcher.find(), markdown);
        String code = matcher.group(1);
        assertFalse(matcher.find(), "每份文档只展示一份主代码：" + markdown);
        return code;
    }
}
