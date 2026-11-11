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

/** 一个实例上报的公共服务声明和类型图；协议契约随各服务接入点保存。 */
public class RefServiceDescriptionData {
    /** 数值契约编号；未声明时为 null。 */
    private Long                                contractServiceId = null;
    /** 服务接口的 Java 类型名称。 */
    private String                              javaType          = null;
    /** 按声明顺序保存的方法列表。 */
    private List<RefMethodDescriptionData>      methods           = new ArrayList<>();
    /** 以完整类型引用为键的公共类型图。 */
    private Map<String, RefTypeDescriptionData> types             = new LinkedHashMap<>();
    /** 是否为单向消息服务。 */
    private boolean                             message           = false;
    /** 未类型化的扩展字段；值仅包含 Map、List、标量及 null。 */
    private Map<String, Object>                 extensions        = new LinkedHashMap<>();

    public Long getContractServiceId() {
        return this.contractServiceId;
    }

    public void setContractServiceId(Long contractServiceId) {
        this.contractServiceId = contractServiceId;
    }

    public String getJavaType() {
        return this.javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public List<RefMethodDescriptionData> getMethods() {
        return this.methods;
    }

    public void setMethods(List<RefMethodDescriptionData> methods) {
        this.methods = methods;
    }

    public Map<String, RefTypeDescriptionData> getTypes() {
        return this.types;
    }

    public void setTypes(Map<String, RefTypeDescriptionData> types) {
        this.types = types;
    }

    public boolean isMessage() {
        return this.message;
    }

    public void setMessage(boolean message) {
        this.message = message;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    @JsonAnySetter
    public void putExtension(String name, Object value) {
        this.extensions.put(name, value);
    }
}
