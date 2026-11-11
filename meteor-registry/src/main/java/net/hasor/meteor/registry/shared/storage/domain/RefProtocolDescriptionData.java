/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.shared.storage.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** 协议契约的持久数据，不依赖 JSON 节点或用户业务类。 */
public class RefProtocolDescriptionData {
    private String                           name          = null;
    private String                           serializeType = null;
    private List<RefProtocolMethodData>      methods       = new ArrayList<>();
    private Map<String, RefProtocolTypeData> types         = new LinkedHashMap<>();
    private Map<String, Object>              extensions    = new LinkedHashMap<>();

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerializeType() {
        return this.serializeType;
    }

    public void setSerializeType(String serializeType) {
        this.serializeType = serializeType;
    }

    public List<RefProtocolMethodData> getMethods() {
        return this.methods;
    }

    public void setMethods(List<RefProtocolMethodData> methods) {
        this.methods = methods;
    }

    public Map<String, RefProtocolTypeData> getTypes() {
        return this.types;
    }

    public void setTypes(Map<String, RefProtocolTypeData> types) {
        this.types = types;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    @JsonAnySetter
    public void putExtension(String name, Object value) {
        this.extensions.put(name, value);
    }
}
