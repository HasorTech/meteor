/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.integration;
import java.util.concurrent.CountDownLatch;
import net.hasor.meteor.MetContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@PropertySource("classpath:spring-provider.properties")
public class ProviderMain {
    public static void main(String[] args) throws InterruptedException {
        try (ConfigurableApplicationContext app = SpringApplication.run(ProviderMain.class, args)) {
            MetContext meteor = app.getBean(MetContext.class);
            System.out.println("Echo 服务已启动：" + meteor.getListenAddress("rsf"));
            new CountDownLatch(1).await();
        }
    }

    @Bean
    public EchoServiceImpl echoService() {
        return new EchoServiceImpl();
    }
}
