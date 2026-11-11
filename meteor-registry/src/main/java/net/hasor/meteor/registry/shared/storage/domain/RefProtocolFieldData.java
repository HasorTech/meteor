/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.shared.storage.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** 协议契约的持久数据，不依赖 JSON 节点或用户业务类。 */
public class RefProtocolFieldData {
    private Long                fieldId    = null;
    private String              name       = null;
    private String              type       = null;
    private Map<String, Object> extensions = new LinkedHashMap<>();

    public Long getFieldId() {
        return this.fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
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
