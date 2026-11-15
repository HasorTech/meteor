/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.spring.boot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.hasor.example.boot.EchoService;
import net.hasor.meteor.EnableMeteor;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.ServiceScannerConfigurer;
import net.hasor.meteor.annotation.MetAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class MeteorAutoConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void scansBootApplicationPackageWithoutEnableMeteor() {
        // 复用 Boot 的应用包，不需要显式 @EnableMeteor 或扫描配置。
        application("defaults").withUserConfiguration(Provider.class).run(app -> {
            assertNull(app.getStartupFailure());
            assertNotNull(app.getBean(MetContext.class).getServiceInfo(EchoService.class));
            assertTrue(app.getBean(MetContext.class).getListenAddress("rsf").getPort() > 0);
        });
    }

    @Test
    void callsRemoteProviderWithNativeInjection() {
        // 两个独立容器：提供者使用默认扫描，消费者使用属性指定包。
        application("provider").withUserConfiguration(Provider.class).run(provider -> {
            assertNull(provider.getStartupFailure());
            String address = provider.getBean(MetContext.class).getListenAddress("rsf").toString();
            Path book = this.directory.resolve("address-book.xml");
            Files.writeString(book, "<address-book xmlns=\"https://www.hasor.net/schema/meteor-address-book\">" + "<service group=\"default\" name=\"net.hasor.example.boot.EchoService\" version=\"1.0.0\"><address unit=\"default\">" + address + "</address></service></address-book>");
            application("consumer").withUserConfiguration(Consumer.class).withPropertyValues("meteor.scan-packages=net.hasor.example.boot", "meteor.address-book=" + book.toUri()).run(consumer -> {
                assertNull(consumer.getStartupFailure());
                assertEquals("remote:Meteor", consumer.getBean(Client.class).service.echo("Meteor"));
            });
        });
    }

    @Test
    void injectsExplicitAddressWithoutEnableMeteor() {
        // Starter 自动安装 @MetAddress 注入器，指定地址直接走远程调用。
        application("direct-provider").withUserConfiguration(Provider.class).run(provider -> {
            String address = provider.getBean(MetContext.class).getListenAddress("rsf").toString();
            application("direct-consumer").withBean(DirectClient.class).withPropertyValues("meteor.scan-packages=net.hasor.example.boot", "rpc.address=" + address).run(consumer -> {
                assertNull(consumer.getStartupFailure());
                assertEquals("remote:direct", consumer.getBean(DirectClient.class).service.echo("direct"));
            });
        });
    }

    @Test
    void backsOffForEnableMeteor() {
        // 用户显式选择扫描范围时，不生成第二个扫描器。
        application("explicit").withUserConfiguration(Explicit.class).withPropertyValues("meteor.scan-packages=net.hasor.example.empty").run(app -> {
            assertNull(app.getStartupFailure());
            assertEquals(1, app.getBeansOfType(ServiceScannerConfigurer.class).size());
            assertNotNull(app.getBean(EchoService.class));
        });
    }

    @Test
    void backsOffForXmlStyleScanner() {
        // XML 或用户 @Bean 声明的扫描器优先于 Starter 默认扫描器。
        application("scanner").withBean("customScanner", ServiceScannerConfigurer.class, () -> {
            ServiceScannerConfigurer scanner = new ServiceScannerConfigurer();
            scanner.setBasePackage("net.hasor.example.boot");
            return scanner;
        }).run(app -> {
            assertNull(app.getStartupFailure());
            assertEquals(1, app.getBeansOfType(ServiceScannerConfigurer.class).size());
            assertTrue(app.containsBean("customScanner"));
            assertNotNull(app.getBean(EchoService.class));
        });
    }

    @Test
    void usesUserConfigurationBean() {
        // 全局 MetConfiguration Bean 替代属性生成配置，不重复创建上下文。
        application("unused").withUserConfiguration(Provider.class).withBean(MetConfiguration.class, () -> {
            MetConfiguration configuration = new MetConfiguration();
            configuration.setApplicationName("custom");
            configuration.setDataHome(this.directory.resolve("custom"));
            MetConfiguration.ConnectorSettings connector = configuration.connector("tcp");
            connector.bind("127.0.0.1", 0);
            connector.protocol("rsf");
            return configuration;
        }).run(app -> {
            assertNull(app.getStartupFailure());
            assertEquals("custom", app.getBean(MetContext.class).getApplicationName());
            assertEquals(1, app.getBeansOfType(MetContext.class).size());
        });
    }

    @Test
    void backsOffForUserContextBean() {
        // 用户接管上下文创建时，Starter 不再注册扫描器或创建第二个上下文。
        application("user-context").withUserConfiguration(UserContext.class).withBean(MetConfiguration.class, () -> {
            MetConfiguration configuration = new MetConfiguration();
            configuration.setApplicationName("user-managed");
            configuration.setDataHome(this.directory.resolve("user-managed"));
            return configuration;
        }).run(app -> {
            assertNull(app.getStartupFailure());
            assertTrue(app.getBeansOfType(ServiceScannerConfigurer.class).isEmpty());
            assertEquals(1, app.getBeansOfType(MetContext.class).size());
            assertEquals("user-managed", app.getBean(MetContext.class).getApplicationName());
        });
    }

    @Test
    void canDisableAutoConfiguration() {
        // 关闭自动配置时不创建 Meteor 对象，也不打开监听。
        application("disabled").withPropertyValues("meteor.enabled=false").run(app -> {
            assertNull(app.getStartupFailure());
            assertTrue(app.getBeansOfType(MetContext.class).isEmpty());
            assertTrue(app.getBeansOfType(ServiceScannerConfigurer.class).isEmpty());
        });
    }

    @Test
    void loadsYamlThroughBootAutoConfigurationImports() throws Exception {
        // 真实 SpringApplication 加载 YAML，覆盖 imports 资源注册与配置加载入口。
        Path yaml = this.directory.resolve("application.yml");
        Files.writeString(yaml, """
                meteor:
                  application: yaml-application
                  scan-packages: net.hasor.example.boot
                  data-home: %s
                  connectors:
                    rsf:
                      host: 127.0.0.1
                      port: 0
                """.formatted(this.directory.resolve("yaml").toString().replace("\\", "/")));
        SpringApplication application = new SpringApplication(BootApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.config.location", yaml.toUri().toString(), "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext app = application.run()) {
            assertEquals("yaml-application", app.getBean(MetContext.class).getApplicationName());
            assertNotNull(app.getBean(EchoService.class));
        }
    }

    private ApplicationContextRunner application(String name) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MeteorAutoConfiguration.class)).withPropertyValues("meteor.application=" + name, "meteor.data-home=" + this.directory.resolve(name), "meteor.connectors.rsf.host=127.0.0.1", "meteor.connectors.rsf.port=0");
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = EchoService.class)
    static class Provider {
        @Bean
        EchoService echoService() {
            return value -> "remote:" + value;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Consumer {
        @Bean
        Client client() {
            return new Client();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMeteor(basePackages = "net.hasor.example.boot")
    static class Explicit {
    }

    @Configuration(proxyBeanMethods = false)
    static class UserContext {
        @Bean(destroyMethod = "close")
        MetContext meteorContext(MetConfiguration configuration) throws Exception {
            return configuration.buildContext();
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class BootApplication {
    }

    static class Client {
        @Autowired
        private EchoService service;
    }

    static class DirectClient {
        @MetAddress("${rpc.address}")
        private EchoService service;
    }
}
