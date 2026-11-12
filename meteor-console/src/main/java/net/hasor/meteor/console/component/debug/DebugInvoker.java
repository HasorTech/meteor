/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.debug;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.description.MetMethodDescription;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 每次请求拥有独立的业务客户端，只向已核验的提供者地址发送一次调用。 */
public final class DebugInvoker {
    public Object invoke(ExampleTarget target, String signature, JsonNode arguments, int timeout, Path directory) throws Exception {
        var description = target.getDescription();
        MetMethodDescription method = description.getMethods().stream()
                .filter(item -> StringUtils.equals(item.getJavaSignature(), signature)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("方法不在当前服务契约中"));
        if (method.isMessage() || description.isMessage()) throw new IllegalArgumentException("暂不支持单向消息调试");
        if (!arguments.isArray() || arguments.size() != method.getParameters().size()) {
            throw new IllegalArgumentException("请求参数必须是与方法参数数量一致的 JSON 数组");
        }
        if (timeout < 100 || timeout > 30000) throw new IllegalArgumentException("超时范围为 100～30000 毫秒");
        DebugContract loader = new DebugContract(description);
        Class<?> serviceType = loader.serviceType();
        Method selected = null;
        for (Method candidate : serviceType.getMethods()) {
            StringBuilder key = new StringBuilder(candidate.getName()).append('(');
            for (int i = 0; i < candidate.getParameterCount(); i++) {
                if (i > 0) key.append(',');
                key.append(candidate.getParameterTypes()[i].getTypeName());
            }
            if (StringUtils.equals(key.append(')').toString(), signature)) selected = candidate;
        }
        if (selected == null) throw new IllegalArgumentException("无法恢复方法签名：" + signature);
        Object[] values = new Object[selected.getParameterCount()];
        for (int i = 0; i < values.length; i++) {
            values[i] = JsonUtils.readValue(JsonUtils.writeValueAsBytes(arguments.get(i)),
                    selected.getGenericParameterTypes()[i]);
        }
        var endpoint = target.getEndpoint();
        String protocol = endpoint.getProtocolName();
        MetProtocolDescription contract = endpoint.getDescription();
        if (contract == null) throw new IllegalArgumentException("提供者缺少该协议的调用契约");
        Object connection = contract.getExtensions().get("connection");
        if (connection instanceof Map<?, ?> options && Boolean.TRUE.equals(options.get("tlsEnabled"))) {
            throw new IllegalArgumentException("当前在线调试暂不支持 TLS 接入点");
        }
        Files.createDirectories(directory);
        Path home = Files.createTempDirectory(directory, "call-");
        MetConfiguration configuration = new MetConfiguration().setApplicationName("console-debug")
                .setDataHome(home).setDefaultProtocol(protocol).setDefaultTimeout(timeout).setConnectTimeout(Math.min(timeout, 3000));
        var connector = configuration.connector(endpoint.getTransport()).protocol(protocol).addressScheme(endpoint.getScheme());
        String contextPath = ExampleConnections.contextPath(target);
        if (contextPath != null) connector.contextPath(contextPath);
        if (Set.of("grpc", "triple").contains(protocol)) connector.option("httpVersion", 2);
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            connector.option("framed", contract.getExtensions().getOrDefault("framed", true));
            connector.option("multiplexed", contract.getExtensions().getOrDefault("multiplexed", false));
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try (MetContext context = configuration.buildContext()) {
            context.start();
            var builder = context.publisher().rsfService(serviceType)
                    .group(target.getService().getGroup()).name(target.getService().getName()).version(target.getService().getVersion())
                    .protocol(protocol).aliasName(protocol, contract.getName());
            if (StringUtils.equals(protocol, "hprose")) builder.aliasName("Hprose", contract.getName());
            if (StringUtils.isNotBlank(contract.getSerializeType())) builder.serialize(contract.getSerializeType());
            String[] paths = ExampleConnections.servicePaths(target);
            if (paths.length > 0) {
                String[] additional = Arrays.copyOfRange(paths, 1, paths.length);
                if (Boolean.TRUE.equals(contract.getExtensions().get("contextExact"))) {
                    builder.contextPathByExact(protocol, paths[0], additional);
                } else {
                    builder.contextPathByPrefix(protocol, paths[0], additional);
                }
            }
            MetBindInfo<?> binding = builder.register();
            Object result = context.getRsfClient(target.getAddress()).syncInvoke(binding, selected.getName(), selected.getParameterTypes(), values);
            // 转为普通 JSON 数据后再释放本次调用独占的业务类加载器和上下文。
            return JsonUtils.readTree(JsonUtils.writeValueAsBytes(result));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            Files.deleteIfExists(home.resolve("meteor.lock"));
            Files.deleteIfExists(home.resolve("meteor.info"));
            Files.deleteIfExists(home);
        }
    }
}
