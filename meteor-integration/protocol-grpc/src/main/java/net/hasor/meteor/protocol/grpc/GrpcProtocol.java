/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjLongConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.ProtocolSession;
import net.hasor.meteor.connector.ProtocolSessionContext;
import net.hasor.meteor.connector.RouteMountIndex;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.connector.transport.http.HttpProtocolProvider;
import net.hasor.meteor.connector.transport.http.HttpServicePathUtils;
import net.hasor.meteor.connector.transport.http.channel.HttpExchange;
import net.hasor.meteor.domain.ContextPath;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.payload.Payload;
import net.hasor.meteor.protocol.grpc.binding.GrpcServiceBinding;
import net.hasor.meteor.protocol.grpc.builtin.GrpcHealthService;
import net.hasor.meteor.protocol.grpc.builtin.GrpcReflectionService;
import net.hasor.meteor.protocol.grpc.builtin.GrpcReflectionV1AlphaService;
import net.hasor.meteor.protocol.grpc.session.GrpcMountRuntime;
import net.hasor.meteor.protocol.grpc.session.GrpcSession;

/** Grpc SPI provider using the same session contract as every other protocol. */
public final class GrpcProtocol implements HttpProtocolProvider {
    private static final Set<Class<?>> STANDARD_SERVICES = Set.of(GrpcHealthService.class, GrpcReflectionService.class, GrpcReflectionV1AlphaService.class);

    @Override
    public Set<Integer> httpVersions(ProtocolConfig config) {
        return Set.of(2);
    }

    @Override
    public Set<String> endpointTypes() {
        return Set.of("endpoint", "http", "schema");
    }

    private final Map<ProtocolConfig, GrpcMountRuntime> runtimes = new ConcurrentHashMap<>();

    public String name() {
        return "grpc";
    }

    @Override
    public String defaultSerialize() {
        return "Protobuf";
    }

    @Override
    public List<String> serviceKey(MetBindInfo<?> service, ProtocolBinding binding) {
        return List.of(((GrpcServiceBinding) binding.getDefinition()).serviceName());
    }

    @Override
    public void prepareOperation(ProtocolConfig config, JsonNode contract, Target target, Binding<Object> binding) {
        GrpcServiceBinding.prepareOperation(contract, target, binding);
    }

    @Override
    public ProtocolBinding prepareBinding(ProtocolConfig config, MetContext context, ProtocolMeta meta) {
        GrpcMountRuntime.validateConfiguration(config);
        GrpcServiceBinding definition = GrpcServiceBinding.prepare(config, meta, this.defaultSerialize());
        if (definition == null) {
            return null;
        }

        // 从同一份端点定义导出契约，一次创建完整发布绑定。
        ObjectNode description = meta.createServiceDescription(config, this.defaultSerialize());
        definition.describeContract(description);
        ContextPath declared = meta.getContextPath();
        ProtocolBinding binding;
        if (declared != null && declared.isExact() && STANDARD_SERVICES.contains(meta.getService().getBindType())) {
            // 标准 Health 和 Reflection 的生成桩固定使用根方法路径。
            description.put("contextExact", true);
            binding = new ProtocolBinding(declared, definition, description);
        } else {
            binding = HttpServicePathUtils.bind(config, meta, definition, description);
        }

        if (definition.schemaResource() != null) {
            definition.schemaResource().describe(binding, List.of(definition.serviceName()));
        }
        return binding;
    }

    public ProtocolSession<HttpExchange> createSession(ProtocolConfig config, ProtocolSessionContext context, //
            NetworkChannel<HttpExchange> connection, ObjLongConsumer<Payload> messages) {
        GrpcMountRuntime runtime = this.runtime(config, context.routes()).retain(context.metContext());
        try {
            return new GrpcSession(context, connection, messages, runtime);
        } catch (RuntimeException | Error failure) {
            runtime.release();
            throw failure;
        }
    }

    private GrpcMountRuntime runtime(ProtocolConfig config, RouteMountIndex routes) {
        return this.runtimes.computeIfAbsent(config, key -> new GrpcMountRuntime(key, routes));
    }
}
