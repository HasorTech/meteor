/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.web;

import net.hasor.core.Inject;
import net.hasor.meteor.console.global.http.ConsoleRequest;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.service.ConsoleSettingsService;
import net.hasor.web.Invoker;
import net.hasor.web.annotation.Get;
import net.hasor.web.annotation.MappingTo;

/** 控制台能力与登录前可用的界面语言包。 */
@MappingTo("/api")
public class ConsoleSettingsController {
    @Inject
    private ConsoleSettingsService settings;

    @Get
    @MappingTo("/i18n")
    public Object i18n() {
        return this.settings.languageBundle(ConsoleI18n.getLocale());
    }

    @Get
    @MappingTo("/settings")
    public Object settings(Invoker invoker) {
        return this.settings.settings(ConsoleRequest.from(invoker).locale());
    }
}
