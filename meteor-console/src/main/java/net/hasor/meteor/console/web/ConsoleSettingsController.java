/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.web;

import net.hasor.core.Inject;
import net.hasor.meteor.console.service.ConsoleSettingsService;
import net.hasor.web.annotation.Get;
import net.hasor.web.annotation.MappingTo;

/** 登录后的控制台能力和界面定义；沿用统一认证边界。 */
@MappingTo("/api")
public class ConsoleSettingsController {
    @Inject
    private ConsoleSettingsService settings;

    @Get
    @MappingTo("/settings")
    public Object settings() {
        return this.settings.settings();
    }
}
