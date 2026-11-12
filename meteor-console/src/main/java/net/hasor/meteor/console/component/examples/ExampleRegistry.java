/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples;

/** 业务注册发现所需的公开连接参数，刻意不承载密码或管理凭据。 */
public final class ExampleRegistry {
    private String type;
    private String address;
    private String database;
    private String username;
    private String namespace;
    private String group;
    private String root;
    private boolean credentialsRequired;

    public String getType() { return this.type; }
    public void setType(String value) { this.type = value; }
    public String getAddress() { return this.address; }
    public void setAddress(String value) { this.address = value; }
    public String getDatabase() { return this.database; }
    public void setDatabase(String value) { this.database = value; }
    public String getUsername() { return this.username; }
    public void setUsername(String value) { this.username = value; }
    public String getNamespace() { return this.namespace; }
    public void setNamespace(String value) { this.namespace = value; }
    public String getGroup() { return this.group; }
    public void setGroup(String value) { this.group = value; }
    public String getRoot() { return this.root; }
    public void setRoot(String value) { this.root = value; }
    public boolean isCredentialsRequired() { return this.credentialsRequired; }
    public void setCredentialsRequired(boolean value) { this.credentialsRequired = value; }
}
