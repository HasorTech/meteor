/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.hasor.example.services.OrderService;
import net.hasor.meteor.annotation.MetAddress;
import net.hasor.meteor.domain.MetServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import static org.junit.jupiter.api.Assertions.*;

class ServiceScannerConfigurerTest {
    @TempDir
    Path directory;

    @Test
    void xmlPublishesAndInjectsRemoteService() throws Exception {
        // XML 声明提供者，属性文件配置随机监听端口。
        String providerBean = "<bean class=\"net.hasor.example.services.OrderServiceImpl\"/>";
        try (GenericApplicationContext provider = application("provider", providerBean)) {
            provider.refresh();
            String address = provider.getBean(MetContext.class).getListenAddress("rsf").toString();
            String book = "<address-book xmlns=\"https://www.hasor.net/schema/meteor-address-book\">" + "<service group=\"spring-demo\" name=\"net.hasor.example.services.OrderService\" version=\"1.0\">" + "<address unit=\"example\">" + address + "</address></service></address-book>";
            Path resource = this.directory.resolve("address-book.xml");
            Files.writeString(resource, book);

            // 消费端通过 XML 中的扫描器注册代理，原生注入后走真实网络调用。
            String clientBean = "<bean class=\"net.hasor.meteor.ServiceScannerConfigurerTest$Client\"/>";
            try (GenericApplicationContext consumer = application("consumer", clientBean)) {
                Map<String, Object> values = Map.of("meteor.address-book", resource.toUri().toString());
                consumer.getEnvironment().getPropertySources().addFirst(new MapPropertySource("address", values));
                consumer.refresh();
                MetBindInfo<?> service = consumer.getBean(MetContext.class).getServiceInfo(OrderService.class);
                assertEquals(MetServiceType.Consumer, service.getServiceType());
                assertEquals("ORDER-1001", consumer.getBean(Client.class).service.getOrder("ORDER-1001").getOrderId());
            }
        }
    }

    @Test
    void xmlRegistersExplicitAddressInjector() throws Exception {
        // 显式地址引用也必须由 XML 扫描器安装，不能依赖 @EnableMeteor。
        try (GenericApplicationContext provider = application("direct-provider", "<bean class=\"net.hasor.example.services.OrderServiceImpl\"/>")) {
            provider.refresh();
            String address = provider.getBean(MetContext.class).getListenAddress("rsf").toString();
            try (GenericApplicationContext consumer = application("direct-consumer", "<bean class=\"net.hasor.meteor.ServiceScannerConfigurerTest$DirectClient\"/>")) {
                consumer.getEnvironment().getPropertySources().addFirst(new MapPropertySource("target", Map.of("rpc.target", address)));
                consumer.refresh();
                assertEquals("PAID", consumer.getBean(DirectClient.class).service.getOrder("ORDER-1001").getStatus());
            }
        }
    }

    @Test
    void existingEnvironmentOverridesPropertiesResource() throws Exception {
        // 属性文件作为默认配置，不覆盖宿主应用已经提供的值。
        try (GenericApplicationContext app = application("defaults", "")) {
            app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("override", Map.of("meteor.application", "overridden")));
            app.refresh();
            assertEquals("overridden", app.getBean(MetContext.class).getApplicationName());
        }
    }

    @Test
    void readsScanPackagesFromPropertiesResource() throws Exception {
        // XML 不声明 basePackage 时，使用同一属性文件中的扫描配置。
        try (GenericApplicationContext app = application("packages", "")) {
            app.getBeanFactory().getBeanDefinition("scanner").getPropertyValues().removePropertyValue("basePackage");
            app.refresh();
            assertNotNull(app.getBean(OrderService.class));
        }
    }

    @Test
    void acceptsMultiplePackageDelimiters() throws Exception {
        // 多包配置兼容 dbVisitor 使用的逗号、分号与空白分隔，并去重扫描结果。
        try (GenericApplicationContext app = application("delimiters", "")) {
            String packages = "net.hasor.example.services ; net.hasor.example.services,\n net.hasor.example.services";
            app.getBeanFactory().getBeanDefinition("scanner").getPropertyValues().add("basePackage", packages);
            app.refresh();
            assertEquals(1, app.getBeansOfType(OrderService.class).size());
        }
    }

    @Test
    void rejectsMissingScanPackage() {
        // 纯 XML 无默认配置类所在包，未指定扫描范围时应明确失败。
        try (GenericApplicationContext app = new GenericApplicationContext()) {
            app.registerBean(ServiceScannerConfigurer.class);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, app::refresh);
            assertTrue(error.getMessage().contains("basePackage"));
        }
    }

    @Test
    void rejectsDuplicateScanners() throws Exception {
        // XML 与另一个扫描器重复启用时，不重复注册上下文或注入器。
        try (GenericApplicationContext app = application("duplicate", "<bean class=\"net.hasor.meteor.ServiceScannerConfigurer\"/>")) {
            IllegalStateException error = assertThrows(IllegalStateException.class, app::refresh);
            assertTrue(error.getMessage().contains("one Meteor service scanner"));
        }
    }

    @Test
    void rejectsMissingConfigurationResource() {
        // 配置路径写错时立即失败，不静默采用默认配置。
        try (GenericApplicationContext app = new GenericApplicationContext()) {
            app.registerBean(ServiceScannerConfigurer.class, () -> {
                ServiceScannerConfigurer scanner = new ServiceScannerConfigurer();
                scanner.setConfigLocation("classpath:missing-meteor-settings.properties");
                return scanner;
            });
            assertThrows(java.io.UncheckedIOException.class, app::refresh);
        }
    }

    private GenericApplicationContext application(String name, String beans) throws Exception {
        Path properties = this.directory.resolve(name + ".properties");
        String home = this.directory.resolve(name).toString().replace("\\", "/");
        Files.writeString(properties, "meteor.application=" + name + "\nmeteor.data-home=" + home + "\nmeteor.unit=example\nmeteor.connectors.rsf.host=127.0.0.1\nmeteor.connectors.rsf.port=0" + "\nscan.package=net.hasor.example.services\nmeteor.scan-packages=net.hasor.example.services\n");
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:context="http://www.springframework.org/schema/context"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
                                           http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
                    <context:annotation-config/>
                    <bean id="scanner" class="net.hasor.meteor.ServiceScannerConfigurer">
                        <property name="basePackage" value="${scan.package}"/>
                        <property name="configLocation" value="%s"/>
                    </bean>
                    %s
                </beans>
                """.formatted(properties.toUri(), beans);
        GenericApplicationContext app = new GenericApplicationContext();
        new XmlBeanDefinitionReader(app).loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
        return app;
    }

    public static class Client {
        @Autowired
        private OrderService service;
    }

    public static class DirectClient {
        @MetAddress("${rpc.target}")
        private OrderService service;
    }

    @EnableMeteor
    @Configuration(proxyBeanMethods = false)
    static class Enable {
    }

    @Test
    void usesEnablingConfigurationPackageWhenMeteorPackagesAreAbsent() {
        // 不指定 basePackages 或 meteor.scan-packages，使用配置类所在包。
        try (AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext()) {
            Map<String, Object> properties = Map.of( //
                    "meteor.connectors.rsf.host", "127.0.0.1", //
                    "meteor.connectors.rsf.port", "0", //
                    "meteor.data-home", this.directory.resolve("meteor").toString());
            app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("defaults", properties));
            app.register(Enable.class);
            app.refresh();
            MetContext context = app.getBean(MetContext.class);
            assertNotNull(context.getServiceInfo(ScannerTypes.Service.class));
            assertNotNull(app.getBean(ScannerTypes.Service.class));
        }
    }

    @Test
    void scansOnlyAnnotatedInterfacesFromNativePackageList() {
        // 通过原生 Environment 索引列表和 @EnableMeteor 完成扫描、注册。
        try (AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext()) {
            Map<String, Object> properties = Map.of( //
                    "meteor.scan-packages[0]", "net.hasor.meteor", //
                    "meteor.scan-packages[1]", "net.hasor.example.empty", //
                    "meteor.connectors.rsf.host", "127.0.0.1", //
                    "meteor.connectors.rsf.port", "0", //
                    "meteor.data-home", this.directory.resolve("meteor").toString());
            app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("scan", properties));
            app.register(Enable.class);
            app.refresh();
            MetContext context = app.getBean(MetContext.class);
            assertNotNull(context.getServiceInfo(ScannerTypes.Service.class));
            assertEquals(0, app.getBeanNamesForType(ScannerTypes.AnnotatedClass.class).length);
            assertEquals(0, app.getBeanNamesForType(ScannerTypes.UnannotatedInterface.class).length);
        }
    }

    @Test
    void rejectsBlankScanPackage() {
        // 显式空包名必须在扫描前拒绝。
        try (AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext()) {
            app.registerBean(ServiceScannerConfigurer.class, () -> {
                ServiceScannerConfigurer scanner = new ServiceScannerConfigurer();
                scanner.setBasePackage("");
                scanner.setDefaultPackage("net.hasor.example");
                return scanner;
            });
            assertThrows(IllegalArgumentException.class, app::refresh);
        }
    }
}
