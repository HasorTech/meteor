/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.shared.rpc;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 实例上的一个协议挂载；附带类型以空地址声明，不包含具体业务服务地址。 */
public final class InstanceEndpointData {
    private String              protocolName;
    private Map<String, String> endpoints = new LinkedHashMap<>();

    private Set<String> protocolFamilies = new LinkedHashSet<>();

    private boolean defaultProtocol; // 是否为所属实例配置的默认协议

    public String getProtocolName() {
        return this.protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    /** 协议插件声明的业务承载家族，旧快照缺少此字段时保持空集合。 */
    public Set<String> getProtocolFamilies() {
        return this.protocolFamilies;
    }

    public void setProtocolFamilies(Set<String> protocolFamilies) {
        this.protocolFamilies = protocolFamilies;
    }

    public boolean isDefaultProtocol() {
        return this.defaultProtocol;
    }

    public void setDefaultProtocol(boolean defaultProtocol) {
        this.defaultProtocol = defaultProtocol;
    }

    /** key 为 endpoint、http、management、wsdl、xsd 等用途；空值字符串仅声明附带能力。 */
    public Map<String, String> getEndpoints() {
        return this.endpoints;
    }

    public void setEndpoints(Map<String, String> endpoints) {
        this.endpoints = endpoints;
    }
}
