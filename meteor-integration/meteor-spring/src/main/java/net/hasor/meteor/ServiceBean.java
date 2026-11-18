/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

/** 在 Spring 中将一个 Meteor 消费接口注册为 Bean。上下文的生命周期由提供它的一方管理。 */
public final class ServiceBean<T> implements FactoryBean<T>, InitializingBean {
    private Class<T>   serviceInterface;
    private MetContext context;
    private String     address;
    private T          proxy;

    public void setServiceInterface(Class<T> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public void setContext(MetContext context) {
        this.context = context;
    }

    /** 可选的直连地址。留空时，从上下文的地址本选取服务地址。 */
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.serviceInterface == null || !this.serviceInterface.isInterface()) {
            throw new IllegalArgumentException("serviceInterface must be a service interface");
        }
        if (this.context == null) {
            throw new IllegalArgumentException("context is required");
        }

        boolean direct = StringUtils.isNotBlank(this.address);
        MetClient client = direct ? this.context.getRsfClient(this.address.trim()) : this.context.getRsfClient();
        MetBindInfo<T> binding = this.context.getServiceInfo(this.serviceInterface);
        if (binding == null) {
            binding = this.context.publisher().rsfService(this.serviceInterface).register();
        }
        this.proxy = direct ? directProxy(client, binding) : client.getRemote(binding);
    }

    // 固定地址只作用于当前代理，保留原绑定的本地调用行为。
    static <T> T directProxy(MetClient client, MetBindInfo<T> binding) {
        MetBindInfo<T> reference = new MetBindInfoWrap<T>(binding) {
            @Override
            public boolean isDistributedCall() {
                return true;
            }
        };
        return client.getRemote(reference);
    }

    @Override
    public T getObject() {
        return this.proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return this.serviceInterface;
    }
}
