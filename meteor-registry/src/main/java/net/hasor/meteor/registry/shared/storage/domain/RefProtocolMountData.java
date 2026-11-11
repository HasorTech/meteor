/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.shared.storage.domain;
import java.util.LinkedHashMap;
import java.util.Map;

/** 实例服务及服务成员中的协议挂载正文；仅保存上报的连接器和协议端点字段。 */
public final class RefProtocolMountData {
    private String                           transport        = null; // 传输 SPI 名称，如 tcp、http
    private String                           protocolName     = null; // 协议 SPI 名称
    private String                           scheme           = null; // 协议地址 scheme
    private String                           contextPath      = null; // 服务实际路由路径；默认路径可包含分组、版本
    private String                           listenAddress    = null; // 本机 SDK 连接地址；无监听时为空
    private String                           publishedAddress = null; // 已发布的 SDK 连接地址；订阅端或未发布时为空
    private Map<String, Map<String, String>> endpoints        = new LinkedHashMap<>(); // 按暴露 scheme 分组的本服务实际接入点，内层 key 为用途、value 为完整地址

    private RefProtocolDescriptionData description; // 此接入点的协议契约，不携带内部连接器身份

    public String getTransport() {
        return this.transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    private boolean defaultProtocol; // 是否为所属实例配置的默认协议

    public String getProtocolName() {
        return this.protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    public boolean isDefaultProtocol() {
        return this.defaultProtocol;
    }

    public void setDefaultProtocol(boolean defaultProtocol) {
        this.defaultProtocol = defaultProtocol;
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

    public String getPublishedAddress() {
        return this.publishedAddress;
    }

    public void setPublishedAddress(String publishedAddress) {
        this.publishedAddress = publishedAddress;
    }

    public Map<String, Map<String, String>> getEndpoints() {
        return this.endpoints;
    }

    public void setEndpoints(Map<String, Map<String, String>> endpoints) {
        this.endpoints = endpoints;
    }

    public RefProtocolDescriptionData getDescription() {
        return this.description;
    }

    public void setDescription(RefProtocolDescriptionData description) {
        this.description = description;
    }
}
