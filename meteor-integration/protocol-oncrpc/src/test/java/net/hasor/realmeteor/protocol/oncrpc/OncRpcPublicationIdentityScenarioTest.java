/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.realmeteor.protocol.oncrpc;
import java.net.DatagramSocket;
import net.hasor.cobble.StringUtils;
import net.hasor.example.protocol.oncrpc.OncRpcDemoCodecs;
import net.hasor.example.protocol.oncrpc.OncRpcDemoService;
import net.hasor.example.protocol.oncrpc.OncRpcDemoServiceImpl;
import net.hasor.example.protocol.oncrpc.TestConfigurationUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.protocol.oncrpc.binding.OncRpcServiceResource;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(25)
public class OncRpcPublicationIdentityScenarioTest {
    @ParameterizedTest
    @ValueSource(strings = { "tcp", "udp" })
    public void sameNameCannotBeRepublishedWithAnotherGroupOrVersion(String transport) throws Exception {
        // 原生数值版本即使不同，Meteor 同名服务也必须在发布时报告冲突。
        String protocol = "oncrpc";
        if (StringUtils.equals(transport, "udp")) {
            protocol = "oncrpc-udp";
        }
        MetConfiguration incoming = TestConfigurationUtils.create().setDefaultProtocol(protocol);
        int port = 0;
        if (StringUtils.equals(transport, "udp")) {
            try (DatagramSocket available = new DatagramSocket(0)) {
                port = available.getLocalPort();
            }
        }
        incoming.connector(transport).bind("127.0.0.1", port).protocol(protocol);
        MetConfiguration outgoing = TestConfigurationUtils.create().setDefaultProtocol(protocol);
        outgoing.connector(transport).protocol(protocol);
        final String selected = protocol;
        try (MetContext server = incoming.buildContext(); MetContext client = outgoing.buildContext()) {
            server.start();
            client.start();
            server.publisher().rsfService(OncRpcDemoService.class, new OncRpcDemoServiceImpl()).name("Orders").group("one").version("1").bindResource(protocol, OncRpcDemoCodecs.resource()).register();
            OncRpcServiceResource other = OncRpcDemoCodecs.resource();
            other.setVersion(2);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> server.publisher().rsfService(OncRpcDemoService.class, new OncRpcDemoServiceImpl()).name("Orders").group("two").version("2").bindResource(selected, other).register());
            assertTrue(failure.getMessage().contains("service-name"));
            assertNull(server.getServiceInfo("two", "Orders", "2"));
            MetBindInfo<OncRpcDemoService> subscribed = client.publisher().rsfService(OncRpcDemoService.class).name("Orders").group("one").version("1").bindResource(protocol, OncRpcDemoCodecs.resource()).register();
            OncRpcDemoService remote = client.getRsfClient(server.getListenAddress(protocol)).getRemote(subscribed);
            assertEquals("still available", remote.echo("still available"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "tcp", "udp" })
    public void differentNamesCannotShareTheSameNativeProgramVersion(String transport) throws Exception {
        // 改 Meteor 名称不能绕过原生 program/version 的唯一性约束。
        String protocol = "oncrpc";
        if (StringUtils.equals(transport, "udp")) {
            protocol = "oncrpc-udp";
        }
        MetConfiguration configuration = TestConfigurationUtils.create().setDefaultProtocol(protocol);
        configuration.connector(transport).protocol(protocol);
        final String selected = protocol;
        try (MetContext server = configuration.buildContext()) {
            server.publisher().rsfService(OncRpcDemoService.class, new OncRpcDemoServiceImpl()).name("First").bindResource(protocol, OncRpcDemoCodecs.resource()).register();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> server.publisher().rsfService(OncRpcDemoService.class, new OncRpcDemoServiceImpl()).name("Second").bindResource(selected, OncRpcDemoCodecs.resource()).register());
            assertTrue(failure.getMessage().contains("program"));
            assertNotNull(server.getServiceInfo("default", "First", "1.0.0"));
            assertNull(server.getServiceInfo("default", "Second", "1.0.0"));
        }
    }
}
