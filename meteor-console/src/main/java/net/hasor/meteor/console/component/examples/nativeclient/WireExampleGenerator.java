/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;

/** 为没有通用动态 SDK 的语言提供一次完整线上报文调用；不会伪造业务类型或依赖生成工程。 */
public final class WireExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (!Set.of("thrift", "thrift-http", "oncrpc", "oncrpc-udp", "hprose", "hessian").contains(protocol)) {
            return List.of();
        }

        ExampleConnections.validate(target);
        var binding = target.getEndpoint().getDescription();
        JsonNode method = JsonUtils.valueToTree(binding.getMethods()).get(0);
        String operation = method.path("name").asText();
        if (protocol.startsWith("thrift")) {
            String coding = binding.getSerializeType();
            if (StringUtils.equalsIgnoreCase(coding, "compact") || StringUtils.equalsIgnoreCase(coding, "json")) {
                return List.of();
            }
            String name = operation;
            if (Boolean.TRUE.equals(binding.getExtensions().get("multiplexed"))) {
                name = binding.getExtensions().get("multiplexedName") + ":" + name;
            }
            var packet = new RequestScript();
            packet.write("I", 0x80010001);
            packet.text(name, false);
            packet.write("i", 1); // Sequence ID
            JsonNode types = JsonUtils.valueToTree(binding.getTypes());
            for (JsonNode field : method.path("parameters")) {
                this.thriftField(types, field, packet, new HashSet<>());
            }
            packet.write("B", 0);
            String contract = ExampleTemplates.codeBlock("thrift", BinaryContracts.thrift(BinaryContracts.onlyMethod(binding, binding.getMethods().get(0))));
            if (StringUtils.equals(protocol, "thrift-http")) {
                return http(HttpRequestExampleGenerator.endpoint(target, address, "http"),
                        contract + "\n\n" + packet.document("packet"), Map.of("Content-Type", "application/x-thrift"),
                        List.of("python", "javascript", "go", "c", "cpp", "browser", "shell"));
            }
            if (!Boolean.TRUE.equals(binding.getExtensions().get("framed"))) {
                return List.of();
            }
            return this.socket(target, address, contract + "\n\n" + packet.document("pack(\">I\", len(packet)) + packet"));
        }
        if (protocol.startsWith("oncrpc")) {
            var packet = new RequestScript();
            packet.write("III", 1, 0, 2); // XID, CALL, RPC version
            packet.write("III", ((Number) binding.getExtensions().get("program")).intValue(),
                    ((Number) binding.getExtensions().get("version")).intValue(), method.path("procedureId").asInt());
            packet.write("IIII", 0, 0, 0, 0); // AUTH_NONE credentials and verifier
            this.xdr(JsonUtils.valueToTree(binding.getTypes()), method.path("requestType").asText(), packet, new HashSet<>());
            String body = "packet";
            if (!StringUtils.equals(protocol, "oncrpc-udp")) {
                body = "pack(\">I\", 0x80000000 | len(packet)) + packet";
            }
            String contract = ExampleTemplates.codeBlock("xdr", BinaryContracts.oncrpc(BinaryContracts.onlyMethod(binding, binding.getMethods().get(0))));
            return this.socket(target, address, contract + "\n\n" + packet.document(body));
        }

        Map<String, Object> call = HttpExampleGenerator.call(target.getDescription(), binding, binding.getMethods().get(0), protocol);
        String mediaType = "application/" + protocol;
        if (StringUtils.equals(protocol, "hessian")) {
            mediaType = "application/x-hessian";
        }
        String name = operation;
        if (StringUtils.equals(protocol, "hprose")) {
            name = Objects.toString(call.get("method"));
        }
        String script = ExampleTemplates.render("wire/" + protocol + "-request.py.tpl", Map.of("method", literal(name)));
        String setup = ExampleTemplates.codeBlock("json arguments.json", JsonUtils.valueToTree(call.get("args")).toPrettyString())
                + "\n\n" + requestScript(script);
        return http(HttpRequestExampleGenerator.endpoint(target, address, "http"), setup,
                Map.of("Content-Type", mediaType), List.of("python", "javascript", "go", "cpp", "browser", "shell"));
    }

    static String requestScript(String source) {
        return ExampleTemplates.codeBlock("python request.py", source)
                + "\n\n" + ExampleTemplates.codeBlock("bash", "python3 request.py");
    }

    static List<ExampleDocument> http(String url, String setup, Map<String, String> headers, List<String> languages) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("url", literal(url));
        values.put("headers", literal(headers));
        boolean http2 = StringUtils.equals(headers.get("Content-Type"), "application/grpc+proto");
        values.put("curlHttpVersion", http2 ? "    curl_easy_setopt(client, CURLOPT_HTTP_VERSION, CURL_HTTP_VERSION_2_PRIOR_KNOWLEDGE);\n" : "");
        values.put("shellHttpVersion", http2 ? " --http2-prior-knowledge" : "");
        StringBuilder curl = new StringBuilder();
        StringBuilder shell = new StringBuilder();
        headers.forEach((name, value) -> {
            curl.append("    headers = curl_slist_append(headers, ").append(literal(name + ": " + value)).append(");\n");
            shell.append("  --header ").append(quote(name + ": " + value)).append(" \\\n");
        });
        values.put("curlHeaders", curl.toString().stripTrailing());
        values.put("shellHeaders", shell.toString().stripTrailing());
        values.put("shellUrl", quote(url));
        return documents("http", values, setup, languages);
    }

    private List<ExampleDocument> socket(ExampleTarget target, String address, String setup) {
        URI uri = URI.create(target.getAddress());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("host", literal(StringUtils.isBlank(address) ? ExampleConnections.ADDRESS_PLACEHOLDER : uri.getHost()));
        values.put("port", Integer.toString(uri.getPort()));
        boolean udp = StringUtils.equals(target.getEndpoint().getTransport(), "udp");
        if (udp) {
            return documents("udp", values, setup, List.of("c", "cpp"));
        }
        if (StringUtils.equals(target.getEndpoint().getProtocolName(), "thrift")) {
            return documents("framed", values, setup, List.of("python", "javascript", "go", "c", "cpp"));
        }
        return documents("record", values, setup, List.of("python", "javascript", "go", "c", "cpp"));
    }

    private static List<ExampleDocument> documents(String transport, Map<String, String> values, String setup, List<String> languages) {
        List<ExampleDocument> result = new ArrayList<>();
        for (String language : languages) {
            String extension = switch (language) {
                case "python" -> "py";
                case "go" -> "go";
                case "c", "cpp" -> "c";
                case "shell" -> "sh";
                default -> "mjs";
            };
            String syntax = language;
            if (StringUtils.equals(language, "browser")) {
                syntax = "javascript";
            } else if (StringUtils.equals(language, "shell")) {
                syntax = "bash";
            }
            String template = transport;
            Map<String, String> context = new LinkedHashMap<>(values);
            if (StringUtils.equals(transport, "http")) {
                if (StringUtils.equals(language, "browser")) {
                    context.put("jsBody", "const body = await (await fetch('./request.bin')).arrayBuffer();");
                } else if (StringUtils.equals(language, "javascript")) {
                    context.put("jsBody", "import { readFileSync } from 'node:fs';\n\nconst body = readFileSync('request.bin');");
                }
            } else {
                template = "socket";
                if (StringUtils.equals(extension, "c")) {
                    boolean udp = StringUtils.equals(transport, "udp");
                    context.put("socketKind", udp ? "SOCK_DGRAM" : "SOCK_STREAM");
                    context.put("reader", udp ? "" : ExampleTemplates.render("wire/socket-reader.c.tpl", values));
                }
                String receive = ExampleTemplates.render("wire/" + transport + "-receive." + extension + ".tpl", values);
                context.put("receive", receive.stripTrailing());
            }
            String code = ExampleTemplates.render("wire/" + template + "." + extension + ".tpl", context);
            String markdown = ExampleTemplates.codeBlock(syntax, code);
            if (StringUtils.equals(transport, "http")) {
                if (StringUtils.equals(language, "c")) {
                    markdown = ExampleTemplates.codeBlock("bash", "cc consumer.c -o consumer -lcurl") + "\n\n" + markdown;
                } else if (StringUtils.equals(language, "cpp")) {
                    markdown = ExampleTemplates.codeBlock("bash", "c++ consumer.cpp -o consumer -lcurl") + "\n\n" + markdown;
                }
            }
            result.add(new ExampleDocument(language + "-direct", language, "direct", setup + "\n\n" + markdown));
        }
        return result;
    }

    private void thriftField(JsonNode types, JsonNode field, RequestScript packet, Set<String> parents) {
        String reference = field.path("type").asText();
        JsonNode type = BinaryContracts.required(types, reference);
        if (parents.contains(reference) && !StringUtils.equals(field.path("requirement").asText(), "required")) {
            return;
        }
        packet.field(field.path("name").asText());
        packet.write("B", type.path("wireType").asInt());
        packet.write("h", field.path("fieldId").asInt());
        this.thrift(types, reference, packet, parents);
    }

    private void thrift(JsonNode types, String reference, RequestScript packet, Set<String> parents) {
        if (!parents.add(reference)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_REQUIRED_RECURSIVE_THRIFT_ARGUMENT_NEEDS);
        }
        JsonNode type = BinaryContracts.required(types, reference);
        switch (type.path("wireType").asInt()) {
            case 2, 3 -> packet.write("B", 0);
            case 4 -> packet.write("d", 1);
            case 6 -> packet.write("h", 1);
            case 8 -> {
                int value = 1;
                if (!type.path("values").isEmpty()) {
                    value = type.path("values").get(0).path("fieldId").asInt();
                }
                packet.write("i", value);
            }
            case 10 -> packet.write("q", 1);
            case 11 -> packet.text("example", false);
            case 12 -> {
                for (JsonNode field : type.path("fields")) {
                    this.thriftField(types, field, packet, new HashSet<>(parents));
                    if (type.path("union").asBoolean()) {
                        break;
                    }
                }
                packet.write("B", 0);
            }
            case 13 -> {
                packet.write("B", BinaryContracts.required(types, type.path("keyType").asText()).path("wireType").asInt());
                packet.write("B", BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt());
                packet.write("i", 0);
            }
            case 14, 15 -> {
                packet.write("B", BinaryContracts.required(types, type.path("elementType").asText()).path("wireType").asInt());
                packet.write("i", 0);
            }
            case 16 -> {
                packet.write("q", 0);
                packet.write("q", 1);
            }
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_UNSUPPORTED_THRIFT_SAMPLE_TYPE);
        }
    }

    private void xdr(JsonNode types, String reference, RequestScript packet, Set<String> parents) {
        if (!parents.add(reference)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_REQUIRED_RECURSIVE_XDR_ARGUMENT_NEEDS);
        }
        JsonNode type = BinaryContracts.required(types, reference);
        switch (type.path("kind").asText()) {
            case "int", "unsignedInt" -> packet.write("i", 1);
            case "enum" -> packet.write("i", type.path("values").get(0).path("fieldId").asInt());
            case "bool", "boolean", "optional" -> packet.write("i", 0);
            case "hyper", "unsignedHyper" -> packet.write("q", 1);
            case "float" -> packet.write("f", 1);
            case "double" -> packet.write("d", 1);
            case "void" -> {
            }
            case "string" -> packet.text("example", true);
            case "struct" -> {
                for (JsonNode member : type.path("members")) {
                    packet.field(member.path("name").asText());
                    this.xdr(types, member.path("type").asText(), packet, new HashSet<>(parents));
                }
            }
            case "array" -> {
                if (type.path("fixed").asBoolean()) {
                    int length = type.path("length").asInt();
                    if (length > 1024) {
                        throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_FIXED_LENGTH_XDR_SAMPLE_ARRAY);
                    }
                    for (int index = 0; index < length; index++) {
                        this.xdr(types, type.path("elementType").asText(), packet, new HashSet<>(parents));
                    }
                } else {
                    packet.write("i", 0);
                }
            }
            case "opaque" -> {
                int length = 0;
                if (type.path("fixed").asBoolean()) {
                    length = type.path("length").asInt();
                    if (length > 65536 || length < 0) {
                        throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_FIXED_LENGTH_XDR_OPAQUE_SAMPLE);
                    }
                } else {
                    packet.write("i", 0);
                }
                packet.zeroes(length + (4 - length % 4) % 4);
            }
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_XDR_TYPE_REQUIRES_AN_EXPLICIT_2);
        }
    }

    private static final class RequestScript {
        private final StringBuilder code = new StringBuilder();
        private boolean text;
        private boolean padded;

        void field(String name) {
            this.code.append("# ").append(name.replace("\r", " ").replace("\n", " ")).append('\n');
        }

        void write(String format, int... values) {
            this.code.append("packet += pack(\">").append(format).append("\"");
            for (int value : values) {
                this.code.append(", ");
                if (value < 0 && format.contains("I")) {
                    this.code.append("0x").append(Integer.toHexString(value));
                } else {
                    this.code.append(value);
                }
            }
            this.code.append(")\n");
        }

        void text(String value, boolean xdr) {
            this.text = true;
            this.padded = xdr;
            this.code.append("packet += text(").append(literal(value)).append(")\n");
        }

        void zeroes(int length) {
            if (length > 0) {
                this.code.append("packet += bytes(").append(length).append(")\n");
            }
        }

        String document(String body) {
            var script = new StringBuilder("from pathlib import Path\nfrom struct import pack\n\n");
            if (this.text) {
                script.append("def text(value):\n    data = value.encode(\"utf-8\")\n    return pack(\">I\", len(data)) + data");
                if (this.padded) {
                    script.append(" + bytes(-len(data) % 4)");
                }
                script.append("\n\n");
            }
            script.append("packet = bytearray()\n").append(this.code);
            script.append("Path(\"request.bin\").write_bytes(").append(body).append(")\n");
            return requestScript(script.toString());
        }
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }

    private static String literal(Object value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_ENCODE_THE_INVOCATION_DOCUMENT);
        }
    }
}
