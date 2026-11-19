/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.meteor.address.InterAddress;

/** Supplies the published service and protocol contracts for RPC target discovery. */
public interface MetServiceResolver {
    /**
     * Finds an endpoint matching the service, protocol and optional fixed address.
     * Pass the matching publication to the supplied factory, or return null when there is no match.
     */
    <T> T resolve(String serviceId, String protocol, InterAddress address, EndpointFactory<T> factory) throws Exception;

    @FunctionalInterface
    interface EndpointFactory<T> {
        T create(InterAddress address, String group, String name, String version, String protocol, String requestPath, //
                JsonNode serviceDescription, JsonNode protocolDescription);
    }
}