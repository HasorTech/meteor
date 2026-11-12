/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.component.examples;
import java.util.List;
import java.util.Map;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.registry.admin.MetServiceEndpoint;
import net.hasor.meteor.registry.admin.description.MetProtocolDescription;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ExampleConnectionsTest {
    @Test
    void sdkMountDoesNotRepeatTheGeneratedServicePath() {
        // SDK 地址与原生调用路径分别上报，生成客户端时只配置 SDK 挂载。
        ExampleTarget target = target("connect", "connect");
        target.getEndpoint().setTransport("http");
        target.getEndpoint().setContextPath("/connect/console-debug/1.0.0");
        target.setAddress("connect://127.0.0.1:19211/connect");

        assertEquals("/connect", ExampleConnections.contextPath(target));
        assertArrayEquals(new String[0], ExampleConnections.servicePaths(target));
    }

    @Test
    void explicitServicePathKeepsItsRawEncodingAndBinding() {
        // 契约保留服务子路径，SDK 地址只定位协议挂载点。
        ExampleTarget target = target("connect", "connect");
        target.getEndpoint().setTransport("http");
        target.getEndpoint().setContextPath("/connect/%2Forders/2.0");
        target.setAddress("connect://127.0.0.1:19211/connect");

        contract(target).getExtensions().put("servicePaths", List.of("/%2Forders/2.0"));
        assertEquals("/connect", ExampleConnections.contextPath(target));
        assertArrayEquals(new String[] { "/%2Forders/2.0" }, ExampleConnections.servicePaths(target));
    }

    @ParameterizedTest(name = "旧 {0} 快照沿用普通连接")
    @ValueSource(strings = { "rsf", "jsonrpc" })
    void legacyPlainSnapshotRemainsUsable(String protocol) {
        // 历史快照没有 connection 扩展，普通协议地址仍可生成示例。
        ExampleTarget target = target(protocol, protocol);

        assertDoesNotThrow(() -> ExampleConnections.validate(target));
    }

    @Test
    void selectedPlainConnectionFactsRemainUsableAndReadOnly() {
        // 当前连接器使用普通 HTTP，同协议的另一个连接器要求 TLS。
        ExampleTarget target = target("jsonrpc", "jsonrpc");
        MetProtocolDescription selected = contract(target);
        selected.getExtensions().put("connection", Map.of("tlsEnabled", false, "httpScheme", "http", "upgrade", ""));
        ExampleTarget secure = target("jsonrpc", "jsonrpc");
        contract(secure).getExtensions().put("connection", Map.of("tlsEnabled", true));

        // 校验只读取当前端点的不可变事实，不混用其他连接器的配置。
        assertDoesNotThrow(() -> ExampleConnections.validate(target));
    }

    @Test
    void tlsEnabledConnectionIsRejected() {
        // 安全能力来自实际连接事实，不能仅凭普通协议 scheme 生成明文客户端。
        ExampleTarget target = target("rsf", "rsf");
        contract(target).getExtensions().put("connection", Map.of("tlsEnabled", true));

        assertThrows(IllegalArgumentException.class, () -> ExampleConnections.validate(target));
    }

    @Test
    void httpsConnectionIsRejected() {
        // HTTP scheme 单独要求 HTTPS 时也必须拒绝当前明文模板。
        ExampleTarget target = target("jsonrpc", "jsonrpc");
        contract(target).getExtensions().put("connection", Map.of("tlsEnabled", false, "httpScheme", "https"));

        assertThrows(IllegalArgumentException.class, () -> ExampleConnections.validate(target));
    }

    @Test
    void webSocketUpgradeIsRejected() {
        // 普通地址前缀不能掩盖端点实际要求的 WebSocket 握手。
        ExampleTarget target = target("jsonrpc", "jsonrpc");
        contract(target).getExtensions().put("connection", Map.of("httpScheme", "http", "upgrade", "websocket"));

        assertThrows(IllegalArgumentException.class, () -> ExampleConnections.validate(target));
    }

    @ParameterizedTest(name = "旧快照拒绝未还原的 {0} 连接")
    @ValueSource(strings = { "https", "ws", "wss", "rsfs" })
    void legacySecureOrUpgradedSchemeIsRejected(String scheme) {
        // 没有 connection 扩展也不能将明确的安全或升级地址当作普通连接。
        ExampleTarget target = target("jsonrpc", scheme);

        assertThrows(IllegalArgumentException.class, () -> ExampleConnections.validate(target));
    }

    private static ExampleTarget target(String protocol, String scheme) {
        MetServiceEndpoint endpoint = new MetServiceEndpoint();

        endpoint.setProtocolName(protocol);
        endpoint.setScheme(scheme);
        MetServiceDescription description = new MetServiceDescription();
        endpoint.setDescription(new MetProtocolDescription());
        ExampleTarget target = new ExampleTarget();
        target.setEndpoint(endpoint);
        target.setDescription(description);
        target.setAddress(scheme + "://127.0.0.1:19000/default");
        return target;
    }

    private static MetProtocolDescription contract(ExampleTarget target) {
        MetServiceEndpoint endpoint = target.getEndpoint();
        return endpoint.getDescription();
    }
}
