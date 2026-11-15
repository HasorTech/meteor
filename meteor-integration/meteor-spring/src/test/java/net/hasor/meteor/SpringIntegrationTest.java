/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import net.hasor.example.OrderClient;
import net.hasor.example.services.InventoryService;
import net.hasor.example.services.Order;
import net.hasor.example.services.OrderService;
import net.hasor.example.services.OrderServiceImpl;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.annotation.MetAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.TargetAdmin;
import net.hasor.meteor.registry.jdbc.JdbcRegistryConfiguration;
import org.aopalliance.intercept.MethodInterceptor;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import static org.junit.jupiter.api.Assertions.*;

class SpringIntegrationTest {
    @TempDir
    Path directory;

    @Configuration(proxyBeanMethods = false)
    @EnableMeteor(basePackages = "net.hasor.example.services")
    static class Enable {
    }

    @Test
    void loadsExampleAddressBookFromClasspath() throws Exception {
        // 使用演示主配置中的 XSD 地址本，仅将监听端口改为随机端口。
        try (AnnotationConfigApplicationContext app = application("consumer", null)) {
            var sources = app.getEnvironment().getPropertySources();
            sources.addFirst(new ResourcePropertySource("classpath:spring-consumer.properties"));
            sources.addFirst(new MapPropertySource("ports", Map.of("meteor.connectors.rsf.port", "0")));
            app.refresh();

            // 两个消费接口均按服务坐标加载静态地址和允许的协议。
            MetContext context = app.getBean(MetContext.class);
            String orderID = context.getServiceInfo(OrderService.class).getBindID();
            String inventoryID = context.getServiceInfo(InventoryService.class).getBindID();
            List<InterAddress> expected = List.of(new InterAddress("rsf://127.0.0.1:28780"));
            assertEquals("spring-order-consumer", context.getApplicationName());
            assertEquals(Set.of("rsf"), context.getUpdater().getSupportedProtocols(orderID));
            assertEquals(Set.of("rsf"), context.getUpdater().getSupportedProtocols(inventoryID));
            assertEquals(expected, context.getUpdater().queryAllAddresses(orderID));
            assertEquals(expected, context.getUpdater().queryAllAddresses(inventoryID));
            assertEquals(expected, context.getUpdater().queryAvailableAddresses(orderID));
            assertEquals(expected, context.getUpdater().queryAvailableAddresses(inventoryID));
        }
    }

    @Test
    void injectsConsumerAndCallsFinalAopProviderOverTcp() {
        // 发布容器增强后的实例，另外一个接口没有实现，只能成为消费者。
        AtomicBoolean intercepted = new AtomicBoolean();
        try (AnnotationConfigApplicationContext provider = application("provider", null)) {
            provider.registerBean(OrderService.class, OrderServiceImpl::new);
            provider.registerBean("aop", BeanPostProcessor.class, () -> new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof OrderService) {
                        ProxyFactory proxy = new ProxyFactory(bean);
                        proxy.addAdvice((MethodInterceptor) invocation -> {
                            intercepted.set(true);
                            return invocation.proceed();
                        });
                        return proxy.getProxy();
                    }
                    return bean;
                }
            });
            provider.refresh();
            MetContext server = provider.getBean(MetContext.class);
            OrderService published = server.getServiceProvider(server.getServiceInfo(OrderService.class)).get();
            assertSame(provider.getBean(OrderService.class), published);
            assertNotNull(provider.getBean(InventoryService.class));
            assertEquals(MetServiceType.Provider, server.getServiceInfo(OrderService.class).getServiceType());
            assertEquals(MetServiceType.Consumer, server.getServiceInfo(InventoryService.class).getServiceType());
            String address = server.getListenAddress("rsf").toString();

            // 自动生成接口代理并完成业务 Bean 注入，调用必须经过网络。
            try (AnnotationConfigApplicationContext consumer = application("consumer", address)) {
                consumer.registerBean(OrderClient.class);
                consumer.refresh();
                OrderClient caller = consumer.getBean(OrderClient.class);
                assertNotNull(consumer.getBean(InventoryService.class));
                assertEquals("ORDER-1001", caller.getOrder("ORDER-1001").getOrderId());
                assertTrue(intercepted.get());
            }
        }
    }

    @Test
    void discoversProviderThroughRegistryBean() throws Exception {
        // 两个独立 Spring 容器共享 H2 注册存储，消费端不配置直连地址。
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:spring_registry;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); InputStream stream = getClass().getResourceAsStream("/META-INF/meteor/schema/h2.sql"); Reader script = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, script);
        }
        try (AnnotationConfigApplicationContext provider = application("provider", null); AnnotationConfigApplicationContext consumer = application("consumer", null)) {
            provider.registerBean(OrderService.class, OrderServiceImpl::new);
            provider.registerBean(JdbcRegistryConfiguration.class, () -> registry(dataSource));
            consumer.registerBean(JdbcRegistryConfiguration.class, () -> registry(dataSource));
            provider.refresh();
            consumer.refresh();

            assertEquals("ORDER-1001", consumer.getBean(OrderService.class).getOrder("ORDER-1001").getOrderId());
        }
    }

    @Test
    void discoversProviderThroughAutomaticJdbcRegistryConfiguration() throws Exception {
        // 原生 Environment 读取配置，容器只提供命名数据源和业务实现。
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:spring_auto_registry;DB_CLOSE_DELAY=-1");
        try (Connection connection = database.getConnection(); //
             InputStream stream = getClass().getResourceAsStream("/META-INF/meteor/schema/h2.sql"); //
             Reader script = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, script);
        }
        try (AnnotationConfigApplicationContext provider = application("provider", null); //
             AnnotationConfigApplicationContext consumer = application("consumer", null)) {
            provider.getEnvironment().getPropertySources().addFirst(new ResourcePropertySource("classpath:spring-registry.properties"));
            consumer.getEnvironment().getPropertySources().addFirst(new ResourcePropertySource("classpath:spring-registry.properties"));
            provider.registerBean(OrderService.class, OrderServiceImpl::new);
            provider.registerBean("registryDb", DataSource.class, () -> database);
            consumer.registerBean("registryDb", DataSource.class, () -> database);
            provider.refresh();
            consumer.refresh();

            // 没有 RegistryConfiguration Bean 或静态地址，仍能发现和管理目标。
            assertEquals("ORDER-1001", consumer.getBean(OrderService.class).getOrder("ORDER-1001").getOrderId());
            MetContext target = provider.getBean(MetContext.class);
            TargetAdmin admin = consumer.getBean(MetContext.class).getExtension(TargetAdmin.class);
            var instance = admin.getInstanceAdminByAddress(target.getListenAddress("rsf"));
            assertEquals(target.getInstanceID(), instance.instance().getInstanceId());
        }
    }

    @Test
    void rejectsAmbiguousProvidersBeforeListening() {
        // 多实现不能任意挑选一个发布。
        try (AnnotationConfigApplicationContext app = application("ambiguous", null)) {
            app.registerBean("first", OrderService.class, OrderServiceImpl::new);
            app.registerBean("second", OrderService.class, OrderServiceImpl::new);
            assertThrows(IllegalStateException.class, app::refresh);
        }
    }

    @Test
    void closesListenerWhenContainerCloses() throws Exception {
        // 保存监听端口，关闭容器后验证端口可重新绑定。
        AnnotationConfigApplicationContext app = application("closing", null);
        app.refresh();
        int port = app.getBean(MetContext.class).getListenAddress("rsf").getPort();
        app.close();
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            assertTrue(socket.isBound());
        }
    }

    @Test
    void acceptsNativeConfigurationBean() {
        // 原生配置 Bean 支持完整 Meteor 配置，不由框架适配器二次覆盖。
        try (AnnotationConfigApplicationContext app = application("unused", null)) {
            app.registerBean(MetConfiguration.class, () -> {
                MetConfiguration config = new MetConfiguration() //
                        .setApplicationName("custom");
                config.setDataHome(this.directory.resolve("custom"));
                config.connector("tcp") //
                        .bind("127.0.0.1", 0) //
                        .protocol("rsf");
                return config;
            });
            app.refresh();
            assertEquals("custom", app.getBean(MetContext.class).getApplicationName());
        }
    }

    @Test
    void loadsJsonRpcStaticAddressWithoutRestrictingProvider() {
        // 提供者启用两个协议，消费端 XML 只声明 JSON-RPC 静态地址。
        try (AnnotationConfigApplicationContext provider = application("http-provider", null)) {
            Map<String, Object> protocols = new LinkedHashMap<>();
            protocols.put("meteor.connectors.jsonrpc.host", "127.0.0.1");
            protocols.put("meteor.connectors.jsonrpc.port", "0");
            protocols.put("meteor.connectors.jsonrpc.context", "/rpc");
            provider.getEnvironment().getPropertySources().addFirst(new MapPropertySource("protocols", protocols));
            provider.registerBean(OrderService.class, OrderServiceImpl::new);
            provider.refresh();
            MetContext server = provider.getBean(MetContext.class);
            assertNotNull(server.getListenAddress("rsf"));
            assertTrue(server.getServiceInfo(OrderService.class).getBindProtocols().isEmpty());
            assertNotNull(server.getListenAddress("jsonrpc"));
            String address = server.getListenAddress("jsonrpc").toString();

            // 消费端从 XML 读取 JSON-RPC 静态地址，通过实际 HTTP 端口调用。
            try (AnnotationConfigApplicationContext consumer = application("http-consumer", address)) {
                Map<String, Object> consumerProtocols = new LinkedHashMap<>(protocols);
                consumerProtocols.put("meteor.address-book", writeAddressBook("spring-consumer-address-book.xml", address));
                consumer.getEnvironment().getPropertySources().addFirst(new MapPropertySource("protocols", consumerProtocols));
                consumer.refresh();
                MetContext client = consumer.getBean(MetContext.class);
                String serviceID = client.getServiceInfo(OrderService.class).getBindID();
                assertEquals(Set.of("rsf", "jsonrpc"), client.getUpdater().getSupportedProtocols(serviceID));
                assertTrue(client.getServiceInfo(OrderService.class).getBindProtocols().isEmpty());
                assertEquals("ORDER-1001", consumer.getBean(OrderService.class).getOrder("ORDER-1001").getOrderId());
            }
        }
    }

    @Test
    void stopsBeforeBusinessBeansAreDestroyedAndSupportsRestart() {
        // 使用原生 Spring 生命周期暂停和恢复入口。
        AtomicBoolean providerDestroyed = new AtomicBoolean();
        AnnotationConfigApplicationContext app = application("lifecycle", null);
        app.registerBean(OrderService.class, OrderServiceImpl::new);
        app.registerBean("destroyObserver", DisposableBean.class, () -> () -> {
            assertFalse(app.getBean(MetContext.class).isOnline());
            providerDestroyed.set(true);
        });
        app.refresh();
        app.stop();
        assertFalse(app.getBean(MetContext.class).isOnline());
        app.start();
        assertTrue(app.getBean(MetContext.class).isOnline());
        app.close();
        assertTrue(providerDestroyed.get());
    }

    @Test
    void ignoresUnavailableProtocolConfiguration() {
        // 不在 SPI 列表中的配置不安装协议，也不会影响已启用的 RSF。
        try (AnnotationConfigApplicationContext app = application("invalid", null)) {
            Map<String, Object> invalid = Map.of("meteor.connectors.missing.host", "127.0.0.1", "meteor.connectors.missing.port", "8001");
            app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid", invalid));
            app.refresh();
            assertEquals(Set.of("rsf"), app.getBean(MetContext.class).runProtocols());
        }
    }

    @Test
    void releasesContextWhenAddressBookFails() {
        // 扩展已经分配资源，消费端 XML 校验失败时必须关闭该扩展。
        AtomicBoolean extensionClosed = new AtomicBoolean();
        try (AnnotationConfigApplicationContext app = application("binding-failure", null)) {
            app.registerBean(MetExtensionFactory.class, () -> context -> new MetExtension() {
                @Override
                public <T> T getService(Class<T> type) {
                    return null;
                }

                @Override
                public void beforeStart() {
                }

                @Override
                public void afterStart() {
                }

                @Override
                public void close() {
                    extensionClosed.set(true);
                }
            });
            Map<String, Object> invalid = Map.of("meteor.address-book", "classpath:spring-invalid-address-book.xml");
            app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid", invalid));
            assertThrows(RuntimeException.class, app::refresh);
            assertTrue(extensionClosed.get());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProviderFactory {
        @Bean
        OrderService orderService(InventoryService inventory) {
            assertNotNull(inventory);
            return new OrderServiceImpl();
        }
    }

    @Test
    void resolvesFactoryProviderThatDependsOnConsumer() {
        // 工厂方法依赖消费代理，服务装配不能触发提供者的提前实例化。
        try (AnnotationConfigApplicationContext provider = application("factory-provider", null)) {
            provider.register(ProviderFactory.class);
            provider.refresh();
            MetContext server = provider.getBean(MetContext.class);
            String address = server.getListenAddress("rsf").toString();
            try (AnnotationConfigApplicationContext consumer = application("factory-consumer", address)) {
                consumer.refresh();
                assertEquals("ORDER-1001", consumer.getBean(OrderService.class).getOrder("ORDER-1001").getOrderId());
            }
        }
    }

    private static JdbcRegistryConfiguration registry(JdbcDataSource dataSource) {
        JdbcRegistryConfiguration config = new JdbcRegistryConfiguration().setDataSource(dataSource);
        config.setRefreshMillis(50);
        return config;
    }

    @Test
    void injectsIndependentAddressesBeforeInitialization() {
        OrderService first = id -> {
            Order order = new OrderServiceImpl().getOrder(id);
            order.setStatus("FIRST");
            return order;
        };
        OrderService second = id -> {
            Order order = new OrderServiceImpl().getOrder(id);
            order.setStatus("SECOND");
            return order;
        };
        try (AnnotationConfigApplicationContext a = application("address-first", null); AnnotationConfigApplicationContext b = application("address-second", null)) {
            a.registerBean(OrderService.class, () -> first);
            b.registerBean(OrderService.class, () -> second);
            a.refresh();
            b.refresh();
            Map<String, Object> addresses = Map.of("rpc.first", a.getBean(MetContext.class).getListenAddress("rsf").toString(), "rpc.second", b.getBean(MetContext.class).getListenAddress("rsf").toString());
            try (AnnotationConfigApplicationContext app = application("address-consumer", null)) {
                app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("addresses", addresses));
                app.registerBean(AddressClient.class);
                app.refresh();
                AddressClient client = app.getBean(AddressClient.class);
                assertTrue(client.initialized);
                assertEquals("FIRST", client.first().getOrder("ORDER-1001").getStatus());
                assertEquals("SECOND", client.second.getOrder("ORDER-1001").getStatus());
                MetContext context = app.getBean(MetContext.class);
                assertTrue(context.getUpdater().queryAllAddresses(context.getServiceInfo(OrderService.class).getBindID()).isEmpty());
            }
        }
    }

    @Test
    void explicitAddressBypassesLocalProvider() {
        OrderService remote = id -> {
            Order order = new OrderServiceImpl().getOrder(id);
            order.setStatus("REMOTE");
            return order;
        };
        try (AnnotationConfigApplicationContext provider = application("remote-provider", null)) {
            provider.registerBean(OrderService.class, () -> remote);
            provider.refresh();
            try (AnnotationConfigApplicationContext app = application("local-provider", null)) {
                app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("addresses", Map.of("rpc.first", provider.getBean(MetContext.class).getListenAddress("rsf").toString())));
                app.registerBean(OrderService.class, OrderServiceImpl::new);
                app.registerBean(AddressParent.class);
                app.refresh();
                assertEquals("PAID", app.getBean(OrderService.class).getOrder("ORDER-1001").getStatus());
                assertEquals("REMOTE", app.getBean(AddressParent.class).first().getOrder("ORDER-1001").getStatus());
            }
        }
    }

    @Test
    void injectsDefaultAddressBookReference() {
        try (AnnotationConfigApplicationContext provider = application("default-provider", null)) {
            provider.registerBean(OrderService.class, OrderServiceImpl::new);
            provider.refresh();
            String address = provider.getBean(MetContext.class).getListenAddress("rsf").toString();
            try (AnnotationConfigApplicationContext app = application("default-consumer", address)) {
                app.registerBean(DefaultAddressClient.class);
                app.refresh();
                assertEquals("ORDER-1001", app.getBean(DefaultAddressClient.class).service.getOrder("ORDER-1001").getOrderId());
            }
        }
    }

    @Test
    void rejectsMissingAddressConfiguration() {
        try (AnnotationConfigApplicationContext app = application("missing-address", null)) {
            app.registerBean(MissingAddressClient.class);
            assertThrows(org.springframework.beans.BeansException.class, app::refresh);
        }
    }

    @Test
    void rejectsEmptyAddress() {
        // 空地址必须在创建代理前拒绝。
        try (AnnotationConfigApplicationContext app = application("empty-address", null)) {
            app.registerBean(EmptyAddressClient.class);
            assertThrows(org.springframework.beans.BeansException.class, app::refresh);
        }
    }

    @Test
    void rejectsBlankAddress() {
        // 空地址必须在创建代理前拒绝。
        try (AnnotationConfigApplicationContext app = application("blank-address", null)) {
            app.registerBean(BlankAddressClient.class);
            assertThrows(org.springframework.beans.BeansException.class, app::refresh);
        }
    }

    @Test
    void rejectsResolvedBlankAddress() {
        // 空地址必须在创建代理前拒绝。
        try (AnnotationConfigApplicationContext app = application("resolvedblank-address", null)) {
            app.registerBean(ResolvedBlankAddressClient.class);
            assertThrows(org.springframework.beans.BeansException.class, app::refresh);
        }
    }

    public static class AddressParent {
        @MetAddress("${rpc.first}")
        private OrderService first;

        OrderService first() {
            return this.first;
        }
    }

    public static class AddressClient extends AddressParent implements org.springframework.beans.factory.InitializingBean {
        @MetAddress("${rpc.second}")
        private OrderService second;
        boolean initialized;

        @Override
        public void afterPropertiesSet() {
            this.initialized = first() != null && this.second != null;
        }
    }

    public static class DefaultAddressClient {
        @Autowired
        private OrderService service;
    }

    public static class EmptyAddressClient {
        @MetAddress("")
        private OrderService service;
    }

    public static class BlankAddressClient {
        @MetAddress("  ")
        private OrderService service;
    }

    public static class ResolvedBlankAddressClient {
        @MetAddress("${rpc.empty:}")
        private OrderService service;
    }

    public static class MissingAddressClient {
        @MetAddress("${rpc.missing}")
        private OrderService service;
    }

    private AnnotationConfigApplicationContext application(String name, String address) {
        AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("meteor.application", name);
        properties.put("meteor.data-home", this.directory.resolve(name).toString());
        properties.put("meteor.unit", "framework");
        properties.put("meteor.connectors.rsf.host", "127.0.0.1");
        properties.put("meteor.connectors.rsf.port", "0");
        if (address != null) {
            properties.put("meteor.address-book", writeAddressBook("spring-consumer-address-book.xml", address));
        }
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("meteorTest", properties));
        app.register(Enable.class);
        return app;
    }

    private String writeAddressBook(String resource, String address) {
        try (InputStream input = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("${address}", address);
            Path book = Files.createTempFile(this.directory, "spring-address-book-", ".xml");
            Files.writeString(book, xml);
            return book.toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
