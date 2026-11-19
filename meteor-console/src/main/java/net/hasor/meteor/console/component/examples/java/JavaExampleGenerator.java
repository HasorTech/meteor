/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.java;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleRegistry;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.component.examples.nativeclient.BinaryContracts;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.MetServiceEndpoint;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 以实际业务接口和端点生成简短文档，不复制接口实现或构建完整工程。 */
public final class JavaExampleGenerator {
    private static final Set<String> PROTOCOLS = Set.of("rsf", "dubbo", "thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "jsonrpc", "xmlrpc", "hessian", "soap", "grpc", "grpc-web", "connect", "triple");

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

        if (registry != null && (StringUtils.isBlank(registry.getAddress()) || StringUtils.isBlank(registry.getType()) || !Set.of("jdbc", "nacos", "zookeeper").contains(registry.getType()))) {
            registry = null;
        }
        JavaContractSources sources = new JavaContractSources( //
                JavaWireMapping.map(endpoint.getProtocolName(), target.getDescription(), contract));
        // 直连主示例附带注册中心接入说明；保留独立代码用于验证两种调用方式。
        List<ExampleDocument> documents = new ArrayList<>();
        String serviceAddress = StringUtils.defaultIfBlank(address, "请填写服务地址");
        ExampleDocument direct = this.document(target, contract, sources, null, "direct", serviceAddress);
        documents.add(new ExampleDocument(direct.getId(), direct.getLanguage(), direct.getRole(), direct.getMarkdown() + this.registryUsage(target, contract, sources, registry)));
        documents.add(this.document(target, contract, sources, registry, "provider", serviceAddress));
        documents.add(this.document(target, contract, sources, registry, "consumer", serviceAddress));
        return documents;
    }

    private ExampleDocument document(ExampleTarget target, MetProtocolDescription contract, JavaContractSources sources, //
            ExampleRegistry registry, String role, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        boolean provider = StringUtils.equals(role, "provider");
        String application = "meteor-example-" + Integer.toUnsignedString(target.getServiceId().hashCode(), 36) + "-" + protocol + "-" + role;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("application", literal(application));
        values.put("unit", literal(target.getUnit()));
        values.put("protocol", literal(protocol));
        values.put("connector", this.connectors(target, contract, provider, registry != null));
        values.put("registry", this.registry(registry));
        values.put("resources", this.resources(protocol, contract));
        String resourceClose = "";
        if (registry != null && StringUtils.equals(registry.getType(), "jdbc")) {
            resourceClose = "source; ";
        }
        values.put("resourceClose", resourceClose);
        values.put("body", this.body(target, contract, sources, role, registry, address));
        values.put("dependencies", this.dependencies(protocol, registry));
        values.put("serviceType", sources.serviceType());
        values.put("registryDescription", this.registryDescription(registry, role));
        values.put("code", ExampleTemplates.codeBlock("java", ExampleTemplates.render("java/ExampleMain.java.tpl", values)));
        return new ExampleDocument("java-" + role, "java", role, ExampleTemplates.render("java/document.md.tpl", values));
    }

    private String registryUsage(ExampleTarget target, MetProtocolDescription contract, JavaContractSources sources, ExampleRegistry registry) {
        if (registry == null) {
            return "\n## 使用注册中心\n\n直连由调用方指定服务地址；注册中心由调用方配置连接参数，再按同一服务 ID 发现提供者。当前连接没有可复用的注册中心配置。\n";
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("dependencies", this.dependencies(target.getEndpoint().getProtocolName(), registry));
        values.put("description", this.registryDescription(registry, "consumer"));
        values.put("configuration", ExampleTemplates.codeBlock("java", this.connectors(target, contract, false, true) + this.registry(registry)));
        values.put("serviceType", sources.serviceType());
        String resourceClose = "";
        if (StringUtils.equals(registry.getType(), "jdbc")) {
            resourceClose = " 数据源由示例创建，将原来的 try 资源声明改为 `try (source; MetContext context = configuration.buildContext())`，随上下文一起关闭。";
        }
        values.put("resourceClose", resourceClose);
        return ExampleTemplates.render("java/registry.md.tpl", values);
    }

    private String connectors(ExampleTarget target, MetProtocolDescription contract, boolean provider, boolean discovery) {
        MetServiceEndpoint endpoint = target.getEndpoint();
        String protocol = endpoint.getProtocolName();
        if (!Set.of("tcp", "udp", "http").contains(endpoint.getTransport())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_NO_METEOR_JAVA_EXAMPLE_TEMPLATE_IS);
        }
        StringBuilder code = new StringBuilder("        configuration.connector(").append(literal(endpoint.getTransport())).append(')');
        if (provider || discovery && StringUtils.equals(protocol, "rsf")) {
            int port = 0;
            if (StringUtils.equals(endpoint.getTransport(), "udp")) {
                port = 19314; // UDP 监听要求指定端口，示例可按本地部署修改。
            }
            code.append(" //\n                .bind(\"0.0.0.0\", ").append(port).append(')');
        }
        code.append(" //\n                .protocol(").append(literal(protocol)).append(')');
        code.append(" //\n                .addressScheme(").append(literal(endpoint.getScheme())).append(')');
        String contextPath = ExampleConnections.contextPath(target);
        if (contextPath != null) {
            code.append(" //\n                .contextPath(").append(literal(contextPath)).append(')');
        }
        if (Set.of("grpc", "triple").contains(protocol)) {
            code.append(" //\n                .option(\"httpVersion\", 2)");
        }
        if (StringUtils.equals(protocol, "thrift")) {
            Object framed = contract.getExtensions().get("framed");
            if (!(framed instanceof Boolean)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_THRIFT_ENDPOINT_HAS_NOT_REPORTED);
            }
            code.append(" //\n                .option(\"framed\", ").append(framed).append(')');
            code.append(" //\n                .option(\"multiplexed\", ").append(contract.getExtensions().getOrDefault("multiplexed", false)).append(')');
        }
        code.append(";\n");
        if (discovery && !StringUtils.equals(protocol, "rsf")) {
            code.append("        configuration.connector(\"tcp\") //\n") //
                    .append("                .bind(\"0.0.0.0\", 0) //\n") //
                    .append("                .protocol(\"rsf\");\n");
        }
        return code.toString();
    }

    private String body(ExampleTarget target, MetProtocolDescription contract, JavaContractSources sources, String role, ExampleRegistry registry, String address) {
        String type = sources.serviceType();
        String protocol = target.getEndpoint().getProtocolName();
        boolean provider = StringUtils.equals(role, "provider");
        boolean discovery = registry != null;
        StringBuilder code = new StringBuilder("            MetBindInfo<").append(type).append("> binding = context.publisher().rsfService(").append(type).append(".class)");
        if (provider) {
            code.append(" //\n                    .toInstance(").append(sources.providerBody()).append(')');
        }
        code.append(" //\n                    .group(").append(literal(target.getService().getGroup())).append(')');
        code.append(" //\n                    .name(").append(literal(target.getService().getName())).append(')');
        code.append(" //\n                    .version(").append(literal(target.getService().getVersion())).append(')');
        code.append(" //\n                    .protocol(").append(literal(protocol)).append(')');
        code.append(" //\n                    .aliasName(").append(literal(protocol)).append(", ").append(literal(contract.getName())).append(')');
        if (StringUtils.isNotBlank(contract.getSerializeType())) {
            code.append(" //\n                    .serialize(").append(literal(contract.getSerializeType())).append(')');
        }
        String[] paths = ExampleConnections.servicePaths(target);
        if (paths.length > 0) {
            String method = "contextPathByPrefix";
            if (Boolean.TRUE.equals(contract.getExtensions().get("contextExact"))) {
                method = "contextPathByExact";
            }
            code.append(" //\n                    .").append(method).append('(').append(literal(protocol));
            for (String path : paths) {
                code.append(", ").append(literal(path));
            }
            code.append(')');
        }
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            code.append(" //\n                    .bindResource(").append(literal(protocol)).append(", new net.hasor.meteor.protocol.thrift.ThriftIdlResource(contract.toUri().toURL()).selectService(").append(literal(BinaryContracts.thriftServiceName(contract))).append("))");
        } else if (Set.of("oncrpc", "oncrpc-udp").contains(protocol)) {
            Object version = contract.getExtensions().get("version");
            if (!(version instanceof Number)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_ONC_RPC_PROGRAM_VERSION);
            }
            code.append(" //\n                    .bindResource(").append(literal(protocol)).append(", new net.hasor.meteor.protocol.oncrpc.binding.OncRpcIdlResource(contract.toUri().toURL()).selectProgram(").append(literal(contract.getName())).append(", ").append(((Number) version).longValue()).append("L))");
        }
        code.append(" //\n                    .register();\n");
        if (provider) {
            code.append("            System.out.println(context.getListenAddresses(").append(literal(protocol)).append("));\n");
            code.append("            Runtime.getRuntime().addShutdownHook(new Thread(() -> {\n");
            code.append("                try { context.close(); } catch (Exception failure) { failure.printStackTrace(); }\n");
            if (registry != null && StringUtils.equals(registry.getType(), "jdbc")) {
                code.append("                try { source.close(); } catch (Exception failure) { failure.printStackTrace(); }\n");
            }
            code.append("            }));\n");
            code.append("            new java.util.concurrent.CountDownLatch(1).await();\n");
        } else {
            if (StringUtils.equals(role, "direct")) {
                code.append("            ").append(type).append(" service = context.getRsfClient(new InterAddress(").append(literal(address)).append(")).getRemote(binding);\n");
            } else {
                if (discovery) {
                    code.append("            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);\n");
                    code.append("            while (context.getUpdater().queryAvailableAddresses(binding.getBindID()).isEmpty()) {\n");
                    code.append("                if (System.nanoTime() >= deadline) throw new IllegalStateException(\"No service provider is available\");\n");
                    code.append("                Thread.sleep(100);\n");
                    code.append("            }\n");
                } else {
                    code.append("            context.getUpdater().appendStaticAddress(binding.getBindID(), java.util.List.of(new InterAddress(").append(literal(address)).append(")));\n");
                }
                code.append("            ").append(type).append(" service = context.getRsfClient().getRemote(binding);\n");
            }
            code.append("            ").append(sources.invocationStatements("service")).append('\n');
        }
        return code.toString();
    }

    private String resources(String protocol, MetProtocolDescription contract) {
        String name;
        String content;
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            name = "service.thrift";
            content = BinaryContracts.thrift(contract);
        } else if (Set.of("oncrpc", "oncrpc-udp").contains(protocol)) {
            name = "service.x";
            content = BinaryContracts.oncrpc(contract);
        } else {
            return "";
        }
        String text = content.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"");
        return "            java.nio.file.Path contract = context.getSettings().getDataHome().resolve(" + literal(name) + ");\n" + "            java.nio.file.Files.createDirectories(contract.getParent());\n" + "            java.nio.file.Files.writeString(contract, \"\"\"\n" + "                    " + text.replace("\n", "\n                    ") + "\"\"\", java.nio.charset.StandardCharsets.UTF_8);\n";
    }

    private String registry(ExampleRegistry registry) {
        if (registry == null) {
            return "";
        }
        StringBuilder code = new StringBuilder();
        code.append("        String publishHost = System.getenv(\"METEOR_PUBLISH_HOST\");\n");
        code.append("        if (publishHost == null || publishHost.isBlank()) {\n");
        code.append("            throw new IllegalStateException(\"Set METEOR_PUBLISH_HOST to a local address reachable by other applications\");\n");
        code.append("        }\n");
        if (StringUtils.equals(registry.getType(), "jdbc")) {
            code.append("        org.apache.commons.dbcp2.BasicDataSource source = new org.apache.commons.dbcp2.BasicDataSource();\n");
            code.append("        source.setUrl(").append(literal(registry.getAddress())).append(");\n");
            code.append("        source.setUsername(System.getenv().getOrDefault(\"METEOR_REGISTRY_USERNAME\", ").append(literal(StringUtils.defaultIfBlank(registry.getUsername(), ""))).append("));\n");
            code.append("        source.setPassword(System.getenv().getOrDefault(\"METEOR_REGISTRY_PASSWORD\", \"\"));\n");
            code.append("        var registry = new net.hasor.meteor.registry.jdbc.JdbcRegistryConfiguration().setDataSource(source);\n");
        } else {
            String className = "net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration";
            if (StringUtils.equals(registry.getType(), "nacos")) {
                className = "net.hasor.meteor.registry.nacos.NacosRegistryConfiguration";
            }
            code.append("        var registry = new ").append(className).append("() //\n                .setAddress(").append(literal(registry.getAddress())).append(");\n");
            code.append("        String username = System.getenv().getOrDefault(\"METEOR_REGISTRY_USERNAME\", ").append(literal(StringUtils.defaultIfBlank(registry.getUsername(), null))).append(");\n");
            code.append("        if (username != null) {\n");
            code.append("            registry.setUsername(username);\n");
            code.append("            registry.setPassword(System.getenv().getOrDefault(\"METEOR_REGISTRY_PASSWORD\", \"\"));\n");
            code.append("        }\n");
            if (StringUtils.equals(registry.getType(), "nacos")) {
                code.append("        registry.setGroup(").append(literal(StringUtils.defaultIfBlank(registry.getGroup(), "meteor"))).append(");\n");
                if (registry.getNamespace() != null) {
                    code.append("        registry.setNamespace(").append(literal(registry.getNamespace())).append(");\n");
                }
            } else {
                code.append("        registry.setRoot(").append(literal(StringUtils.defaultIfBlank(registry.getRoot(), "/meteor"))).append(");\n");
            }
        }
        code.append("        registry.setPublishHost(publishHost);\n");
        code.append("        registry.setManagementToken(java.util.UUID.randomUUID().toString());\n");
        code.append("        configuration.addExtension(registry);\n");
        return code.toString();
    }

    private String registryDescription(ExampleRegistry registry, String role) {
        if (StringUtils.equals(role, "direct")) {
            return "使用传入的服务地址直连调用。";
        }
        if (registry == null) {
            if (StringUtils.equals(role, "provider")) {
                return "发布示例实现，端口由系统分配。当前连接没有可复用的注册中心配置。";
            }
            return "通过本地地址本获取服务代理，使用传入的服务地址。当前连接没有可复用的注册中心配置。";
        }
        String description = "注册中心使用当前连接的 `" + registry.getType() + "`，依赖和连接参数已包含在下方代码中。";
        description += " `METEOR_PUBLISH_HOST` 必须是运行此示例的本机可达地址，用于发布服务和管理端点。";
        if (registry.isCredentialsRequired() || StringUtils.isNotBlank(registry.getUsername())) {
            description += " 密码从 `METEOR_REGISTRY_PASSWORD` 读取，用户名可由 `METEOR_REGISTRY_USERNAME` 覆盖。";
        }
        if (StringUtils.equals(role, "provider")) {
            description += " 提供者返回类型样本，监听端口由系统分配。";
        }
        return description;
    }

    private String dependencies(String protocol, ExampleRegistry registry) {
        String version = ExampleTemplates.version();
        List<String> dependencies = new ArrayList<>();
        dependencies.add("`net.hasor:meteor:" + version + "`");
        if (!StringUtils.equals(protocol, "rsf")) {
            String module = switch (protocol) {
                case "thrift-http" -> "thrift";
                case "oncrpc-udp" -> "oncrpc";
                case "grpc-web" -> "grpc";
                default -> protocol;
            };
            dependencies.add("`net.hasor:meteor-protocol-" + module + ":" + version + "`");
        }
        if (registry != null) {
            dependencies.add("`net.hasor:meteor-registry-" + registry.getType() + ":" + version + "`");
            if (StringUtils.equals(registry.getType(), "jdbc")) {
                dependencies.add("`org.apache.commons:commons-dbcp2:2.12.0`");
                String driver = switch (StringUtils.defaultIfBlank(registry.getDatabase(), "")) {
                    case "h2" -> "com.h2database:h2:2.3.232";
                    case "mysql" -> "com.mysql:mysql-connector-j:8.4.0";
                    case "postgresql" -> "org.postgresql:postgresql:42.7.4";
                    case "sqlserver" -> "com.microsoft.sqlserver:mssql-jdbc:10.2.1.jre8";
                    case "oracle" -> "com.oracle.database.jdbc:ojdbc8:21.1.0.0";
                    default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_NO_DRIVER_TEMPLATE_IS_AVAILABLE_FOR);
                };
                dependencies.add("`" + driver + "`");
            }
        }
        return String.join("、", dependencies);
    }

    private static String literal(String value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_GENERATE_A_JAVA_LITERAL);
        }
    }
}
