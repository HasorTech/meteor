/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.shared.storage.domain;
import java.util.ArrayList;
import java.util.List;

/** 一个方法名的持久定义集合；每个定义携带自己的类型图。 */
public class EntServiceMethodData {
    private String                          serviceId;
    private String                          name;
    private List<RefServiceDescriptionData> definitions = new ArrayList<>();

    public String getServiceId() {
        return this.serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<RefServiceDescriptionData> getDefinitions() {
        return this.definitions;
    }

    public void setDefinitions(List<RefServiceDescriptionData> definitions) {
        this.definitions = definitions;
    }
}
