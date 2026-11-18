/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.lang.reflect.Modifier;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.annotation.MetAddress;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

/** Injects explicit field references before initialization, independently of type-wide consumer Beans. */
final class AddressInjector implements InstantiationAwareBeanPostProcessor, BeanFactoryAware, EnvironmentAware {
    private BeanFactory factory;
    private Environment environment;

    @Override
    public void setBeanFactory(BeanFactory factory) {
        this.factory = factory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues properties, Object bean, String name) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            MetAddress annotation = field.getAnnotation(MetAddress.class);
            if (annotation == null) {
                return;
            }
            if (!field.getType().isInterface() || Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                throw new IllegalArgumentException("@MetAddress requires a mutable service interface field: " + field);
            }
            if (field.isAnnotationPresent(Autowired.class) || field.isAnnotationPresent(Value.class) || properties.contains(field.getName())) {
                throw new IllegalArgumentException("Multiple injection sources for " + field);
            }
            String template = annotation.value();
            String address = this.environment.resolveRequiredPlaceholders(template);
            if (StringUtils.isBlank(address)) {
                throw new IllegalArgumentException("Empty @MetAddress configuration: " + field);
            }
            MetContext context = this.factory.getBean(MetContext.class);
            try {
                MetClient client = context.getRsfClient(address.trim());
                Object proxy = createProxy(context, client, field.getType());
                ReflectionUtils.makeAccessible(field);
                field.set(bean, proxy);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot inject @MetAddress: " + field, e);
            }
        });
        return properties;
    }

    private static <T> T createProxy(MetContext context, MetClient client, Class<T> type) {
        MetBindInfo<T> service = context.getServiceInfo(type);
        if (service == null) {
            throw new IllegalArgumentException("Undefined @MetAddress service: " + type.getName());
        }

        return ServiceBean.directProxy(client, service);
    }
}
