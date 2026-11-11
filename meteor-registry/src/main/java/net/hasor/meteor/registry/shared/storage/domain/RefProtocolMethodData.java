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
public class RefProtocolMethodData {
    private String              name          = null;
    private String              javaSignature = null;
    private String              requestType   = null;
    private String              responseType  = null;
    private Boolean             message       = null;
    private Map<String, Object> extensions    = new LinkedHashMap<>();

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaSignature() {
        return this.javaSignature;
    }

    public void setJavaSignature(String javaSignature) {
        this.javaSignature = javaSignature;
    }

    public String getRequestType() {
        return this.requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getResponseType() {
        return this.responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public Boolean getMessage() {
        return this.message;
    }

    public void setMessage(Boolean message) {
        this.message = message;
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
