/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.soap.definition;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import org.w3c.dom.Element;

/** WSDL and XSD definitions reconstructed exclusively from a published resource snapshot. */
public final class SoapPublishedResource extends SoapDefinitionResource {
    private final SoapDefinition               definition;
    private final SoapSchemaParser             schemas;
    private final Map<String, SoapXmlResource> files;
    private final SoapXmlResource              entry;

    public SoapPublishedResource(JsonNode documents, String style) {
        try {
            Map<String, SoapXmlResource> parsed = new LinkedHashMap<>();
            var files = documents.path("files").fields();
            while (files.hasNext()) {
                var file = files.next();
                URI location = URI.create("file:/meteor-published/").resolve(file.getKey()).normalize();
                if (!StringUtils.equals("file", location.getScheme()) || location.getAuthority() != null || location.getPath() == null || !location.getPath().startsWith("/meteor-published/")) {
                    throw new IllegalArgumentException("Invalid published SOAP resource name: " + file.getKey());
                }
                var url = location.toURL();
                byte[] bytes = Base64.getDecoder().decode(file.getValue().asText());
                parsed.put(file.getKey(), new SoapXmlResource(file.getKey(), url, bytes, SoapWsdlResource.parse(url, bytes)));
            }
            this.entry = parsed.get(documents.path("entry").asText());
            if (this.entry == null) {
                throw new IllegalArgumentException("Published SOAP WSDL entry is missing");
            }
            this.files = Map.copyOf(parsed);
            this.schemas = new SoapSchemaParser(parsed.values(), this.entry);
            this.definition = new SoapWsdlParser(parsed.values(), this.entry, this.schemas, style).parse();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid published SOAP definition", failure);
        }
    }

    @Override
    public SoapDefinition definition() {
        return this.definition;
    }

    @Override
    public ObjectNode describeSchemas() {
        return this.schemas.describeSchemas();
    }

    @Override
    public void validateBody(Element body, SoapBody binding) throws IOException {
        this.schemas.validateBody(body, binding);
    }

    @Override
    public void validatePart(Element payload, SoapPart part) throws IOException {
        this.schemas.validatePart(payload, part);
    }

    @Override
    public byte[] renderWsdl(URI endpoint) {
        return this.entry.body();
    }

    @Override
    public byte[] renderResource(String name, URI endpoint) {
        SoapXmlResource resource = this.files.get(name);
        return resource == null ? null : resource.body();
    }
}
