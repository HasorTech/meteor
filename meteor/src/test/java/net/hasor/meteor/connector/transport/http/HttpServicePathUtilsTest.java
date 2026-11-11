/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.connector.transport.http;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.meteor.domain.ContextPath;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.ServiceDomain;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HttpServicePathUtilsTest {
    @Test
    public void sdkUsesTheConfiguredMountWhileTheWirePathKeepsTheIdentity() {
        // 默认挂载生成线上身份路径，SDK 地址仍使用配置提供的原始挂载。
        ServiceDomain<Runnable> service = new ServiceDomain<>(Runnable.class);
        service.setBindGroup("orders");
        service.setBindVersion("2.0");
        ProtocolMeta meta = new ProtocolMeta(service, null, null, Map.of());
        ProtocolConfig config = ProtocolConfig.newConfig("example", "example", Map.of("contextPath", "/rpc"));
        ObjectNode description = JsonNodeFactory.instance.objectNode();
        var binding = HttpServicePathUtils.bind(config, meta, null, description);

        String wirePath = binding.getContextPath().getPaths().iterator().next();
        assertEquals("/rpc/orders/2.0", wirePath);
        assertEquals("/rpc", HttpServicePathUtils.clientPath(binding, wirePath));
        assertEquals(wirePath, HttpServicePathUtils.target("/rpc", service, description));
    }

    @Test
    public void identitySegmentsCannotInjectAPathOrCollideWithEmptyValues() {
        assertEquals("~", HttpServicePathUtils.segment(""));
        assertEquals("%7E", HttpServicePathUtils.segment("~"));
        assertEquals("a%2Fb", HttpServicePathUtils.segment("a/b"));
        assertEquals("a%252Fb", HttpServicePathUtils.segment("a%2Fb"));
        assertEquals("%2E%2E", HttpServicePathUtils.segment(".."));
        assertEquals("%E4%B8%AD%20%3F", HttpServicePathUtils.segment("中 ?"));
    }

    @Test
    public void boundAddressAndListenerBaseProduceTheSameConsumerTarget() {
        ServiceDomain<Runnable> service = new ServiceDomain<>(Runnable.class);
        service.setBindGroup("orders");
        service.setBindVersion("2.0");
        ObjectNode description = JsonNodeFactory.instance.objectNode();
        assertEquals("/rpc/orders/2.0", HttpServicePathUtils.target("/rpc", service, description));
        assertEquals("/rpc/orders/2.0", HttpServicePathUtils.target("/rpc/orders/2.0", service, description));
    }

    @Test
    public void listenerMountEndingWithTheIdentityStillReceivesItsOwnScope() {
        ServiceDomain<Runnable> service = new ServiceDomain<>(Runnable.class);
        service.setBindGroup("orders");
        service.setBindVersion("2.0");
        ObjectNode description = JsonNodeFactory.instance.objectNode();
        description.put("serviceBasePath", "/orders/2.0");
        assertEquals("/orders/2.0/orders/2.0", HttpServicePathUtils.target("/orders/2.0", service, description));
    }

    @Test
    public void literalServiceMountOverridesTheGeneratedIdentityPath() {
        ServiceDomain<Runnable> service = new ServiceDomain<>(Runnable.class);
        ContextPath declared = new ContextPath(List.of("/native"), false);
        ProtocolMeta meta = new ProtocolMeta(service, declared, null, Map.of());
        ProtocolConfig config = ProtocolConfig.newConfig("example", "example", Map.of("contextPath", "/rpc"));
        ObjectNode description = JsonNodeFactory.instance.objectNode();
        var binding = HttpServicePathUtils.bind(config, meta, new Object(), description);
        assertEquals(Set.of("/rpc/native"), binding.getContextPath().getPaths());
        assertSame(declared, meta.getContextPath());
        assertFalse(description.path("serviceScoped").asBoolean());
        assertEquals("/rpc", HttpServicePathUtils.clientPath(binding, "/rpc/native"));
    }

    @Test
    public void sdkKeepsAnExplicitExactPathIncludingIdentityLikeSegments() {
        // 显式精确挂载即使以分组、版本结尾，也不允许从字符串中裁剪。
        ServiceDomain<Runnable> service = new ServiceDomain<>(Runnable.class);
        ContextPath declared = new ContextPath(List.of("/native/orders/2.0"), true);
        ProtocolMeta meta = new ProtocolMeta(service, declared, null, Map.of());
        ProtocolConfig config = ProtocolConfig.newConfig("example", "example", Map.of("contextPath", "/rpc"));
        ObjectNode description = JsonNodeFactory.instance.objectNode();
        var binding = HttpServicePathUtils.bind(config, meta, null, description);

        assertEquals("/rpc", HttpServicePathUtils.clientPath(binding, "/rpc/native/orders/2.0"));
        assertEquals(Set.of("/rpc/native/orders/2.0"), binding.getContextPath().getPaths());
        assertTrue(binding.getContextPath().isExact());
    }
}
