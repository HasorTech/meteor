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
import net.hasor.meteor.registry.admin.MetDirectoryQuery;
import net.hasor.meteor.registry.admin.MetServiceMethodPage;

public final class ServiceMethodsRequest extends CommandRequest<MetServiceMethodPage> {
    private final String            methodName;
    private final MetDirectoryQuery query;

    @JsonCreator
    public ServiceMethodsRequest(@JsonProperty("target") ManagementTarget target, @JsonProperty("methodName") String methodName, @JsonProperty("query") MetDirectoryQuery query) {
        super(target);
        this.methodName = methodName;
        this.query = query;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public MetDirectoryQuery getQuery() {
        return this.query;
    }

    @Override
    public CommandType getCommand() {
        return CommandType.SERVICE_METHODS;
    }
}
