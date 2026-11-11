/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.hessian;
import java.util.List;
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
import net.hasor.meteor.protocol.hessian.codec.HessianCodec;
import net.hasor.meteor.protocol.hessian.codec.HessianServiceBinding;
import net.hasor.meteor.protocol.hessian.session.HessianSession;

/** Hessian RPC 协议入口，按 HTTP 挂载点发布普通 Java 服务。 */
public final class HessianProtocol implements HttpProtocolProvider {
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
        return "hessian";
    }

    @Override
    public String defaultSerialize() {
        return "Hessian";
    }

    @Override
    public List<String> serviceKey(MetBindInfo<?> service, ProtocolBinding binding) {
        return List.of(binding.getDescription().path("name").asText());
    }

    @Override
    public ProtocolBinding prepareBinding(ProtocolConfig config, MetContext context, ProtocolMeta meta) {
        HessianServiceBinding definition = HessianCodec.prepareService(meta.getService());
        if (definition == null) {
            return null;
        }
        ObjectNode description = meta.createServiceDescription(config, this.defaultSerialize());
        definition.describeContract(description);
        ProtocolBinding binding = HttpServicePathUtils.bind(config, meta, definition, description);
        if (binding.getContextPath() != null) {
            // 原生 Hessian 客户端调用服务 URL，Meteor 地址仍保留当前服务挂载目录。
            ObjectNode endpoints = description.putObject("endpoints");
            for (String path : binding.getContextPath().getPaths()) {
                String endpoint = path;
                if (!binding.getContextPath().isExact()) {
                    endpoint = (path.endsWith("/") ? path : path + "/") + description.path("name").asText();
                }
                endpoints.putObject(path).put("http", endpoint);
            }
        }
        return binding;
    }

    @Override
    public ProtocolSession<HttpExchange> createSession(ProtocolConfig config, ProtocolSessionContext context,//
            NetworkChannel<HttpExchange> channel, ObjLongConsumer<Payload> messages) {
        return new HessianSession(config, context, channel, messages);
    }
}
