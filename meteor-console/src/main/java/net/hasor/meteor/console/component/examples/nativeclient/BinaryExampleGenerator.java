/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE.txt or https://www.apache.org/licenses/LICENSE-2.0
 */
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
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 二进制原生客户端直接使用导出的 wire 契约，不依赖用户的 Java 接口包。 */
public final class BinaryExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        String protocol = target.getEndpoint().getProtocolName();
        if (Set.of("thrift", "thrift-http", "oncrpc", "oncrpc-udp").contains(protocol)) {
            ExampleConnections.validate(target);
        }
        if (Set.of("thrift", "thrift-http").contains(protocol)) {
            return List.of(this.thrift(target, address));
        }
        if (Set.of("oncrpc", "oncrpc-udp").contains(protocol)) {
            ExampleDocument document = this.oncrpc(target, address);
            return List.of(document, new ExampleDocument("cpp-direct", "cpp", "direct", document.getMarkdown()));
        }
        return List.of();
    }

    private ExampleDocument thrift(ExampleTarget target, String providedAddress) {
        MetProtocolDescription binding = this.binding(target);
        String contract = BinaryContracts.thrift(binding);
        Object framed = binding.getExtensions().get("framed");
        if (StringUtils.equals(target.getEndpoint().getProtocolName(), "thrift") && !(framed instanceof Boolean)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_THRIFT_ENDPOINT_HAS_NOT_REPORTED_2);
        }
        if (StringUtils.isNotBlank(providedAddress)) {
            this.address(providedAddress);
        }
        JsonNode types = JsonUtils.valueToTree(binding.getTypes());
        JsonNode selected = null;
        String arguments = null;
        IllegalArgumentException lastFailure = null;
        for (JsonNode method : JsonUtils.valueToTree(binding.getMethods())) {
            try {
                StringBuilder values = new StringBuilder();
                for (JsonNode parameter : method.path("parameters")) {
                    if (!values.isEmpty()) {
                        values.append(", ");
                    }
                    values.append(this.pythonValue(types, parameter.path("type").asText(), new HashSet<>()));
                }
                selected = method;
                arguments = values.toString();
                break;
            } catch (IllegalArgumentException failure) {
                lastFailure = failure;
            }
        }
        if (selected == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_COULD_NOT_CONSTRUCT_VALID_SAMPLE_ARGUMENTS, lastFailure == null ? "" : lastFailure);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("service", BinaryContracts.identifier(BinaryContracts.thriftServiceName(binding)));
        values.put("address", this.string(StringUtils.defaultIfBlank(providedAddress, "请填写服务地址")));
        values.put("transport", Boolean.TRUE.equals(framed) ? "TFramedTransport" : "TBufferedTransport");
        String protocol = "TBinaryProtocol.TBinaryProtocol(transport)";
        if (Boolean.TRUE.equals(binding.getExtensions().get("multiplexed"))) {
            Object multiplexed = binding.getExtensions().get("multiplexedName");
            if (!(multiplexed instanceof String name) || StringUtils.isBlank(name)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_THRIFT_CONTRACT_IS_MISSING_THE);
            }
            protocol = "TMultiplexedProtocol.TMultiplexedProtocol(" + protocol + ", " + this.string(name) + ")";
        }
        values.put("protocol", protocol);
        values.put("method", this.string(selected.path("name").asText()));
        values.put("arguments", arguments);
        String connection = "TSocket.TSocket(unquote(address.hostname), address.port)";
        if (StringUtils.equals(target.getEndpoint().getProtocolName(), "thrift-http")) {
            connection = "THttpClient.THttpClient(" + this.string(HttpRequestExampleGenerator.endpoint(target, providedAddress, "http")) + ")";
        }
        values.put("connection", connection);
        return this.document("python", "thrift", contract, ExampleTemplates.render("thrift/consumer.py", values), "Python 3；依赖 `thrift==0.22.0`，并使用下方 service.thrift 生成的 Python 绑定（meteor_example 包）。");
    }

    private ExampleDocument oncrpc(ExampleTarget target, String providedAddress) {
        MetProtocolDescription binding = this.binding(target);
        BinaryContracts.XdrWriter writer = new BinaryContracts.XdrWriter(binding);
        String contract = writer.write();
        URI address = null;
        if (StringUtils.isNotBlank(providedAddress)) {
            address = this.address(providedAddress);
        }
        if (address != null && address.getHost().contains(":")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_C_ONC_RPC_EXAMPLE_USES);
        }
        JsonNode selected = JsonUtils.valueToTree(binding.getMethods()).get(0);
        String input = selected.path("requestType").asText();
        String output = selected.path("responseType").asText();
        String inputType = writer.name(input);
        String outputType = writer.name(output);
        Map<String, String> values = new LinkedHashMap<>();
        if (address == null) {
            values.put("host", this.string("请填写服务地址"));
            values.put("port", "0");
        } else {
            values.put("host", this.string(address.getHost()));
            values.put("port", Integer.toString(address.getPort()));
        }
        values.put("program", String.valueOf(binding.getExtensions().get("program")));
        values.put("version", String.valueOf(binding.getExtensions().get("version")));
        values.put("procedure", selected.path("procedureId").asText());
        StringBuilder definitions = new StringBuilder();
        for (Map<String, Object> method : binding.getMethods()) {
            definitions.append("#undef ").append(BinaryContracts.identifier((String) method.get("name"))).append('\n');
        }
        definitions.append("#undef ").append(BinaryContracts.identifier(binding.getName())).append('\n');
        definitions.append("#undef ").append(BinaryContracts.identifier(binding.getName())).append("_VERSION\n");
        values.put("undefinitions", definitions.toString());
        StringBuilder initialize = new StringBuilder();
        if (StringUtils.equals(inputType, "void")) {
            values.put("request", "    char request = 0;\n");
            values.put("requestXdr", "xdr_void");
        } else {
            initialize.append("    ").append(inputType).append(" request = {0};\n");
            this.cValue(writer, input, "request", initialize, 0);
            values.put("request", initialize.toString());
            values.put("requestXdr", "xdr_" + inputType);
        }
        if (StringUtils.equals(outputType, "void")) {
            values.put("response", "    char response = 0;\n");
            values.put("responseXdr", "xdr_void");
            values.put("printResult", "    puts(\"RPC completed (void)\");\n");
        } else {
            values.put("response", "    " + outputType + " response = {0};\n");
            values.put("responseXdr", "xdr_" + outputType);
            values.put("printResult", ExampleTemplates.render("oncrpc/print-result.c", Map.of("type", outputType)));
        }
        if (StringUtils.equals(target.getEndpoint().getTransport(), "udp")) {
            values.put("connect", "    struct timeval retry = {5, 0};\n" + "    CLIENT *client = clntudp_create(&address, PROGRAM_ID, VERSION_ID, retry, &socket_fd);\n");
        } else if (StringUtils.equals(target.getEndpoint().getTransport(), "tcp")) {
            String connect = "    CLIENT *client = clnttcp_create(&address, PROGRAM_ID, VERSION_ID, &socket_fd, 0, 0);\n";
            values.put("connect", connect);
        } else {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_ONC_RPC_ENDPOINT_HAS_NOT);
        }
        return this.document("c", "xdr", contract, ExampleTemplates.render("oncrpc/consumer.c", values), "依赖 libtirpc，以及由下方 service.x 经 rpcgen 生成的 service.h、service_xdr.c。");
    }

    private ExampleDocument document(String language, String contractLanguage, String contract, String code, String dependency) {
        String markdown = ExampleTemplates.render("binary-document.md.tpl", Map.of("dependency", dependency, "code", ExampleTemplates.codeBlock(language, code), "contract", ExampleTemplates.codeBlock(contractLanguage, contract)));
        return new ExampleDocument(language + "-direct", language, "direct", markdown);
    }

    private void cValue(BinaryContracts.XdrWriter writer, String reference, String expression, //
            StringBuilder text, int depth) {
        if (depth > 24) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_XDR_TYPE_NESTING_IS_TOO);
        }
        JsonNode type = BinaryContracts.required(writer.types(), reference);
        String kind = type.path("kind").asText();
        if (StringUtils.equals(kind, "string")) {
            long length = type.path("length").asLong(-1);
            String sample = length == 0 ? "" : "meteor";
            if (length > 0 && length < sample.length()) {
                sample = sample.substring(0, (int) length);
            }
            text.append("    ").append(expression).append(" = strdup(").append(this.string(sample)).append(");\n");
        } else if (StringUtils.equals(kind, "struct")) {
            for (JsonNode member : type.path("members")) {
                this.cValue(writer, member.path("type").asText(), expression + "." + BinaryContracts.identifier(member.path("name").asText()), text, depth + 1);
            }
        } else if (StringUtils.equals(kind, "enum")) {
            JsonNode first = type.path("values").get(0);
            if (first == null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_XDR_ENUM_HAS_NO_VALID);
            }
            text.append("    ").append(expression).append(" = ").append(first.path("fieldId").asInt()).append(";\n");
        } else if (StringUtils.equals(kind, "array") && type.path("fixed").asBoolean()) {
            long length = type.path("length").asLong(-1);
            if (length < 0 || length > 10000) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_FIXED_LENGTH_XDR_ARRAY_IS_2);
            }
            String index = "i" + depth;
            text.append("    for (size_t ").append(index).append(" = 0; ").append(index).append(" < ");
            text.append(length).append("; ").append(index).append("++) {\n");
            this.cValue(writer, type.path("elementType").asText(), expression + "[" + index + "]", text, depth + 1);
            text.append("    }\n");
        } else if (StringUtils.equals(kind, "union")) {
            JsonNode branch = type.path("members").get(0);
            if (branch == null || branch.path("cases").isEmpty()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_XDR_UNION_HAS_NO_EXPLICIT);
            }
            String discriminator = BinaryContracts.identifier(type.path("discriminator").path("name").asText());
            text.append("    ").append(expression).append('.').append(discriminator).append(" = ");
            text.append(branch.path("cases").get(0).asLong()).append(";\n");
            String memberType = branch.path("type").asText();
            if (!StringUtils.equals(writer.name(memberType), "void")) {
                this.cValue(writer, memberType, expression + "." + writer.name(reference) + "_u." + BinaryContracts.identifier(branch.path("name").asText()), text, depth + 1);
            }
        }
        // optional 的 NULL、变长数组的空列表以及数值 0 均为合法 XDR 值，无需额外赋值。
    }

    private String pythonValue(JsonNode types, String reference, Set<String> parents) {
        JsonNode type = BinaryContracts.required(types, reference);
        if (!type.path("values").isEmpty()) {
            return Integer.toString(type.path("values").get(0).path("fieldId").asInt());
        }
        int wire = type.path("wireType").asInt(-1);
        return switch (wire) {
            case 2 -> "False";
            case 3, 6, 8, 10 -> "1";
            case 4 -> "1.0";
            case 11 -> type.path("binary").asBoolean() ? "b'Meteor'" : "'meteor'";
            case 13 -> "{}";
            case 14 -> "set()";
            case 15 -> "[]";
            case 16 -> "uuid.UUID('00000000-0000-0000-0000-000000000001')";
            case 12 -> this.pythonStruct(types, reference, type, parents);
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_COULD_NOT_CONSTRUCT_THRIFT_ARGUMENTS, reference);
        };
    }

    private String pythonStruct(JsonNode types, String reference, JsonNode type, Set<String> parents) {
        if (!parents.add(reference)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_REQUIRED_RECURSIVE_THRIFT_TYPE_HAS, reference);
        }
        String name = BinaryContracts.identifier(type.path("name").asText());
        StringBuilder value = new StringBuilder("ttypes.").append(name);
        value.append("(**{");
        int count = 0;
        for (JsonNode field : type.path("fields")) {
            boolean required = StringUtils.equals(field.path("requirement").asText(), "required");
            if (!required && parents.contains(field.path("type").asText())) {
                continue;
            }
            if (count++ > 0) {
                value.append(", ");
            }
            value.append(this.string(field.path("name").asText())).append(": ");
            value.append(this.pythonValue(types, field.path("type").asText(), new HashSet<>(parents)));
            if (type.path("union").asBoolean()) {
                break;
            }
        }
        return value.append("})").toString();
    }

    private MetProtocolDescription binding(ExampleTarget target) {
        MetProtocolDescription binding = target.getEndpoint().getDescription();
        if (binding == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_PROVIDER_ENDPOINT_HAS_NOT_REPORTED);
        }
        return binding;
    }

    private URI address(String providedAddress) {
        URI address = URI.create(providedAddress);
        if (address.getHost() == null || address.getPort() < 1) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_SERVICE_DOES_NOT_HAVE_A);
        }
        return address;
    }

    private String string(String value) {
        try {
            return JsonUtils.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_ENCODE_A_STRING_IN);
        }
    }
}
