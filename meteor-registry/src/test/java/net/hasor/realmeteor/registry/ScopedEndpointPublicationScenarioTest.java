/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.realmeteor.registry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import net.hasor.meteor.registry.shared.RegistryTestUtils.Echo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static net.hasor.meteor.registry.shared.RegistryTestUtils.createConfiguration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 注册中心保存发布阶段生成的服务地址；管理查询不重新拼装身份路径。 */
@Timeout(30)
public class ScopedEndpointPublicationScenarioTest {
    @Test
    public void discoveredSdkMountSelectsTheBoundGroupAndVersion() throws Exception {
        // 独立提供者与消费者共享内存注册存储，业务 SOAP 地址不携带分组和版本。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("sdk-mount-tests");
        MetConfiguration incoming = createConfiguration().setDefaultProtocol("soap").addExtension(registry);
        incoming.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        incoming.connector("http").bind("127.0.0.1", 0).protocol("soap").contextPath("/soap");
        MetConfiguration outgoing = createConfiguration().setDefaultProtocol("soap").addExtension(registry);
        outgoing.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        outgoing.connector("http").protocol("soap").contextPath("/soap");
        try (MetContext provider = incoming.buildContext(); //
             MetContext consumer = outgoing.buildContext()) {
            provider.start();
            consumer.start();
            for (int index = 0; index < 3; index++) {
                String group = index == 2 ? "other" : "orders";
                String version = index == 1 ? "2.0" : "1.0";
                String marker = group + ":" + version;
                provider.publisher().rsfService(Echo.class, value -> marker) //
                        .group(group).version(version).protocol("soap").register();
            }
            provider.getExtension(RegistryClient.class).pushSync().get(10, TimeUnit.SECONDS);

            // 三个服务身份下推相同的 SDK 挂载，各自绑定仍正确选择实际线上路由。
            InterAddress mount = provider.getListenAddress("soap");
            for (int index = 0; index < 3; index++) {
                String group = index == 2 ? "other" : "orders";
                String version = index == 1 ? "2.0" : "1.0";
                var binding = consumer.publisher().rsfService(Echo.class) //
                        .group(group).version(version).protocol("soap").register();
                consumer.getExtension(RegistryClient.class).pullSync(binding.getBindID()).get(10, TimeUnit.SECONDS);
                assertEquals(List.of(mount), consumer.getUpdater().queryAvailableAddresses(binding.getBindID()));
                Echo remote = consumer.getRsfClient().getRemote(binding);
                assertEquals(group + ":" + version, remote.echo("sdk"));
            }
        }
    }

    @Test
    public void groupsAndVersionsShareTheSdkMountAndKeepTheirOwnWsdlAddresses() throws Exception {
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("scoped-endpoint-tests");
        MetConfiguration configuration = createConfiguration().setDefaultProtocol("soap").addExtension(registry);
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        configuration.connector("http").bind("127.0.0.1", 0).protocol("soap").contextPath("/soap");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            for (int index = 0; index < 3; index++) {
                String group = index == 2 ? "other" : "orders";
                String version = index == 1 ? "2.0" : "1.0";
                context.publisher().rsfService(Echo.class, value -> value).name("Orders").group(group).version(version).register();
            }
            context.getExtension(RegistryClient.class).pushSync().get(10, TimeUnit.SECONDS);
            RegistryAdmin admin = context.getExtension(RegistryAdmin.class);
            HttpClient http = HttpClient.newHttpClient();
            for (int index = 0; index < 3; index++) {
                String group = index == 2 ? "other" : "orders";
                String version = index == 1 ? "2.0" : "1.0";
                String id = context.getServiceInfo(group, "Orders", version).getBindID();
                var member = admin.getServiceAdmin(id).providers(null, null).getItems().get(0);
                var endpoint = Arrays.stream(member.getEndpoints()).filter(item -> StringUtils.equals(item.getProtocolName(), "soap")).findFirst().orElseThrow();
                String path = "/soap/" + group + "/" + version;
                assertEquals(path, endpoint.getContextPath());
                assertEquals("/soap", URI.create(endpoint.getPublishedAddress()).getRawPath());
                var addresses = endpoint.getEndpoints().get("soap");
                assertEquals(endpoint.getPublishedAddress(), addresses.get("endpoint"));
                assertEquals(path + "/Orders", URI.create(addresses.get("http")).getRawPath());
                URI wsdl = URI.create(addresses.get("schema"));
                assertEquals(path + "/Orders", wsdl.getRawPath());
                assertEquals("wsdl", wsdl.getRawQuery());
                // 页面拿到的真实地址可以直接读取该版本的文件。
                var response = http.send(HttpRequest.newBuilder(wsdl).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains(path + "/Orders"));
            }
        }
    }
}
