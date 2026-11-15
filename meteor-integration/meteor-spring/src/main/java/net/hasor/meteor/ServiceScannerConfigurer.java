/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.annotation.MetService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Registers interface FactoryBeans after ordinary Spring Bean definitions are available. */
public final class ServiceScannerConfigurer extends ClassPathScanningCandidateComponentProvider implements BeanFactoryPostProcessor, EnvironmentAware {
    private String basePackage;
    private String defaultPackage;
    private String configLocation;

    public ServiceScannerConfigurer() {
        super(false);
        addIncludeFilter(new AnnotationTypeFilter(MetService.class));
    }

    /** Service interface packages, separated by commas, semicolons or whitespace. */
    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    /** Fallback packages when neither basePackage nor meteor.scan-packages is configured. */
    public void setDefaultPackage(String defaultPackage) {
        this.defaultPackage = defaultPackage;
    }

    /** Optional properties resource. Existing Spring environment values take precedence. */
    public void setConfigLocation(String configLocation) {
        this.configLocation = configLocation;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
        return definition.getMetadata().isIndependent() && definition.getMetadata().isInterface();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        Environment environment = getEnvironment();
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
        if (factory.getBeanNamesForType(ServiceScannerConfigurer.class, false, false).length > 1) {
            throw new IllegalStateException("Use one Meteor service scanner per Spring container");
        }

        if (StringUtils.isNotBlank(this.configLocation)) {
            if (!(environment instanceof ConfigurableEnvironment configurable)) {
                throw new IllegalStateException("configLocation requires a ConfigurableEnvironment");
            }

            String location = environment.resolveRequiredPlaceholders(this.configLocation);
            try {
                configurable.getPropertySources().addLast(new ResourcePropertySource(location, new PathMatchingResourcePatternResolver(factory.getBeanClassLoader()).getResource(location)));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load Meteor configuration: " + location, e);
            }
        }
        if (factory.getBeanNamesForType(MetContext.class, true, false).length > 0) {
            throw new IllegalStateException("Meteor service scanner creates MetContext; remove the custom Bean");
        }

        Map<Class<?>, String> providers = new LinkedHashMap<>();
        String[] scanPackages = new String[0];
        if (this.basePackage != null) {
            scanPackages = environment.resolveRequiredPlaceholders(this.basePackage).trim().split("[,;\\s]+");
        }

        if (scanPackages.length == 0) {
            scanPackages = environment.getProperty("meteor.scan-packages", String[].class);
            if (scanPackages == null) {
                List<String> values = new ArrayList<>();
                for (int index = 0; environment.containsProperty("meteor.scan-packages[" + index + "]"); index++) {
                    values.add(environment.getRequiredProperty("meteor.scan-packages[" + index + "]"));
                }
                scanPackages = values.toArray(String[]::new);
            }
            if (scanPackages.length == 0) {
                if (StringUtils.isBlank(this.defaultPackage)) {
                    throw new IllegalArgumentException("Configure basePackage or meteor.scan-packages");
                }
                scanPackages = this.defaultPackage.trim().split("[,;\\s]+");
            }
        }

        setResourceLoader(new PathMatchingResourcePatternResolver(factory.getBeanClassLoader()));
        Set<Class<?>> services = new LinkedHashSet<>();
        for (String name : scanPackages) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("Empty Meteor service scan package");
            }

            for (BeanDefinition candidate : findCandidateComponents(name)) {
                try {
                    services.add(factory.getBeanClassLoader().loadClass(candidate.getBeanClassName()));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Cannot load Meteor service: " + candidate.getBeanClassName(), e);
                }
            }
        }

        for (Class<?> type : services) {
            String[] names = factory.getBeanNamesForType(type, true, false);
            if (names.length > 1) {
                String candidates = Arrays.toString(names);
                String message = "Multiple Meteor provider Beans for " + type.getName();
                throw new IllegalStateException(message + ": " + candidates);
            }

            if (names.length == 1) {
                providers.put(type, names[0]);
            } else {
                AbstractBeanDefinition consumer = BeanDefinitionBuilder.genericBeanDefinition(ServiceBean.class)//
                        .addConstructorArgValue(type) // Interface FactoryBean, same registration pattern as MapperBean.
                        .addConstructorArgReference("meteorContext").getBeanDefinition();
                consumer.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, type);
                if (registry.containsBeanDefinition(type.getName())) {
                    throw new IllegalStateException("Consumer Bean name already in use: " + type.getName());
                }
                registry.registerBeanDefinition(type.getName(), consumer);
            }
        }

        if (registry.containsBeanDefinition("meteorContext")) {
            throw new IllegalStateException("Bean name meteorContext is reserved for Meteor");
        }

        AbstractBeanDefinition context = BeanDefinitionBuilder.genericBeanDefinition(MeteorContextBean.class)//
                .addConstructorArgValue(services) // Scan results precede provider-name resolution.
                .addConstructorArgValue(providers) // Suppliers resolve the final Spring Beans.
                .addConstructorArgValue(environment).getBeanDefinition();
        registry.registerBeanDefinition("meteorContext", context);
        if (registry.containsBeanDefinition("meteorAddressInjector")) {
            throw new IllegalStateException("Bean name meteorAddressInjector is reserved for Meteor");
        }
        registry.registerBeanDefinition("meteorAddressInjector", BeanDefinitionBuilder.genericBeanDefinition(AddressInjector.class).getBeanDefinition());
    }
}
