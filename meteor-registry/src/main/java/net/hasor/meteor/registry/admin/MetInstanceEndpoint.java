/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.admin;

/** 实例的一个监听挂载；同协议的不同连接器和上下文分别返回。 */
public final class MetInstanceEndpoint {
    private String connectorName     = null; // 实例内的连接器名称
    private String transport         = null; // 传输 SPI 名称
    private String protocolName      = null; // 协议 SPI 名称
    private String scheme            = null; // 地址 scheme，可独立于协议名配置
    private String contextPath       = null; // HTTP 挂载路径；非 HTTP 端点为空
    private String listenAddress     = null; // 当前实际监听地址
    private String managementAddress = null; // 管理服务接受此挂载时的管理 RPC 地址，否则为空

    public String getConnectorName() {
        return this.connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public String getTransport() {
        return this.transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getProtocolName() {
        return this.protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    public String getScheme() {
        return this.scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getContextPath() {
        return this.contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getListenAddress() {
        return this.listenAddress;
    }

    public void setListenAddress(String listenAddress) {
        this.listenAddress = listenAddress;
    }

    public String getManagementAddress() {
        return this.managementAddress;
    }

    public void setManagementAddress(String managementAddress) {
        this.managementAddress = managementAddress;
    }
}
