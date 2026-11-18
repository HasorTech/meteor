/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.spring;
import java.util.concurrent.CountDownLatch;
import net.hasor.example.spring.api.EchoService;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlProviderMain {
    public static MetConfiguration configuration(int port) {
        MetConfiguration configuration = new MetConfiguration().setApplicationName("spring-xml1-provider");
        configuration.connector("tcp").bind("127.0.0.1", port).protocol("rsf");
        return configuration;
    }

    public static void main(String[] args) throws Exception {
        try (var app = new ClassPathXmlApplicationContext("spring-provider.xml")) {
            MetContext context = app.getBean(MetContext.class);
            context.publisher().rsfService(EchoService.class, app.getBean(EchoService.class)).register();
            context.start();
            System.out.println("服务已启动：" + context.getListenAddress("rsf"));
            new CountDownLatch(1).await();
        }
    }
}
