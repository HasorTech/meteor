/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.realmeteor.protocol.dubbo;
import net.hasor.example.protocol.dubbo.TestConfigurationUtils;
import net.hasor.example.protocol.dubbo.identity.IdentityMessage;
import net.hasor.example.protocol.dubbo.identity.IdentityService;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** 同一监听端口中的服务身份隔离。 */
@Timeout(30)
public class IdentityRoutingScenarioTest {
    @Test
    public void dubboSeparatesGroupsAndVersionsOnOneEndpoint() throws Exception {
        // 同一协议与端口上，两个分组、两个版本返回不同标记，验证正式调用没有串路。
        MetConfiguration incoming = TestConfigurationUtils.create() //
                .setDefaultProtocol("dubbo");
        incoming.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("dubbo");
        MetConfiguration outgoing = TestConfigurationUtils.create() //
                .setDefaultProtocol("dubbo");
        outgoing.connector("tcp") //
                .protocol("dubbo");
        try (MetContext server = incoming.buildContext(); //
             MetContext client = outgoing.buildContext()) {
            server.start();
            client.start();

            for (int index = 0; index < 3; index++) {
                String group = index == 2 ? "other" : "orders";
                String version = index == 1 ? "2.0" : "1.0";
                String marker = group + ":" + version;
                server.publisher().rsfService(IdentityService.class, request -> {
                            request.setValue(marker);
                            return request;
                        }) //
                        .group(group).version(version).register();
            }

            for (int index = 0; index < 3; index++) {
                String group = index == 2 ? "other" : "orders";
                String version = index == 1 ? "2.0" : "1.0";
                MetBindInfo<IdentityService> binding = client.publisher().rsfService(IdentityService.class) //
                        .group(group).version(version).register();
                IdentityService remote = client.getRsfClient(server.getListenAddress("dubbo")).getRemote(binding);
                IdentityMessage response = remote.echo(new IdentityMessage());
                assertEquals(group + ":" + version, response.getValue());
            }
        }
    }

    @Test
    public void duplicateWireIdentityIsRejectedBeforePublication() throws Exception {
        MetConfiguration incoming = TestConfigurationUtils.create().setDefaultProtocol("dubbo");
        incoming.connector("tcp").bind("127.0.0.1", 0).protocol("dubbo");
        MetConfiguration outgoing = TestConfigurationUtils.create().setDefaultProtocol("dubbo");
        outgoing.connector("tcp").protocol("dubbo");
        try (MetContext server = incoming.buildContext(); //
             MetContext client = outgoing.buildContext()) {
            server.start();
            client.start();
            server.publisher().rsfService(IdentityService.class, request -> request) //
                    .name("First").group("orders").version("1.0") //
                    .aliasName("dubbo", "wire.Identity").register();

            // Meteor 名称不同也不能发布相同的原生 Dubbo 身份。
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                server.publisher().rsfService(IdentityService.class, request -> request) //
                        .name("Second").group("orders").version("1.0") //
                        .aliasName("dubbo", "wire.Identity").register();
            });
            assertTrue(failure.getMessage().contains("Conflicting service route"));
            assertNull(server.getServiceInfo("orders", "Second", "1.0"));

            // 发布失败不替换先前服务；实际网络调用继续到达原提供者。
            MetBindInfo<IdentityService> binding = client.publisher().rsfService(IdentityService.class) //
                    .name("First").group("orders").version("1.0") //
                    .aliasName("dubbo", "wire.Identity").register();
            IdentityService remote = client.getRsfClient(server.getListenAddress("dubbo")).getRemote(binding);
            assertEquals("input", remote.echo(new IdentityMessage()).getValue());
        }
    }
}