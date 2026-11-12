/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.commands.action;
import net.hasor.meteor.console.component.commands.CommandContext;
import net.hasor.meteor.console.component.commands.RegistryCommand;
import net.hasor.meteor.console.component.commands.dto.service.ServiceMethodsRequest;
import net.hasor.meteor.registry.admin.MetServiceMethodPage;

/** 直接读取服务的持久方法目录，不展开成员。 */
public final class ServiceMethodsRegistryCmd implements RegistryCommand<ServiceMethodsRequest, MetServiceMethodPage> {
    @Override
    public MetServiceMethodPage execute(CommandContext context, ServiceMethodsRequest request) {
        return context.service(request.getTarget()).methods(request.getMethodName(), request.getQuery());
    }
}
