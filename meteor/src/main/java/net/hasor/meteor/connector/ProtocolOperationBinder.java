/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.meteor.domain.ProtocolConfig;

/** Prepares the native operation data required by the normal protocol codec. */
public interface ProtocolOperationBinder {
    /** Compile the selected operation from its published protocol contract and supply its binding before returning. */
    void prepareOperation(ProtocolConfig config, JsonNode contract, Target target, Binding<Object> binding);

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
