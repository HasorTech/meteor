/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;

/** 从端点发布的契约读取完整描述，供各语言的示例共用。 */
public final class ProtocolDescriptor {
    private ProtocolDescriptor() {
    }

    public static String markdown(String protocol, MetProtocolDescription contract) {
        try {
            return switch (protocol) {
                case "soap" -> soap(contract);
                case "grpc", "grpc-web", "connect", "triple" -> protobuf(contract);
                case "thrift", "thrift-http" -> ExampleTemplates.codeBlock("thrift service.thrift", BinaryContracts.thrift(contract));
                case "oncrpc", "oncrpc-udp" -> ExampleTemplates.codeBlock("c service.x", BinaryContracts.oncrpc(contract));
                default -> "";
            };
        } catch (ErrorMessageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ErrorMessageException(failure, ConsoleMessageKey.ERROR_COULD_NOT_GENERATE_AN_INVOCATION_DOCUMENT, failure);
        }
    }

    private static String protobuf(MetProtocolDescription contract) throws Exception {
        if (!(contract.getExtensions().get("protobufDescriptors") instanceof String encoded)) {
            return "";
        }
        var schema = GrpcProtoSchema.fromDescriptorSet(Base64.getDecoder().decode(encoded));
        var service = schema.service(contract.getName());
        if (service == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_EXAMPLE_MISSING_ENDPOINT_CONTRACT, contract.getName(), "Protobuf");
        }
        return new GrpcExampleContract(service, service.getMethods()).markdown();
    }

    private static String soap(MetProtocolDescription contract) throws XMLStreamException {
        JsonNode documents = JsonUtils.valueToTree(contract.getExtensions().get("documents"));
        String entry = documents.path("entry").asText();
        JsonNode files = documents.path("files");
        if (!files.path(entry).isTextual()) {
            return "";
        }

        var xml = XMLInputFactory.newFactory();
        xml.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xml.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        List<String> blocks = new ArrayList<>();
        blocks.add(xmlFile(xml, entry, files.path(entry).asText()));
        for (var iterator = files.fields(); iterator.hasNext(); ) {
            var file = iterator.next();
            if (!StringUtils.equals(entry, file.getKey()) && file.getValue().isTextual()) {
                blocks.add(xmlFile(xml, file.getKey(), file.getValue().asText()));
            }
        }
        return String.join("\n\n", blocks);
    }

    private static String xmlFile(XMLInputFactory xml, String name, String encoded) throws XMLStreamException {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        XMLStreamReader reader = xml.createXMLStreamReader(new ByteArrayInputStream(bytes));
        Charset charset = StandardCharsets.UTF_8;
        try {
            if (reader.getEncoding() != null) {
                charset = Charset.forName(reader.getEncoding());
            }
        } finally {
            reader.close();
        }
        String source = new String(bytes, charset);
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        return ExampleTemplates.codeBlock("xml " + name.replaceAll("[\\r\\n`]", "_"), source);
    }
}
