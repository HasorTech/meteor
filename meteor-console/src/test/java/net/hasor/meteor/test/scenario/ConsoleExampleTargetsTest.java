/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.scenario;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.meteor.registry.shared.storage.domain.EntInstanceRuntimeData;
import net.hasor.meteor.registry.shared.storage.domain.EntServiceRuntimeData;
import net.hasor.meteor.registry.shared.storage.domain.RefProtocolMountData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static net.hasor.meteor.test.scenario.ConsoleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** 通过正式应用、注册中心和 Console HTTP 验证示例只使用当前发布者的实际契约。 */
public class ConsoleExampleTargetsTest {
    @TempDir
    public Path directory;

    @MetService(group = "examples", name = "Echo", version = "1")
    public interface Echo {
        String echo(String value);
    }

    @MetService(group = "examples", name = "Subscribed", version = "1")
    public interface Subscribed {
        String echo(String value);
    }

    @Test
    public void registryTargetsKeepProviderAndConnectorIdentity() throws Exception {
        // 两个实际提供者共享同一个服务，一个提供者有两个端口；消费者不应成为调用目标。
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var registry = registry(url);
        var firstConfig = application(this.directory.resolve("first"), registry, "provider-a", "east", true);
        firstConfig.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        var secondConfig = application(this.directory.resolve("second"), registry, "provider-b", "west", true);
        var consumerConfig = application(this.directory.resolve("consumer"), registry, "consumer", "east", false);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext first = firstConfig.buildContext(); //
             MetContext second = secondConfig.buildContext(); //
             MetContext consumer = consumerConfig.buildContext(); //
             BootApplication server = boot.start()) {
            String serviceId = first.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            second.publisher().rsfService(Echo.class, value -> value).register();
            consumer.publisher().rsfService(Echo.class).register();
            first.start();
            second.start();
            consumer.start();
            Api api = new Api(server);
            api.login();
            String cluster = api.ok("clusters", "POST", profile("调用示例", url)).get("id").asText();
            String scope = "mode=registry&clusterId=" + cluster + "&serviceId=" + encode(serviceId) + "&protocol=rsf";

            // 每个实际绑定独立保留身份、地址和协议契约，不把端点折叠成协议名。
            var result = api.get("examples/targets?" + scope);
            assertEquals("jdbc", result.at("/registry/provider").asText());
            assertTrue(result.path("warnings").isEmpty());
            assertEquals(3, result.path("targets").size());
            Set<String> targetIds = new HashSet<>();
            Set<String> providerIds = new HashSet<>();
            for (var target : result.path("targets")) {
                assertTrue(targetIds.add(target.path("id").asText()));
                providerIds.add(target.path("instanceId").asText());
                assertEquals(serviceId, target.path("serviceId").asText());
                assertEquals(Echo.class.getName(), target.at("/description/javaType").asText());
                assertEquals("Provider", target.at("/service/role").asText());
                assertTrue(target.at("/endpoint/publishedAddress").asText().startsWith("rsf://127.0.0.1:"));
                assertFalse(target.path("endpoint").has("connectorName"));
                assertFalse(target.path("description").has("protocols"));
                assertTrue(target.path("endpoint").path("description").path("methods").isArray());
            }
            assertEquals(Set.of(first.getInstanceID(), second.getInstanceID()), providerIds);

            // 实例和实际地址限制同时生效；重复读取不会改变用于选择的稳定标识。
            String selected = scope + "&instanceId=" + first.getInstanceID() + "&address=" //
                    + encode(first.getListenAddress("tcp-1", "rsf", "rsf").toString());
            var narrowed = api.get("examples/targets?" + selected);
            assertEquals(1, narrowed.path("targets").size());
            assertEquals("provider-a", narrowed.at("/targets/0/application").asText());
            assertEquals("east", narrowed.at("/targets/0/unit").asText());
            assertTrue(targetIds.contains(narrowed.at("/targets/0/id").asText()));
            assertEquals(narrowed, api.get("examples/targets?" + selected));
        }
    }

    @Test
    public void directInstanceTargetsOnlyIncludeItsPublishedServices() throws Exception {
        // 直连目标上同时存在发布和订阅，地址对应的实例身份优先于遗留的实例参数。
        var registry = new RegistryConfiguration().setOnline(false).setManagementToken("test-token");
        var config = application(this.directory.resolve("direct"), registry, "direct-provider", "east", true);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = config.buildContext(); //
             BootApplication server = boot.start()) {
            String serviceId = provider.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            provider.publisher().rsfService(Subscribed.class).register();
            provider.start();
            Api api = new Api(server);
            api.login();
            api.managementToken("test-token");
            String scope = "mode=direct&target=" + encode(provider.getListenAddress("rsf").toString());
            scope += "&instanceId=stale-instance&protocol=rsf";

            // 返回服务专属发布地址和实际实例，且不把管理地址当作注册中心配置。
            var result = api.get("examples/targets?" + scope);
            assertTrue(result.path("registry").isNull());
            assertEquals(1, result.path("targets").size());
            assertEquals(serviceId, result.at("/targets/0/serviceId").asText());
            assertEquals(provider.getInstanceID(), result.at("/targets/0/instanceId").asText());
            assertEquals(result.at("/targets/0/endpoint/listenAddress"), result.at("/targets/0/address"));
            assertTrue(result.at("/targets/0/endpoint/publishedAddress").isNull());
            assertTrue(result.path("warnings").isEmpty());
            var noMount = api.get("examples/targets?" + scope + "&contextPath=" + encode("/other"));
            assertTrue(noMount.path("targets").isEmpty());
        }
    }

    @Test
    public void directWildcardListenerUsesTheManagementHostWithoutChangingItsSnapshot() throws Exception {
        // 真实服务监听全地址，用户通过可达主机直连管理入口。
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("wildcard-provider") //
                .setDataHome(this.directory.resolve("provider")) //
                .setBindAddress("0.0.0.0");
        configuration.addExtension(new RegistryConfiguration().setOnline(false).setManagementToken("test-token"));
        configuration.connector("tcp") //
                .bind("0.0.0.0", 0) //
                .protocol("rsf");
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            provider.start();
            InterAddress listener = provider.getListenAddress("rsf");
            InterAddress management = new InterAddress("rsf", "127.0.0.1", listener.getPort(), listener.getPath());
            Api api = new Api(server);
            api.login();
            api.managementToken("test-token");
            String scope = "mode=direct&target=" + encode(management.toString()) + "&protocol=rsf&serviceId=" + encode(id);

            // 有效调用地址只替换主机，原始监听快照仍保留全地址、端口、协议和单元路径。
            var result = api.get("examples/targets?" + scope);
            assertEquals(1, result.path("targets").size(), result.toPrettyString());
            URI raw = URI.create(result.at("/targets/0/endpoint/listenAddress").asText());
            URI reachable = URI.create(result.at("/targets/0/address").asText());
            assertTrue(InetAddress.getByName(raw.getHost()).isAnyLocalAddress(), raw.toString());
            assertEquals("127.0.0.1", reachable.getHost());
            assertEquals(raw.getScheme(), reachable.getScheme());
            assertEquals(raw.getPort(), reachable.getPort());
            assertEquals(raw.getPath(), reachable.getPath());
        }
    }

    @Test
    public void missingServiceDescriptionIsReportedWithoutInventingContract() throws Exception {
        // 旧实例可以只有发布端点而没有完整描述；通过真实存储准备这一合法登记。
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var registry = registry(url);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (var storage = registry.openStorage(); //
             BootApplication server = boot.start()) {
            EntInstanceRuntimeData instance = new EntInstanceRuntimeData();
            instance.setInstanceId("legacy-instance");
            instance.setApplicationName("legacy-app");
            storage.registerInstance(instance);
            RefProtocolMountData endpoint = new RefProtocolMountData();

            endpoint.setProtocolName("rsf");
            endpoint.setScheme("rsf");

            endpoint.setPublishedAddress("rsf://127.0.0.1:28999");
            EntServiceRuntimeData service = new EntServiceRuntimeData();
            service.setServiceId("legacy-service");
            service.setName("legacy-service");
            service.setRole(MetServiceType.Provider);
            service.setEndpoints(new RefProtocolMountData[] { endpoint });
            storage.registerService(instance.getInstanceId(), service);
            Api api = new Api(server);
            api.login();
            String cluster = api.ok("clusters", "POST", profile("旧实例", url)).get("id").asText();
            String scope = "mode=registry&clusterId=" + cluster + "&serviceId=legacy-service&protocol=rsf";

            // 用户得到不完整登记的原因，后端不拿服务名或监听地址伪造可执行示例。
            var result = api.get("examples/targets?" + scope);
            assertTrue(result.path("targets").isEmpty());
            assertEquals(1, result.path("warnings").size());
            assertTrue(result.at("/warnings/0").asText().contains("缺少服务描述"));
            assertTrue(result.at("/warnings/0").asText().contains("legacy-instance"));

            api.acceptLanguage("en-US");
            var english = api.get("examples/targets?" + scope);
            assertEquals("legacy-service / legacy-instance has no service description for generating invocation examples", english.at("/warnings/0").asText());
        }
    }
}
