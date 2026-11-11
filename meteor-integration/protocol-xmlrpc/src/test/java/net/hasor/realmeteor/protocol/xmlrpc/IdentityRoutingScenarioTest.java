/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.realmeteor.protocol.xmlrpc;
import net.hasor.example.protocol.xmlrpc.TestConfigurationUtils;
import net.hasor.example.protocol.xmlrpc.identity.IdentityMessage;
import net.hasor.example.protocol.xmlrpc.identity.IdentityService;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 同一监听端口中的服务身份隔离。 */
@Timeout(30)
public class IdentityRoutingScenarioTest {
    @Test
    public void xmlrpcSeparatesGroupsAndVersionsOnOneEndpoint() throws Exception {
        // 同一协议与端口上，两个分组、两个版本返回不同标记，验证正式调用没有串路。
        MetConfiguration incoming = TestConfigurationUtils.create() //
                .setDefaultProtocol("xmlrpc");
        incoming.connector("http") //
                .bind("127.0.0.1", 0) //
                .protocol("xmlrpc") //
                .contextPath("/rpc");
        MetConfiguration outgoing = TestConfigurationUtils.create() //
                .setDefaultProtocol("xmlrpc");
        outgoing.connector("http") //
                .protocol("xmlrpc") //
                .contextPath("/rpc");
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
                IdentityService remote = client.getRsfClient(server.getListenAddress("xmlrpc")).getRemote(binding);
                IdentityMessage response = remote.echo(new IdentityMessage());
                assertEquals(group + ":" + version, response.getValue());
            }
        }
    }
}
