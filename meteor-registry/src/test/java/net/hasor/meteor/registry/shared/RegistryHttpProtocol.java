/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.shared;
import java.util.List;
import java.util.Set;
import java.util.function.ObjLongConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.ProtocolSession;
import net.hasor.meteor.connector.ProtocolSessionContext;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.connector.transport.http.HttpProtocolProvider;
import net.hasor.meteor.connector.transport.http.channel.HttpExchange;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.payload.Payload;

/** 注册目录场景使用的 HTTP 挂载；只声明协议元信息，不提供业务报文编解码。 */
public final class RegistryHttpProtocol implements HttpProtocolProvider {
    @Override
    public Set<Integer> httpVersions(ProtocolConfig config) {
        return Set.of(1, 2);
    }

    @Override
    public Set<String> endpointTypes() {
        return Set.of("endpoint", "http");
    }

    @Override
    public String name() {
        return "registry-http";
    }

    @Override
    public String defaultSerialize() {
        return "Java";
    }

    @Override
    public void prepareOperation(ProtocolConfig config, JsonNode contract, Target target, Binding<Object> binding) {
    }

    @Override
    public ProtocolBinding prepareBinding(ProtocolConfig config, MetContext context, ProtocolMeta meta) {
        MetBindInfo<?> service = meta.getService();
        if (StringUtils.equals(service.getBindName(), config.option("rejectService", null))) {
            return null;
        }
        var description = meta.createServiceDescription(config, this.defaultSerialize());
        description.put("serviceBasePath", config.option("contextPath", null));
        return meta.build(config, null, description);
    }

    @Override
    public List<String> serviceKey(MetBindInfo<?> service, ProtocolBinding binding) {
        return List.of();
    }

    @Override
    public ProtocolSession<HttpExchange> createSession(ProtocolConfig config, ProtocolSessionContext context, //
            NetworkChannel<HttpExchange> connection, ObjLongConsumer<Payload> messages) {
        throw new UnsupportedOperationException("Registry metadata fixture does not exchange business messages");
    }
}
