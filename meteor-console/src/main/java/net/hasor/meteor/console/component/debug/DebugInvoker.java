/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.debug;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.description.MetMethodDescription;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;
import net.hasor.meteor.rpc.MetServiceResolver;

/** 每次请求拥有独立的业务客户端，只向已核验的提供者地址发送一次调用。 */
public final class DebugInvoker {
    public Object invoke(ExampleTarget target, String operation, JsonNode arguments, int timeout, Path directory) throws Exception {
        var description = target.getDescription();
        List<MetMethodDescription> methods = description.getMethods().stream() //
                .filter(item -> StringUtils.equals(item.getName(), operation) || StringUtils.equals(item.getJavaSignature(), operation)) //
                .toList();
        if (methods.isEmpty() || methods.stream().map(MetMethodDescription::getJavaSignature).distinct().count() != 1) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_METHOD_IS_NOT_IN_THE);
        }

        MetMethodDescription method = methods.get(0);
        if (method.isMessage() || description.isMessage()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_ONLINE_DEBUGGING_DOES_NOT_SUPPORT_ONE);
        }
        if (!arguments.isArray() || arguments.size() != method.getParameters().size()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_REQUEST_ARGUMENTS_MUST_BE_A_JSON);
        }
        if (timeout < 100 || timeout > 30000) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_DEBUG_TIMEOUT);
        }

        var endpoint = target.getEndpoint();
        String protocol = endpoint.getProtocolName();
        MetProtocolDescription contract = endpoint.getDescription();
        if (contract == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_PROVIDER_IS_MISSING_AN_INVOCATION);
        }

        Object connection = contract.getExtensions().get("connection");
        if (connection instanceof Map<?, ?> options && Boolean.TRUE.equals(options.get("tlsEnabled"))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_ONLINE_DEBUGGING_DOES_NOT_YET_SUPPORT_3);
        }

        Files.createDirectories(directory);
        Path home = Files.createTempDirectory(directory, "call-");
        try {
            MetConfiguration configuration = configuration(target, timeout, home, contract);
            ObjectNode protocolDescription = JsonUtils.createObjectNode();
            contract.getExtensions().forEach((name, value) -> protocolDescription.set(name, JsonUtils.valueToTree(value)));
            protocolDescription.put("name", contract.getName());
            protocolDescription.put("serializeType", contract.getSerializeType());
            protocolDescription.set("methods", JsonUtils.valueToTree(contract.getMethods()));
            protocolDescription.set("types", JsonUtils.valueToTree(contract.getTypes()));
            InterAddress address = new InterAddress(target.getAddress());
            MetServiceResolver resolver = publishedTarget(target, address, JsonUtils.valueToTree(description), protocolDescription);
            Object[] parameters = JsonUtils.treeToValue(arguments, Object[].class);
            try (MetContext context = configuration.buildContext()) {
                context.start();
                return context.getRsfClient(address).withServiceResolver(resolver) //
                        .syncInvokeGeneric(target.getServiceId(), protocol, method.getName(), parameters, timeout);
            }
        } finally {
            Files.deleteIfExists(home.resolve("meteor.lock"));
            Files.deleteIfExists(home.resolve("meteor.info"));
            Files.deleteIfExists(home);
        }
    }

    private static MetServiceResolver publishedTarget(ExampleTarget target, InterAddress address, JsonNode serviceDescription, JsonNode protocolDescription) {
        return new MetServiceResolver() {
            @Override
            public <T> T resolve(String serviceId, String protocol, InterAddress fixedAddress, EndpointFactory<T> factory) {
                var endpoint = target.getEndpoint();
                if (!StringUtils.equals(serviceId, target.getServiceId()) || !StringUtils.equalsIgnoreCase(protocol, endpoint.getProtocolName()) || !address.equals(fixedAddress)) {
                    return null;
                }
                var service = target.getService();
                return factory.create(address, service.getGroup(), service.getName(), service.getVersion(), //
                        endpoint.getProtocolName(), endpoint.getContextPath(), serviceDescription, protocolDescription);
            }
        };
    }

    private static MetConfiguration configuration(ExampleTarget target, int timeout, Path home, MetProtocolDescription contract) {
        var endpoint = target.getEndpoint();
        String protocol = endpoint.getProtocolName();
        MetConfiguration configuration = new MetConfiguration().setApplicationName("console-debug").setDataHome(home) //
                .setDefaultProtocol(protocol).setDefaultTimeout(timeout).setConnectTimeout(Math.min(timeout, 3000));
        var connector = configuration.connector(endpoint.getTransport()).protocol(protocol).addressScheme(endpoint.getScheme());
        String contextPath = ExampleConnections.contextPath(target);
        if (contextPath != null) {
            connector.contextPath(contextPath);
        }
        if (Set.of("grpc", "triple").contains(protocol)) {
            connector.option("httpVersion", 2);
        }
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            connector.option("framed", contract.getExtensions().getOrDefault("framed", true));
            connector.option("multiplexed", contract.getExtensions().getOrDefault("multiplexed", false));
        }

        if (StringUtils.equals("soap", protocol)) {
            Object version = contract.getExtensions().get("version");
            if (version != null) {
                connector.option("soapVersion", version);
            }
        }
        return configuration;
    }
}
