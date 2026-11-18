/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.integration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@PropertySource("classpath:spring-consumer.properties")
public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext app = SpringApplication.run(ConsumerMain.class, args)) {
            EchoClient client = app.getBean(EchoClient.class);
            client.run();
        }
    }

    @Bean
    public EchoClient echoClient() {
        return new EchoClient();
    }
}
