/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.model.vo;
import java.util.List;
import java.util.Map;
import net.hasor.meteor.console.component.examples.ExampleDocument;

/** 当前服务端点的调用文档及无法生成的原因。 */
public final class InvocationExamples {
    private final String interfaceDefinition;
    private final String interfaceDescriptor;
    private final List<Map<String, Object>> languages;
    private final List<ExampleDocument> documents;
    private final List<String> warnings;

    public InvocationExamples(String interfaceDefinition, String interfaceDescriptor, List<Map<String, Object>> languages,
            List<ExampleDocument> documents, List<String> warnings) {
        this.interfaceDefinition = interfaceDefinition;
        this.interfaceDescriptor = interfaceDescriptor;
        this.languages = List.copyOf(languages);
        this.documents = List.copyOf(documents);
        this.warnings = List.copyOf(warnings);
    }

    public String getInterfaceDefinition() { return this.interfaceDefinition; }
    public String getInterfaceDescriptor() { return this.interfaceDescriptor; }
    public List<Map<String, Object>> getLanguages() { return this.languages; }
    public List<ExampleDocument> getDocuments() { return this.documents; }
    public List<String> getWarnings() { return this.warnings; }
}
