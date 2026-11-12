/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.model.vo;

/** 当前部署中一个可选功能的可用状态。 */
public final class ConsoleCapability {
    private final String  key;
    private final String  label;
    private final boolean available;
    private final String  reason;

    public ConsoleCapability(String key, String label, boolean available, String reason) {
        this.key = key;
        this.label = label;
        this.available = available;
        this.reason = reason;
    }

    public String getKey() {
        return this.key;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public String getReason() {
        return this.reason;
    }
}
