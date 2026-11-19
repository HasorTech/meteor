/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.model.vo;

import java.util.Locale;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** 当前部署中一个可选功能的可用状态。 */
public final class ConsoleCapability {
    private final String            key;
    private final String            label;
    private final ConsoleMessageKey reason;
    private final Locale            locale;

    public ConsoleCapability(String key, String label, ConsoleMessageKey reason, Locale locale) {
        this.key = key;
        this.label = label;
        this.reason = reason;
        this.locale = locale;
    }

    public String getKey() {
        return this.key;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean isAvailable() {
        return this.reason == null;
    }

    public ConsoleMessageKey reasonKey() {
        return this.reason;
    }

    public String getReason() {
        return this.reason == null ? "" : ConsoleI18n.getMessage(this.reason, this.locale, this.label);
    }
}
