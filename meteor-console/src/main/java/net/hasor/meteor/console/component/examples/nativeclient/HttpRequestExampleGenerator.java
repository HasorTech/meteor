/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.util.*;
import javax.xml.namespace.QName;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;

/** HTTP 线上请求文档；同一请求正文在各语言中使用普通 HTTP 客户端发送。 */
public final class HttpRequestExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (!List.of("jsonrpc", "xmlrpc", "soap", "triple").contains(protocol)) {
            return List.of();
        }

        ExampleConnections.validate(target);
        var binding = target.getEndpoint().getDescription();
        Map<String, Object> method = binding.getMethods().get(0);
        Map<String, String> headers = new LinkedHashMap<>();
        String body;
        if (StringUtils.equals(protocol, "triple")) {
            var call = HttpExampleGenerator.call(target.getDescription(), binding, method, protocol);
            String url = endpoint(target, address, "http");
            if (StringUtils.isNotBlank(address)) {
                url += "/" + binding.getName() + "/" + method.get("name");
            }
            return documents(url, JsonUtils.valueToTree(call.get("args")).toPrettyString(), Map.of("Content-Type", "application/json", "tri-service-group", target.getService().getGroup(), "tri-service-version", target.getService().getVersion()), List.of("python", "javascript", "go", "c", "cpp", "browser", "shell"));
        }
        if (StringUtils.equals(protocol, "soap")) {
            body = this.soap(target, JsonUtils.valueToTree(method));
            String version = String.valueOf(binding.getExtensions().get("version"));
            String action = String.valueOf(method.getOrDefault("soapAction", ""));
            if (version.contains("1.2")) {
                headers.put("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + action + "\"");
            } else {
                headers.put("Content-Type", "text/xml; charset=utf-8");
                headers.put("SOAPAction", "\"" + action + "\"");
            }
        } else {
            Map<String, Object> call = HttpExampleGenerator.call(target.getDescription(), binding, method, protocol);
            if (StringUtils.equals(protocol, "jsonrpc")) {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("jsonrpc", "2.0");
                envelope.put("id", 1);
                envelope.put("method", call.get("method"));
                envelope.put("params", call.get("args"));
                body = JsonUtils.valueToTree(envelope).toPrettyString();
                headers.put("Content-Type", "application/json");
            } else {
                StringBuilder xml = new StringBuilder("<methodCall><methodName>");
                xml.append(escape((String) call.get("method"))).append("</methodName><params>");
                for (Object argument : (List<?>) call.get("args")) {
                    xml.append("<param>").append(this.xmlRpc(JsonUtils.valueToTree(argument))).append("</param>");
                }
                body = xml.append("</params></methodCall>").toString();
                headers.put("Content-Type", "text/xml");
            }
        }
        String url = endpoint(target, address, "http");
        List<ExampleDocument> result = documents(url, body, headers, List.of("python", "javascript", "go", "c", "cpp", "browser", "shell"));
        if (StringUtils.equals(protocol, "soap")) {
            boolean soap12 = String.valueOf(binding.getExtensions().get("version")).contains("1.2");
            Map<String, String> values = new LinkedHashMap<>();
            values.put("url", literal(url));
            values.put("body", literal(body));
            values.put("soapAction", literal(String.valueOf(method.getOrDefault("soapAction", ""))));
            values.put("soapBinding", soap12 ? "SOAPBinding.SOAP12HTTP_BINDING" : "SOAPBinding.SOAP11HTTP_BINDING");
            values.put("soapVersion", soap12 ? "SOAPConstants.SOAP_1_2_PROTOCOL" : "SOAPConstants.SOAP_1_1_PROTOCOL");
            String code = ExampleTemplates.render("http-request/consumer.java.tpl", values);
            String dependencies = ExampleTemplates.gradleDependencies(List.of("com.sun.xml.ws:jaxws-rt:2.3.7"));
            result.add(new ExampleDocument("java-sdk-direct", "java-sdk", "direct", dependencies + "\n\n" + ExampleTemplates.codeBlock("java", code)));
        }
        return result;
    }

    static String endpoint(ExampleTarget target, String address, String type) {
        if (StringUtils.isBlank(address)) {
            return ExampleConnections.ADDRESS_PLACEHOLDER;
        }
        for (var endpoints : target.getEndpoint().getEndpoints().values()) {
            if (StringUtils.isNotBlank(endpoints.get(type))) {
                return endpoints.get(type);
            }
        }
        throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_PROVIDER_HAS_NOT_REPORTED_A_2, type);
    }

    static List<ExampleDocument> documents(String url, String body, Map<String, String> headers, List<String> languages) {
        List<ExampleDocument> result = new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("url", literal(url));
        values.put("body", literal(body));
        values.put("headers", literal(headers));
        StringBuilder curl = new StringBuilder();
        StringBuilder shell = new StringBuilder();
        headers.forEach((name, value) -> {
            curl.append("    headers = curl_slist_append(headers, ").append(literal(name + ": " + value)).append(");\n");
            shell.append("  --header ").append(quote(name + ": " + value)).append(" \\\n");
        });
        values.put("curlHeaders", curl.toString().stripTrailing());
        values.put("shellHeaders", shell.toString().stripTrailing());
        values.put("shellBody", quote(body));
        values.put("shellUrl", quote(url));
        for (String language : languages) {
            String extension = switch (language) {
                case "python" -> "py";
                case "go" -> "go";
                case "c", "cpp" -> "c";
                case "shell" -> "sh";
                default -> "mjs";
            };
            String syntax = switch (language) {
                case "browser" -> "javascript";
                case "shell" -> "bash";
                default -> language;
            };
            String code = ExampleTemplates.render("http-request/consumer." + extension + ".tpl", values);
            String markdown = ExampleTemplates.codeBlock(syntax, code);
            if (StringUtils.equals(language, "c")) {
                markdown = ExampleTemplates.codeBlock("bash", "cc consumer.c -o consumer -lcurl") + "\n\n" + markdown;
            } else if (StringUtils.equals(language, "cpp")) {
                markdown = ExampleTemplates.codeBlock("bash", "c++ consumer.cpp -o consumer -lcurl") + "\n\n" + markdown;
            }
            result.add(new ExampleDocument(language + "-direct", language, "direct", markdown));
        }
        return result;
    }

    private String xmlRpc(JsonNode value) {
        String body;
        if (value.isObject()) {
            StringBuilder object = new StringBuilder("<struct>");
            value.fields().forEachRemaining(field -> object.append("<member><name>").append(escape(field.getKey())).append("</name>").append(this.xmlRpc(field.getValue())).append("</member>"));
            body = object.append("</struct>").toString();
        } else if (value.isArray()) {
            StringBuilder array = new StringBuilder("<array><data>");
            value.forEach(item -> array.append(this.xmlRpc(item)));
            body = array.append("</data></array>").toString();
        } else if (value.isIntegralNumber()) {
            body = "<int>" + value.asText() + "</int>";
        } else if (value.isNumber()) {
            body = "<double>" + value.asText() + "</double>";
        } else if (value.isBoolean()) {
            body = "<boolean>" + (value.asBoolean() ? "1" : "0") + "</boolean>";
        } else {
            body = "<string>" + escape(value.asText()) + "</string>";
        }
        return "<value>" + body + "</value>";
    }

    private String soap(ExampleTarget target, JsonNode method) {
        String version = String.valueOf(target.getEndpoint().getDescription().getExtensions().get("version"));
        String namespace = "http://schemas.xmlsoap.org/soap/envelope/";
        if (version.contains("1.2")) {
            namespace = "http://www.w3.org/2003/05/soap-envelope";
        }
        StringBuilder body = new StringBuilder();
        String wrapper = method.path("requestWrapper").asText("");
        for (JsonNode parameter : method.path("parameters")) {
            int index = parameter.path("argIndex").asInt();
            var declaration = target.getDescription().getMethods().stream().filter(item -> StringUtils.equals(item.getJavaSignature(), method.path("javaSignature").asText())).findFirst().orElseThrow();
            var field = declaration.getParameters().stream().filter(item -> item.getArgIndex() == index).findFirst().orElseThrow();
            String name = parameter.path("element").asText();
            if (StringUtils.isBlank(name)) {
                name = parameter.path("name").asText();
            }
            body.append(this.soapElement(target, name, field.getType(), new HashSet<>()));
        }
        if (StringUtils.isNotBlank(wrapper)) {
            body = new StringBuilder(element(wrapper, body.toString()));
        }
        return "<s:Envelope xmlns:s=\"" + namespace + "\"><s:Body>" + body + "</s:Body></s:Envelope>";
    }

    private String soapElement(ExampleTarget target, String name, String type, java.util.Set<String> parents) {
        var graph = target.getEndpoint().getDescription().getTypes();
        if (!graph.containsKey(type)) {
            Object sample = HttpExampleGenerator.sample(target.getDescription(), type, "soap", new HashSet<>());
            return element(name, escape(String.valueOf(sample)));
        }
        if (!parents.add(type)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_REQUIRED_RECURSIVE_SOAP_ARGUMENT_NEEDS);
        }
        JsonNode schema = JsonUtils.valueToTree(graph.get(type));
        StringBuilder attributes = new StringBuilder();
        StringBuilder children = new StringBuilder();
        for (JsonNode property : schema.path("properties")) {
            String fieldName = property.path("name").asText();
            String fieldNamespace = property.path("namespace").asText();
            String fieldType = property.path("type").asText();
            if (property.path("attribute").asBoolean() || property.path("text").asBoolean()) {
                Object sample = HttpExampleGenerator.sample(target.getDescription(), fieldType, "soap", new HashSet<>());
                if (property.path("text").asBoolean()) {
                    children.append(escape(String.valueOf(sample)));
                } else if (StringUtils.isBlank(fieldNamespace)) {
                    attributes.append(' ').append(fieldName).append("=\"").append(escape(String.valueOf(sample))).append('"');
                } else {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_SOAP_NAMESPACE_ATTRIBUTE_REQUIRES_A);
                }
            } else {
                String qname = fieldName;
                if (StringUtils.isNotBlank(fieldNamespace)) {
                    qname = "{" + fieldNamespace + "}" + fieldName;
                }
                children.append(this.soapElement(target, qname, fieldType, new HashSet<>(parents)));
            }
        }
        QName qname = QName.valueOf(name);
        return "<" + qname.getLocalPart() + " xmlns=\"" + escape(qname.getNamespaceURI()) + "\"" + attributes + ">" + children + "</" + qname.getLocalPart() + ">";
    }

    private static String element(String name, String content) {
        QName qname = QName.valueOf(name);
        return "<" + qname.getLocalPart() + " xmlns=\"" + escape(qname.getNamespaceURI()) + "\">" + content + "</" + qname.getLocalPart() + ">";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }

    private static String literal(Object value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_ENCODE_THE_HTTP_DOCUMENT);
        }
    }
}
