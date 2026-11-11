/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.admin;

/** 注册中心已知的接入点用途；协议 SPI 通过同名字符串声明，允许扩展其他类型。 */
public enum MetEndpointType {
    ENDPOINT("endpoint", "SDK"),
    HTTP("http", "HTTP"),
    MANAGEMENT("management", "管理"),
    SCHEMA("schema", "SCHEMA");

    private final String key;
    private final String label;

    MetEndpointType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return this.key;
    }

    public String getLabel() {
        return this.label;
    }
}
