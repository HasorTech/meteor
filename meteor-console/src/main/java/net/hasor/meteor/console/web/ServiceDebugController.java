/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.web;
import net.hasor.core.Inject;
import net.hasor.meteor.console.global.http.ConsoleRequest;
import net.hasor.meteor.console.service.ServiceDebugService;
import net.hasor.web.Invoker;
import net.hasor.web.annotation.Get;
import net.hasor.web.annotation.MappingTo;
import net.hasor.web.annotation.Post;

/** 读取实例契约与显式发起一次业务调用；沿用控制台会话及写操作认证。 */
@MappingTo("/api/service/debug")
public class ServiceDebugController {
    @Inject
    private ServiceDebugService debugging;

    @Get
    public Object describe(Invoker invoker) {
        return this.debugging.describe(ConsoleRequest.from(invoker).target());
    }

    @Post
    public Object invoke(Invoker invoker) {
        ConsoleRequest request = ConsoleRequest.from(invoker);
        return this.debugging.invoke(request.target(), request.body(), request.locale());
    }
}
