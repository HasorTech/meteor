/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.domain.MetServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import static org.junit.jupiter.api.Assertions.*;

public class ServiceBeanTest {
    @TempDir
    public Path directory;

    @Test
    public void fixedAddressBeanCallsRemoteEvenWithLocalProvider() throws Exception {
        // 两个上下文发布相同接口，消费者上下文中的实现用于识别本地调用。
        try (MetContext remote = this.configuration("remote").buildContext(); //
             MetContext local = this.configuration("local").buildContext(); //
             GenericApplicationContext app = new GenericApplicationContext()) {
            remote.publisher().rsfService(Echo.class, value -> "remote:" + value).register();
            local.publisher().rsfService(Echo.class, value -> "local:" + value).register();
            remote.start();
            local.start();

            // 用 XML 中的 ref 将接口代理注入业务 Bean，并按类型取得同一对象。
            String address = remote.getListenAddress("rsf").toString();
            String property = "<property name=\"address\" value=\"" + address + "\"/>";
            configureXml(app, local, Echo.class, property);
            app.refresh();

            Echo proxy = app.getBean(Echo.class);
            EchoCaller caller = app.getBean(EchoCaller.class);
            assertSame(proxy, caller.echo);
            assertEquals("remote:hello", caller.echo.echo("hello"));
            assertEquals("local:hello", local.getRsfClient().wrapper(Echo.class).echo("hello"));
        }
    }

    @Test
    public void beanWithoutAddressRegistersConsumerAndUsesAddressBook() throws Exception {
        // 构建实际的客户端和提供者，客户端事先未登记接口。
        try (MetContext remote = this.configuration("address-book-provider").buildContext(); //
             MetContext client = this.configuration("address-book-client").buildContext(); //
             GenericApplicationContext app = new GenericApplicationContext()) {
            remote.publisher().rsfService(Echo.class, value -> "remote:" + value).register();
            remote.start();
            client.start();

            // 显式接口 Bean 自动登记消费者，并从上下文地址本查找端点。
            configureXml(app, client, Echo.class, "");
            app.refresh();
            MetBindInfo<Echo> binding = client.getServiceInfo(Echo.class);
            assertNotNull(binding);
            assertEquals(MetServiceType.Consumer, binding.getServiceType());

            InterAddress address = remote.getListenAddress("rsf");
            client.getUpdater().appendStaticAddress(binding.getBindID(), List.of(address));
            assertEquals("remote:hello", app.getBean(Echo.class).echo("hello"));
        }
    }

    @Test
    public void concreteClassIsRejectedWhenLoadingXml() throws Exception {
        // 配置具体类而非接口，Spring 初始化时应报告错误。
        try (MetContext client = this.configuration("invalid-interface").buildContext(); //
             GenericApplicationContext app = new GenericApplicationContext()) {
            configureXml(app, client, String.class, "");
            BeanCreationException error = assertThrows(BeanCreationException.class, app::refresh);
            assertInstanceOf(IllegalArgumentException.class, error.getMostSpecificCause());
            assertTrue(error.getMostSpecificCause().getMessage().contains("serviceInterface"));
        }
    }

    private MetConfiguration configuration(String name) {
        MetConfiguration configuration = new MetConfiguration().setApplicationName(name).setDataHome(this.directory.resolve(name));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        return configuration;
    }

    private static void configureXml(GenericApplicationContext app, MetContext context, Class<?> service, String addressProperty) {
        app.getBeanFactory().registerSingleton("meteorContext", context);
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                                           https://www.springframework.org/schema/beans/spring-beans.xsd">
                    <bean id="echoService" class="net.hasor.meteor.ServiceBean">
                        <property name="context" ref="meteorContext"/>
                        <property name="serviceInterface" value="%s"/>
                        %s
                    </bean>
                    <bean class="%s">
                        <constructor-arg ref="echoService"/>
                    </bean>
                </beans>
                """.formatted(service.getName(), addressProperty, EchoCaller.class.getName());
        new XmlBeanDefinitionReader(app).loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoCaller {
        private final Echo echo;

        public EchoCaller(Echo echo) {
            this.echo = echo;
        }
    }
}
