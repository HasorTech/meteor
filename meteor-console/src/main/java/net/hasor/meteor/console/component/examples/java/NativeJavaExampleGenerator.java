/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.java;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.ObjectUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.component.examples.nativeclient.HttpExampleGenerator;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;

/** 原生 Java 客户端只引用对应 RPC SDK 和业务 API，不引入 Meteor。 */
public final class NativeJavaExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (StringUtils.equals(protocol, "triple") && target.getEndpoint().getDescription().getTypes().isEmpty()) {
            return this.triple(target, address);
        }
        if (!Set.of("hessian", "hprose", "jsonrpc", "xmlrpc", "dubbo").contains(protocol)) {
            return List.of();
        }

        ExampleConnections.validate(target);
        JavaContractSources source = JavaContractSources.forExample(target.getDescription());
        var method = target.getDescription().getMethods().get(0);
        var contract = target.getEndpoint().getDescription();
        var operation = contract.getMethods().stream().filter(item -> ObjectUtils.equals(method.getJavaSignature(), item.get("javaSignature"))).findFirst().orElseThrow(() -> new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ENDPOINT_HAS_NOT_REPORTED_THE));
        String wireName = Objects.toString(operation.get("name"), method.getJavaName());
        String http = this.httpAddress(target, address);
        String nativeArguments = source.invocationArguments();
        if (Set.of("xmlrpc", "jsonrpc", "dubbo").contains(protocol)) {
            var call = HttpExampleGenerator.call(target.getDescription(), contract, operation, protocol);
            List<String> arguments = new ArrayList<>();
            for (JsonNode argument : JsonUtils.valueToTree(call.get("args"))) {
                arguments.add(this.value(argument));
            }
            nativeArguments = String.join(", ", arguments);
        }

        String body;
        String imports;
        List<String> dependencies = new ArrayList<>();
        switch (protocol) {
            case "jsonrpc" -> {
                imports = "import com.googlecode.jsonrpc4j.JsonRpcHttpClient;\nimport java.net.URI;";
                dependencies.add("com.github.briandilley.jsonrpc4j:jsonrpc4j:1.7");
                body = "        var client = new JsonRpcHttpClient(URI.create(" + literal(http) + ").toURL());\n" + "        Object result = client.invoke(" + literal(contract.getName() + "." + wireName) + ", new Object[] { " + nativeArguments + " }, Object.class);\n" + "        System.out.println(new ObjectMapper().writeValueAsString(result));";
            }
            case "xmlrpc" -> {
                imports = "import org.apache.xmlrpc.client.XmlRpcClient;\nimport org.apache.xmlrpc.client.XmlRpcClientConfigImpl;\nimport java.net.URI;";
                dependencies.add("org.apache.xmlrpc:xmlrpc-client:3.1.3");
                body = "        var config = new XmlRpcClientConfigImpl();\n" + "        config.setServerURL(URI.create(" + literal(http) + ").toURL());\n" + "        config.setConnectionTimeout(5000);\n        config.setReplyTimeout(5000);\n" + "        var client = new XmlRpcClient();\n        client.setConfig(config);\n" + "        Object result = client.execute(" + literal(contract.getName() + "." + wireName) + ", new Object[] { " + nativeArguments + " });\n        System.out.println(new ObjectMapper().writeValueAsString(result));";
            }
            case "hprose" -> {
                imports = "import hprose.client.HproseHttpClient;\nimport hprose.util.concurrent.Threads;";
                dependencies.add("org.hprose:hprose-java:2.0.38");
                body = "        var client = new HproseHttpClient(" + literal(http) + ");\n        try {\n" + "            Object result = client.invoke(" + literal(wireName) + ", new Object[] { " + nativeArguments + " });\n            System.out.println(new ObjectMapper().writeValueAsString(result));\n" + "        } finally {\n            client.close();\n            Threads.runShutdownHandler();\n        }";
            }
            case "hessian" -> {
                if (!StringUtils.equals(wireName, method.getJavaName())) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_NATIVE_HESSIAN_PROXY_REQUIRES_A);
                }
                imports = "import com.caucho.hessian.client.HessianProxyFactory;";
                dependencies.add("com.caucho:hessian:4.0.66");
                body = "        var factory = new HessianProxyFactory();\n        factory.setHessian2Request(true);\n" + "        factory.setConnectTimeout(5000);\n        factory.setReadTimeout(5000);\n" + "        var service = (" + source.exampleType() + ") factory.create(" + source.exampleType() + ".class, " + literal(http) + ");\n        " + source.invocationStatements("service");
            }
            default -> {
                imports = "import org.apache.dubbo.config.ApplicationConfig;\nimport org.apache.dubbo.config.ReferenceConfig;\n" + "import org.apache.dubbo.config.RegistryConfig;\nimport org.apache.dubbo.config.bootstrap.DubboBootstrap;\nimport org.apache.dubbo.rpc.service.GenericService;";
                dependencies.add("org.apache.dubbo:dubbo:3.3.6");
                String url = StringUtils.defaultIfBlank(address, ExampleConnections.ADDRESS_PLACEHOLDER);
                body = "        var application = new ApplicationConfig(\"native-debug\");\n" + "        application.setQosEnable(false);\n" + "        var reference = new ReferenceConfig<GenericService>();\n" + "        reference.setInterface(" + literal(contract.getName()) + ");\n        reference.setGeneric(\"true\");\n" + "        reference.setGroup(" + literal(target.getService().getGroup()) + ");\n" + "        reference.setVersion(" + literal(target.getService().getVersion()) + ");\n" + "        reference.setUrl(" + literal(url + "/" + contract.getName() + "?serialization=hessian2")
                        + ");\n" + "        reference.setScope(\"remote\");\n" + "        var bootstrap = DubboBootstrap.getInstance().application(application)\n" + "                .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE)).reference(reference);\n" + "        try {\n            bootstrap.start();\n            var service = reference.get();\n            " + "System.out.println(service.$invoke(" + literal(wireName) + ", " + this.parameterTypes(target) + ", new Object[] { " + nativeArguments + " }));\n        } finally {\n            bootstrap.destroy();\n        }";
            }
        }
        if (StringUtils.equals(protocol, "hessian") && body.contains("System.out.println((Object) ")) {
            body = body.replace("System.out.println((Object) ", "System.out.println(new ObjectMapper().writeValueAsString(");
            int end = body.lastIndexOf(");");
            body = body.substring(0, end) + ")" + body.substring(end);
        }
        if (!StringUtils.equals(protocol, "dubbo")) {
            dependencies.add("com.fasterxml.jackson.core:jackson-databind:2.18.4");
            imports += "\nimport com.fasterxml.jackson.databind.ObjectMapper;";
        }
        String code = ExampleTemplates.render("java-sdk/ExampleMain.java.tpl", Map.of("imports", imports, "body", body));
        String markdown = ExampleTemplates.gradleDependencies(dependencies);
        if (StringUtils.equals(protocol, "hessian")) {
            markdown += "\n\n" + ExampleTemplates.codeBlock("java", source.sdkInterface(source.exampleType()));
        }
        markdown += "\n\n" + ExampleTemplates.codeBlock("java", code);
        if (StringUtils.equals(protocol, "hprose")) {
            markdown += "\n\n" + ExampleTemplates.codeBlock("bash", "java --add-opens=java.base/java.io=ALL-UNNAMED -cp '<CLASSPATH>' ExampleMain");
        }
        return List.of(new ExampleDocument("java-sdk-direct", "java-sdk", "direct", markdown));
    }

    private List<ExampleDocument> triple(ExampleTarget target, String address) {
        ExampleConnections.validate(target);
        var source = JavaContractSources.forExample(target.getDescription());
        var method = target.getDescription().getMethods().get(0);
        var contract = target.getEndpoint().getDescription();
        var operation = contract.getMethods().stream().filter(item -> ObjectUtils.equals(method.getJavaSignature(), item.get("javaSignature"))).findFirst().orElseThrow();
        URI uri = URI.create(target.getAddress());
        String path = target.getEndpoint().getContextPath();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("arguments", source.invocationArguments());
        List<String> types = new ArrayList<>();
        method.getParameters().stream().sorted(Comparator.comparingInt(item -> item.getArgIndex())).forEach(parameter -> types.add(literal(target.getDescription().getTypes().get(parameter.getType()).getJavaType())));
        values.put("types", String.join(", ", types));
        values.put("method", literal(path.replaceAll("^/|/$", "") + "/" + contract.getName() + "/" + operation.get("name")));
        values.put("host", literal(StringUtils.isBlank(address) ? ExampleConnections.ADDRESS_PLACEHOLDER : uri.getHost()));
        values.put("port", Integer.toString(uri.getPort()));
        values.put("group", literal(target.getService().getGroup()));
        values.put("version", literal(target.getService().getVersion()));
        String code = ExampleTemplates.render("triple/java.java.tpl", values);
        String dependencies = ExampleTemplates.gradleDependencies(List.of(
                "io.grpc:grpc-netty-shaded:1.84.1", "io.grpc:grpc-stub:1.84.1",
                "com.google.protobuf:protobuf-java:4.33.0", "com.caucho:hessian:4.0.66",
                "com.fasterxml.jackson.core:jackson-databind:2.18.4"));
        return List.of(new ExampleDocument("java-sdk-direct", "java-sdk", "direct",
                dependencies + "\n\n" + ExampleTemplates.codeBlock("java", code)));
    }

    private String parameterTypes(ExampleTarget target) {
        List<String> types = new ArrayList<>();
        var method = target.getDescription().getMethods().get(0);
        method.getParameters().stream().sorted(Comparator.comparingInt(item -> item.getArgIndex())).forEach(parameter -> types.add(literal(target.getDescription().getTypes().get(parameter.getType()).getJavaType())));
        return "new String[] { " + String.join(", ", types) + " }";
    }

    private String value(JsonNode node) {
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            node.fields().forEachRemaining(field -> fields.add("java.util.Map.entry(" + literal(field.getKey()) + ", " + this.value(field.getValue()) + ")"));
            return "java.util.Map.ofEntries(" + String.join(", ", fields) + ")";
        }
        if (node.isArray()) {
            List<String> fields = new ArrayList<>();
            node.forEach(field -> fields.add(this.value(field)));
            return "new Object[] { " + String.join(", ", fields) + " }";
        }
        return node.toString();
    }

    private String httpAddress(ExampleTarget target, String address) {
        if (StringUtils.isBlank(address)) {
            return ExampleConnections.ADDRESS_PLACEHOLDER;
        }
        for (var endpoints : target.getEndpoint().getEndpoints().values()) {
            if (StringUtils.isNotBlank(endpoints.get("http"))) {
                return endpoints.get("http");
            }
        }
        return address;
    }

    private static String literal(String text) {
        try {
            return JsonUtils.writeValueAsString(text);
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_ENCODE_THE_JAVA_EXAMPLE);
        }
    }
}
