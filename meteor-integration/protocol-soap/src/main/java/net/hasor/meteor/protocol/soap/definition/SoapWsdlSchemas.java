/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.soap.definition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import net.hasor.cobble.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** 将已加载的类型依赖内嵌到 WSDL，下载时不再依赖外部 XSD。 */
final class SoapWsdlSchemas {
    interface Resolver {
        SoapXmlResource resolve(String current, String location) throws IOException;
    }

    static void embed(Document document, String name, Resolver resolver) throws IOException {
        var types = document.getElementsByTagNameNS(SoapWsdlParser.WSDL_NAMESPACE, "types");
        if (types.getLength() == 0) {
            return;
        }
        Element container = (Element) types.item(0);
        var schemas = new ArrayList<Element>();
        var nodes = container.getElementsByTagNameNS(SoapWsdlParser.XSD_NAMESPACE, "schema");
        for (int i = 0; i < nodes.getLength(); i++) {
            schemas.add((Element) nodes.item(i));
        }
        Set<String> imports = new HashSet<>();
        for (Element schema : schemas) {
            expand(schema, name, container, resolver, imports, new HashSet<>());
        }
    }

    private static void expand(Element schema, String name, Element container, Resolver resolver,
            Set<String> imports, Set<String> includes) throws IOException {
        var references = new ArrayList<Element>();
        for (Node node = schema.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && StringUtils.equals(SoapWsdlParser.XSD_NAMESPACE, element.getNamespaceURI())
                    && (StringUtils.equals("import", element.getLocalName()) || StringUtils.equals("include", element.getLocalName()))) {
                references.add(element);
            }
        }
        for (Element reference : references) {
            String location = reference.getAttribute("schemaLocation");
            if (location.isBlank()) {
                continue;
            }
            SoapXmlResource resource = resolver.resolve(name, location);
            if (resource == null) {
                throw new IOException("XML Schema dependency was not loaded: " + location);
            }
            Element dependency = (Element) schema.getOwnerDocument().importNode(resource.document().getDocumentElement(), true);
            copyNamespaces(resource.document().getDocumentElement(), dependency);
            if (StringUtils.equals("include", reference.getLocalName())) {
                if (includes.add(resource.name())) {
                    expand(dependency, resource.name(), container, resolver, imports, includes);
                    preserveForms(dependency);
                    while (dependency.hasChildNodes()) {
                        Node child = dependency.getFirstChild();
                        if (child instanceof Element element) {
                            copyNamespaces(dependency, element);
                        }
                        schema.insertBefore(child, reference);
                    }
                }
                schema.removeChild(reference);
            } else {
                reference.removeAttribute("schemaLocation");
                if (imports.add(resource.name())) {
                    container.appendChild(dependency);
                    Set<String> dependencyIncludes = new HashSet<>();
                    dependencyIncludes.add(resource.name());
                    expand(dependency, resource.name(), container, resolver, imports, dependencyIncludes);
                }
            }
        }
    }

    private static void preserveForms(Element schema) {
        for (String kind : new String[] { "element", "attribute" }) {
            String form = schema.getAttribute(kind + "FormDefault");
            if (form.isBlank()) {
                form = "unqualified";
            }
            var declarations = schema.getElementsByTagNameNS(SoapWsdlParser.XSD_NAMESPACE, kind);
            for (int i = 0; i < declarations.getLength(); i++) {
                Element declaration = (Element) declarations.item(i);
                if (declaration.getParentNode() != schema && declaration.hasAttribute("name")
                        && !declaration.hasAttribute("form")) {
                    declaration.setAttribute("form", form);
                }
            }
        }
    }

    private static void copyNamespaces(Element source, Element target) {
        for (Node ancestor = source; ancestor instanceof Element; ancestor = ancestor.getParentNode()) {
            var attributes = ancestor.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if (StringUtils.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attribute.getNamespaceURI())
                        && !target.hasAttributeNS(attribute.getNamespaceURI(), attribute.getLocalName())) {
                    target.setAttributeNS(attribute.getNamespaceURI(), attribute.getNodeName(), attribute.getNodeValue());
                }
            }
        }
    }
}
