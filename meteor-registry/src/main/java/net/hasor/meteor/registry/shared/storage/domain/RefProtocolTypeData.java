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
public class RefProtocolTypeData {
    private String                     name        = null;
    private String                     kind        = null;
    private String                     elementType = null;
    private List<RefProtocolFieldData> fields      = new ArrayList<>();
    private List<RefProtocolFieldData> members     = new ArrayList<>();
    private List<RefProtocolFieldData> values      = new ArrayList<>();
    private Map<String, Object>        extensions  = new LinkedHashMap<>();

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return this.kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getElementType() {
        return this.elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public List<RefProtocolFieldData> getFields() {
        return this.fields;
    }

    public void setFields(List<RefProtocolFieldData> fields) {
        this.fields = fields;
    }

    public List<RefProtocolFieldData> getMembers() {
        return this.members;
    }

    public void setMembers(List<RefProtocolFieldData> members) {
        this.members = members;
    }

    public List<RefProtocolFieldData> getValues() {
        return this.values;
    }

    public void setValues(List<RefProtocolFieldData> values) {
        this.values = values;
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
