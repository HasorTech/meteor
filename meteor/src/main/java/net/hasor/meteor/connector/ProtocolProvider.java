/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector;
import java.util.function.ObjLongConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.payload.Payload;

/** Protocol SPI for metadata, service and operation binding, and session creation. */
public interface ProtocolProvider<M> {
    String name();

    String transportType();

    /** Default serializer for calls sent through this protocol. */
    String defaultSerialize();

    Class<M> messageType();

    /** 校验服务并准备定义与描述，通过 meta.build 创建完整绑定；null 表示拒绝，异常撤销本次登记。 */
    ProtocolBinding prepareBinding(ProtocolConfig config, MetContext context, ProtocolMeta meta);

    /**
     * Prepares the selected operation from the published protocol contract.
     * Supply a binding when the codec needs additional data or a different request path.
     */
    void prepareOperation(ProtocolConfig config, JsonNode contract, Target target, Binding<Object> binding);

    ProtocolSession<M> createSession(ProtocolConfig config, ProtocolSessionContext context, //
            NetworkChannel<M> connection, ObjLongConsumer<Payload> messages);

    /** Read-only metadata for the selected operation and mount. */
    interface Target {
        String serviceName();

        String methodName();

        /** The published HTTP mount path, or null on transports without a path. */
        String requestPath();

        Class<?> returnType();

        /** Java binary name from the published service declaration, available even without the business class. */
        String declaredReturnType();
    }

    /** Supplies one codec-owned binding and an optional override for the request path. */
    @FunctionalInterface
    interface Binding<T> {
        void bind(String path, T value);
    }
}
