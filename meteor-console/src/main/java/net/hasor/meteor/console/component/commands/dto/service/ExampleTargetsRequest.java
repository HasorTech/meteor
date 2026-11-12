/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.commands.dto.service;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.hasor.meteor.console.component.commands.CommandRequest;
import net.hasor.meteor.console.component.commands.CommandType;
import net.hasor.meteor.console.model.fo.ManagementTarget;

/** 在服务或实例范围内选择实际发布端点；指定实例时 protocol 为空可一次读取所有协议。 */
public final class ExampleTargetsRequest extends CommandRequest<ExampleTargetsResult> {
    private final String protocol;
    private final String address;
    private final String contextPath;

    @JsonCreator
    public ExampleTargetsRequest(@JsonProperty("target") ManagementTarget target, //
            @JsonProperty("protocol") String protocol, //
            @JsonProperty("address") String address, //
            @JsonProperty("contextPath") String contextPath) {
        super(target);
        this.protocol = protocol;
        this.address = address;
        this.contextPath = contextPath;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public String getAddress() {
        return this.address;
    }

    public String getContextPath() {
        return this.contextPath;
    }

    @Override
    public CommandType getCommand() {
        return CommandType.EXAMPLE_TARGETS;
    }
}
