/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.spring;
import net.hasor.example.spring.api.EchoService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlConsumerMain {
    public static MetConfiguration configuration() {
        MetConfiguration configuration = new MetConfiguration().setApplicationName("spring-xml1-consumer");
        configuration.connector("tcp").protocol("rsf");
        return configuration;
    }

    public static void main(String[] args) {
        try (var app = new ClassPathXmlApplicationContext("spring-consumer.xml")) {
            EchoService echo = app.getBean(EchoService.class);
            System.out.println(echo.echo("Meteor"));
        }
    }
}
