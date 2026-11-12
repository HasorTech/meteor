/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.web;
import net.hasor.core.Inject;
import net.hasor.meteor.console.global.http.ConsoleRequest;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.web.Invoker;
import net.hasor.web.annotation.Get;
import net.hasor.web.annotation.MappingTo;

/** 查询调用示例的真实服务提供者，不接受浏览器上传的端点或接口声明。 */
@MappingTo("/api")
public class ExampleTargetsController {
    @Inject
    private GovernanceService governance;

    @Get
    @MappingTo("/examples/targets")
    public Object targets(Invoker invoker) {
        ConsoleRequest request = ConsoleRequest.from(invoker);
        return this.governance.exampleTargets(request.target(), request.required("protocol"), //
                request.query("address"), request.query("contextPath"));
    }
}
