/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.commands.dto.service;
import java.util.ArrayList;
import java.util.List;
import net.hasor.meteor.registry.admin.RegistryInfo;

/** 可生成调用示例的发布端点，以及无法使用的不完整登记提示。 */
public final class ExampleTargetsResult {
    private List<ExampleTarget> targets  = new ArrayList<>();
    private RegistryInfo        registry;
    private List<String>        warnings = new ArrayList<>();

    public List<ExampleTarget> getTargets() {
        return this.targets;
    }

    public void setTargets(List<ExampleTarget> targets) {
        this.targets = targets;
    }

    public RegistryInfo getRegistry() {
        return this.registry;
    }

    public void setRegistry(RegistryInfo registry) {
        this.registry = registry;
    }

    public List<String> getWarnings() {
        return this.warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
