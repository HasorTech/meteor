/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.xmlrpc;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjLongConsumer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.ProtocolSession;
import net.hasor.meteor.connector.ProtocolSessionContext;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.connector.transport.http.HttpProtocolProvider;
import net.hasor.meteor.connector.transport.http.HttpServicePathUtils;
import net.hasor.meteor.connector.transport.http.channel.HttpExchange;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.payload.Payload;
import net.hasor.meteor.protocol.xmlrpc.codec.XmlRpcCodec;
import net.hasor.meteor.protocol.xmlrpc.session.XmlRpcSession;

/** XML-RPC publishes ordinary HTTP request/response methods through the protocol SPI. */
public final class XmlRpcProtocol implements HttpProtocolProvider {
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
        return "xmlrpc";
    }

    @Override
    public String defaultSerialize() {
        return "Json";
    }

    @Override
    public List<String> serviceKey(MetBindInfo<?> service, ProtocolBinding binding) {
        return List.of(binding.getDescription().path("name").asText());
    }

    @Override
    public ProtocolBinding prepareBinding(ProtocolConfig config, MetContext context, ProtocolMeta meta) {
        Map<Method, String> methods = XmlRpcCodec.prepareService(config, meta.getService());
        if (methods == null) {
            return null;
        }

        ObjectNode description = meta.createServiceDescription(config, this.defaultSerialize(), methods);
        return HttpServicePathUtils.bind(config, meta, methods, description);
    }

    @Override
    public ProtocolSession<HttpExchange> createSession(ProtocolConfig config, ProtocolSessionContext context, //
            NetworkChannel<HttpExchange> channel, ObjLongConsumer<Payload> messages) {
        return new XmlRpcSession(config, context, channel, messages);
    }
}
