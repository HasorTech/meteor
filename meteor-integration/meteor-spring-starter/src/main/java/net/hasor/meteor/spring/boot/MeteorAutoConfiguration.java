/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.spring.boot;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.ServiceScannerConfigurer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Reuses the Spring integration and defaults service scanning to Boot's application packages. */
@AutoConfiguration
@ConditionalOnClass(MetContext.class)
@ConditionalOnProperty(prefix = "meteor", name = "enabled", matchIfMissing = true)
public class MeteorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(value = { ServiceScannerConfigurer.class, MetContext.class }, name = "meteorServiceScanner")
    public static ServiceScannerConfigurer meteorServiceScanner(ConfigurableListableBeanFactory factory) {
        ServiceScannerConfigurer scanner = new ServiceScannerConfigurer();
        if (AutoConfigurationPackages.has(factory)) {
            scanner.setDefaultPackage(String.join(",", AutoConfigurationPackages.get(factory)));
        }
        return scanner;
    }
}
