/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.commands.dto.service;
import net.hasor.meteor.registry.admin.MetServiceEndpoint;
import net.hasor.meteor.registry.admin.MetServiceInfo;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;

/** 一个已发布服务的实际提供端点及其完整契约，不包含注册中心凭据。 */
public final class ExampleTarget {
    private String                id;
    private String                serviceId;
    private String                instanceId;
    private String                application;
    private String                unit;
    private String                address;
    private MetServiceInfo        service;
    private MetServiceEndpoint    endpoint;
    private MetServiceDescription description;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServiceId() {
        return this.serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getInstanceId() {
        return this.instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getApplication() {
        return this.application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getUnit() {
        return this.unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    /** 当前选择可使用的业务地址；直连时可由实际监听地址取得，不改变端点上报正文。 */
    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public MetServiceInfo getService() {
        return this.service;
    }

    public void setService(MetServiceInfo service) {
        this.service = service;
    }

    public MetServiceEndpoint getEndpoint() {
        return this.endpoint;
    }

    public void setEndpoint(MetServiceEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public MetServiceDescription getDescription() {
        return this.description;
    }

    public void setDescription(MetServiceDescription description) {
        this.description = description;
    }
}
