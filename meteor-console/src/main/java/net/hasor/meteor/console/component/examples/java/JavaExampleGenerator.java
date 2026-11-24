/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.java;
import java.net.URI;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleRegistry;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.component.examples.nativeclient.BinaryContracts;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.connector.ProtocolProvider;
import net.hasor.meteor.registry.admin.MetServiceEndpoint;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 以实际业务接口和端点生成发布和调用代码。 */
public final class JavaExampleGenerator {
    private static final Set<String> PROTOCOLS = Set.of("rsf", "dubbo", "thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "grpc", "grpc-web", "connect", "triple");
    private static final Map<String, String> DEFAULT_SERIALIZERS = defaultSerializers();
    private static final Set<String> REGISTRY_TYPES = Set.of("jdbc", "nacos", "zookeeper");

    public List<ExampleDocument> generate(ExampleTarget target, ExampleRegistry registry, String address) {
        MetServiceEndpoint endpoint = target.getEndpoint();
        if (endpoint == null || endpoint.getProtocolName() == null || !PROTOCOLS.contains(endpoint.getProtocolName())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_NO_METEOR_JAVA_TEMPLATE_IS_AVAILABLE);
        }
        ExampleConnections.validate(target);
        if (StringUtils.isBlank(target.getAddress())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_PROVIDER_HAS_NOT_REPORTED_A);
        }
        MetProtocolDescription contract = endpoint.getDescription();
        if (contract == null || StringUtils.isBlank(contract.getName())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ENDPOINT_IS_MISSING_A_COMPLETE);
        }

        if (registry != null && (StringUtils.isBlank(registry.getAddress()) || StringUtils.isBlank(registry.getType())
                || !REGISTRY_TYPES.contains(registry.getType()))) {
            registry = null;
        }
        JavaContractSources declaration = JavaContractSources.forExample(target.getDescription());
        JavaContractSources sources = JavaContractSources.forExample( //
                JavaWireMapping.map(endpoint.getProtocolName(), target.getDescription(), contract));
        String serviceType = declaration.exampleType();
        String protocol = endpoint.getProtocolName();
        boolean preserveErasure = Set.of("rsf", "dubbo").contains(protocol)
                || StringUtils.equals(protocol, "triple") && StringUtils.equalsIgnoreCase(contract.getSerializeType(), "Hessian2");
        String interfaceCode = "import net.hasor.meteor.annotation.*;\n\n"
                + this.serviceAnnotation(target, contract) + declaration.exampleInterface(serviceType, preserveErasure);
        String resources = this.resources(protocol, contract, declaration.methodSignature());
        List<ExampleDocument> documents = new ArrayList<>();
        String serviceAddress = StringUtils.defaultIfBlank(address, ExampleConnections.ADDRESS_PLACEHOLDER);
        String direct = this.callerBody(target, contract, sources, serviceType, false, serviceAddress);
        documents.add(this.document(target, contract, "direct", direct, interfaceCode, resources));
        String implementation = serviceType + "Impl";
        String reference = serviceType + ".class, new " + implementation + "()";
        String provider = "            " + this.registration(target, contract, serviceType, reference, false)
                + "            context.start();\n"
                + "            new java.util.concurrent.CountDownLatch(1).await();\n";
        String providerTypes = interfaceCode.stripTrailing() + "\n\nclass " + implementation + " implements " + serviceType + " {\n    // ...\n}";
        documents.add(this.document(target, contract, "provider", provider, providerTypes, resources));
        if (registry != null) {
            String consumer = this.callerBody(target, contract, sources, serviceType, true, serviceAddress);
            documents.add(this.document(target, contract, "registry", consumer, interfaceCode, resources));
        }
        return documents;
    }

    public String interfaceDefinition(ExampleTarget target) {
        var source = JavaContractSources.forInterface(target.getDescription());
        return ExampleTemplates.codeBlock("java", this.serviceAnnotation(target, target.getEndpoint().getDescription()) + source.fullInterface());
    }

    private String serviceAnnotation(ExampleTarget target, MetProtocolDescription contract) {
        var service = target.getService();
        List<String> attributes = new ArrayList<>();
        MetConfiguration defaults = new MetConfiguration();
        if (!StringUtils.equals(service.getGroup(), defaults.getDefaultGroup())) {
            attributes.add("group = " + literal(service.getGroup()));
        }
        attributes.add("name = " + literal(service.getName()));
        if (!StringUtils.equals(service.getVersion(), defaults.getDefaultVersion())) {
            attributes.add("version = " + literal(service.getVersion()));
        }
        String serializer = contract.getSerializeType();
        String protocol = target.getEndpoint().getProtocolName();
        if (StringUtils.isNotBlank(serializer) && !StringUtils.equals(serializer, DEFAULT_SERIALIZERS.get(protocol))) {
            attributes.add("serializeType = " + literal(serializer));
        }
        Long id = target.getDescription().getContractServiceId();
        if (id != null && id != -1L) {
            attributes.add("id = " + id + "L");
        }
        return "@MetService(" + String.join(", ", attributes) + ")\n";
    }

    private ExampleDocument document(ExampleTarget target, MetProtocolDescription contract,
            String role, String body, String types, String resources) {
        boolean discovery = StringUtils.equals(role, "registry");
        boolean provider = StringUtils.equals(role, "provider");
        String protocol = target.getEndpoint().getProtocolName();
        Map<String, String> values = new LinkedHashMap<>();
        String configuration = "        var configuration = new MetConfiguration()";
        if (discovery && StringUtils.isNotBlank(target.getUnit())
                && !StringUtils.equals(target.getUnit(), new MetConfiguration().getUnitName())) {
            configuration += ".setUnitName(" + literal(target.getUnit()) + ")";
        }
        values.put("configuration", configuration + ";");
        values.put("connector", this.connectors(target, contract, provider, discovery));
        String registryImport = "";
        String registrySetup = "";
        if (discovery) {
            registryImport = "import net.hasor.meteor.registry.RegistryConfiguration;\n";
            registrySetup = "        // See the documentation for registry configuration.\n"
                    + "        RegistryConfiguration registry = ...;\n"
                    + "        configuration.addExtension(registry);\n";
        }
        values.put("registryImport", registryImport);
        values.put("registry", registrySetup);
        values.put("resources", resources);
        values.put("body", body);
        String code = ExampleTemplates.render("java/ExampleMain.java.tpl", values);
        String markdown = ExampleTemplates.gradleDependencies(this.dependencies(protocol, discovery)) + "\n\n"
                + ExampleTemplates.codeBlock("java", types) + "\n\n"
                + ExampleTemplates.codeBlock("java", code);
        return new ExampleDocument("java-" + role, "java", role, markdown);
    }

    private String connectors(ExampleTarget target, MetProtocolDescription contract, boolean provider, boolean discovery) {
        MetServiceEndpoint endpoint = target.getEndpoint();
        String protocol = endpoint.getProtocolName();
        if (!Set.of("tcp", "udp", "http").contains(endpoint.getTransport())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_NO_METEOR_JAVA_EXAMPLE_TEMPLATE_IS);
        }
        StringBuilder code = new StringBuilder("        configuration.connector(").append(literal(endpoint.getTransport())).append(')');
        if (provider) {
            String listenAddress = StringUtils.defaultIfBlank(endpoint.getListenAddress(), target.getAddress());
            int port = URI.create(listenAddress).getPort();
            if (port <= 0) {
                port = StringUtils.equals(endpoint.getTransport(), "udp") ? 19314 : 0;
            }
            code.append("\n                .bind(\"0.0.0.0\", ").append(port).append(')');
        } else if (discovery && StringUtils.equals(protocol, "rsf")) {
            code.append("\n                .bind(\"0.0.0.0\", 0)");
        }
        code.append("\n                .protocol(").append(literal(protocol)).append(')');
        if (StringUtils.isNotBlank(endpoint.getScheme()) && !StringUtils.equalsIgnoreCase(endpoint.getScheme(), protocol)) {
            code.append("\n                .addressScheme(").append(literal(endpoint.getScheme())).append(')');
        }
        if (StringUtils.equals(endpoint.getTransport(), "http")) {
            String address = target.getAddress();
            if (provider) {
                address = StringUtils.defaultIfBlank(endpoint.getListenAddress(), address);
            }
            String contextPath = URI.create(address).getRawPath();
            if (StringUtils.isNotBlank(contextPath) && !StringUtils.equals(contextPath, "/")) {
                code.append("\n                .contextPath(").append(literal(contextPath)).append(')');
            }
        }
        if (Set.of("grpc", "triple").contains(protocol)) {
            code.append("\n                .option(\"httpVersion\", 2)");
        }
        if (StringUtils.equals(protocol, "thrift")) {
            Object framed = contract.getExtensions().get("framed");
            if (!(framed instanceof Boolean)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_THRIFT_ENDPOINT_HAS_NOT_REPORTED);
            }
            if (Boolean.FALSE.equals(framed)) {
                code.append("\n                .option(\"framed\", false)");
            }
        }
        if (Set.of("thrift", "thrift-http").contains(protocol) && Boolean.TRUE.equals(contract.getExtensions().get("multiplexed"))) {
            code.append("\n                .option(\"multiplexed\", true)");
        }
        code.append(";\n");
        if (discovery && !StringUtils.equals(protocol, "rsf")) {
            code.append("        configuration.connector(\"tcp\")\n") //
                    .append("                .bind(\"0.0.0.0\", 0)\n") //
                    .append("                .protocol(\"rsf\");\n");
        }
        return code.toString();
    }

    private String registration(ExampleTarget target, MetProtocolDescription contract, String serviceType, String reference, boolean discovery) {
        String protocol = target.getEndpoint().getProtocolName();
        StringBuilder code = new StringBuilder("context.publisher().rsfService(")
                .append(reference).append(')');
        if (discovery) {
            code.append("\n                    .protocol(").append(literal(protocol)).append(')');
        }
        if (!StringUtils.equals(contract.getName(), defaultAlias(target, contract, serviceType))) {
            code.append("\n                    .aliasName(").append(literal(protocol)).append(", ").append(literal(contract.getName())).append(')');
        }
        String[] paths = ExampleConnections.servicePaths(target);
        if (paths.length > 0) {
            String method = "contextPathByPrefix";
            if (Boolean.TRUE.equals(contract.getExtensions().get("contextExact"))) {
                method = "contextPathByExact";
            }
            code.append("\n                    .").append(method).append('(').append(literal(protocol));
            for (String path : paths) {
                code.append(", ").append(literal(path));
            }
            code.append(')');
        }
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            code.append("\n                    .bindResource(").append(literal(protocol))
                    .append(", new net.hasor.meteor.protocol.thrift.ThriftIdlResource(contract.toUri().toURL())");
            if (StringUtils.equals(protocol, "thrift") && !StringUtils.equals(contract.getName(), BinaryContracts.thriftServiceName(contract))) {
                code.append(".selectService(").append(literal(BinaryContracts.thriftServiceName(contract))).append(')');
            }
            code.append(')');
        } else if (Set.of("oncrpc", "oncrpc-udp").contains(protocol)) {
            code.append("\n                    .bindResource(").append(literal(protocol))
                    .append(", new net.hasor.meteor.protocol.oncrpc.binding.OncRpcIdlResource(contract.toUri().toURL()))");
        }
        if (code.indexOf("\n") >= 0) {
            code.append("\n                    ");
        }
        return code.append(".register();\n").toString();
    }

    private static String defaultAlias(ExampleTarget target, MetProtocolDescription contract, String serviceType) {
        String protocol = target.getEndpoint().getProtocolName();
        return switch (protocol) {
            case "dubbo" -> serviceType;
            case "thrift", "thrift-http" -> BinaryContracts.thriftServiceName(contract);
            case "oncrpc", "oncrpc-udp" -> contract.getName();
            case "hprose" -> StringUtils.firstCharToLowerCase(serviceType);
            default -> target.getService().getName();
        };
    }

    private static Map<String, String> defaultSerializers() {
        Map<String, String> serializers = new HashMap<>();
        for (ProtocolProvider<?> provider : ServiceLoader.load(ProtocolProvider.class, JavaExampleGenerator.class.getClassLoader())) {
            serializers.put(provider.name(), provider.defaultSerialize());
        }
        return serializers;
    }

    private String callerBody(ExampleTarget target, MetProtocolDescription contract, JavaContractSources sources,
            String serviceType, boolean discovery, String address) {
        StringBuilder code = new StringBuilder("            var binding = ")
                .append(this.registration(target, contract, serviceType, serviceType + ".class", discovery))
                .append("            context.start();\n");
        if (discovery) {
            code.append("            var service = context.getRsfClient().getRemote(binding);\n");
        } else {
            code.append("            var service = context.getRsfClient(").append(literal(address)).append(").getRemote(binding);\n");
        }
        code.append("            ").append(sources.invocationStatements("service")).append('\n');
        return code.toString();
    }

    private String resources(String protocol, MetProtocolDescription contract, String signature) {
        String name;
        String content;
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            name = "service.thrift";
            content = BinaryContracts.thrift(methodContract(contract, signature));
        } else if (Set.of("oncrpc", "oncrpc-udp").contains(protocol)) {
            name = "service.x";
            content = BinaryContracts.oncrpc(methodContract(contract, signature));
        } else {
            return "";
        }
        String text = content.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"");
        return "            var contract = context.getSettings().getDataHome().resolve(" + literal(name) + ");\n"
                + "            java.nio.file.Files.writeString(contract, \"\"\"\n" + "                    " + text.replace("\n", "\n                    ") + "\"\"\");\n";
    }

    private static MetProtocolDescription methodContract(MetProtocolDescription contract, String signature) {
        var selected = new MetProtocolDescription();
        selected.setName(contract.getName());
        selected.setExtensions(contract.getExtensions());
        selected.setTypes(contract.getTypes());
        selected.setMethods(contract.getMethods().stream()
                .filter(method -> Objects.equals(signature, method.get("javaSignature"))).toList());
        return selected;
    }

    private List<String> dependencies(String protocol, boolean discovery) {
        String version = ExampleTemplates.version();
        List<String> dependencies = new ArrayList<>();
        if (!discovery && StringUtils.equals(protocol, "rsf")) {
            dependencies.add("net.hasor:meteor:" + version);
        }
        if (!StringUtils.equals(protocol, "rsf")) {
            String module = switch (protocol) {
                case "thrift-http" -> "thrift";
                case "oncrpc-udp" -> "oncrpc";
                case "grpc-web" -> "grpc";
                default -> protocol;
            };
            dependencies.add("net.hasor:meteor-protocol-" + module + ":" + version);
        }
        if (discovery) {
            dependencies.add("net.hasor:meteor-registry:" + version);
        }
        return dependencies;
    }

    static String literal(String value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_GENERATE_A_JAVA_LITERAL);
        }
    }
}
