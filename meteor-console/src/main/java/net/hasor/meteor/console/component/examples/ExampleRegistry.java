/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples;

/** 生成注册发现示例所需的注册中心类型和地址。 */
public final class ExampleRegistry {
    private String type;
    private String address;

    public String getType() { return this.type; }
    public void setType(String value) { this.type = value; }
    public String getAddress() { return this.address; }
    public void setAddress(String value) { this.address = value; }
}
