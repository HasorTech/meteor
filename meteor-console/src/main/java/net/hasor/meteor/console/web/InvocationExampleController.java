/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.web;
import net.hasor.core.Inject;
import net.hasor.meteor.console.global.http.ConsoleRequest;
import net.hasor.meteor.console.service.InvocationExampleService;
import net.hasor.web.Invoker;
import net.hasor.web.annotation.Get;
import net.hasor.web.annotation.MappingTo;

/** 示例文档只使用重新读取的真实契约和发布端点。 */
@MappingTo("/api")
public class InvocationExampleController {
    @Inject
    private InvocationExampleService examples;

    @Get
    @MappingTo("/examples/languages")
    public Object languages() {
        return this.examples.languages();
    }

    @Get
    @MappingTo("/examples/render")
    public Object render(Invoker invoker) {
        ConsoleRequest request = ConsoleRequest.from(invoker);
        return this.examples.render(request.target(), request.required("protocol"), request.required("endpointAddress"), request.query("contextPath"), request.query("address"), request.locale());
    }
}
