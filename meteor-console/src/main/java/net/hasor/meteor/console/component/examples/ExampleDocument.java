/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples;

/** 一个语言、一个角色对应一份可直接阅读的调用文档。 */
public final class ExampleDocument {
    private final String id;
    private final String language;
    private final String role;
    private final String markdown;

    public ExampleDocument(String id, String language, String role, String markdown) {
        this.id = id;
        this.language = language;
        this.role = role;
        this.markdown = markdown;
    }

    public String getId() { return this.id; }
    public String getLanguage() { return this.language; }
    public String getRole() { return this.role; }
    public String getMarkdown() { return this.markdown; }
}
