/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.rpc;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** 注册中心管理接口的作用域标识；不接受直连地址或目标实例凭据。 */
public final class RpcTarget {
    public enum Scope {
        CLUSTER,
        SERVICE,
        INSTANCE
    }

    private String clusterId;
    private Scope  scope;
    private String serviceId;
    private String instanceId;

    public static RpcTarget cluster(String clusterId) {
        RpcTarget target = new RpcTarget();
        target.clusterId = clusterId;
        target.scope = Scope.CLUSTER;
        return target;
    }

    public static RpcTarget service(String clusterId, String serviceId, String instanceId) {
        RpcTarget target = new RpcTarget();
        target.clusterId = clusterId;
        target.scope = Scope.SERVICE;
        target.serviceId = serviceId;
        target.instanceId = instanceId;
        target.validate();
        return target;
    }

    public static RpcTarget instance(String clusterId, String instanceId) {
        RpcTarget target = new RpcTarget();
        target.clusterId = clusterId;
        target.scope = Scope.INSTANCE;
        target.instanceId = instanceId;
        target.validate();
        return target;
    }

    public String getClusterId() {
        return this.clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public Scope getScope() {
        return this.scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public String getServiceId() {
        return this.serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getInstanceId() {
        return this.instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void validate() {
        if (StringUtils.isBlank(this.clusterId)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_REMOTE_CLUSTER_ID);
        }
        if (this.scope == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_REGISTRY_MANAGEMENT_SCOPE);
        }

        switch (this.scope) {
            case CLUSTER:
                if (StringUtils.isNotBlank(this.serviceId) || StringUtils.isNotBlank(this.instanceId)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_CLUSTER_SCOPE_DOES_NOT_ACCEPT_A);
                }
                break;
            case SERVICE:
                if (StringUtils.isBlank(this.serviceId)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_SERVICE_ID);
                }
                break;
            case INSTANCE:
                if (StringUtils.isBlank(this.instanceId) || StringUtils.isNotBlank(this.serviceId)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_INSTANCE_SCOPE_REQUIRES_AN_INSTANCE_ID);
                }
                break;
        }
    }
}
