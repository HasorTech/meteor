/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.spring;
import java.util.concurrent.CountDownLatch;
import net.hasor.meteor.MetContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlProviderMain {
    public static void main(String[] args) throws Exception {
        try (var app = new ClassPathXmlApplicationContext("spring-provider.xml")) {
            System.out.println("服务已启动：" + app.getBean(MetContext.class).getListenAddress("rsf"));
            new CountDownLatch(1).await();
        }
    }
}
