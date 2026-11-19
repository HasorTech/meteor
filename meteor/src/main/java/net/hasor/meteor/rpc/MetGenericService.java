/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.meteor.address.InterAddress;

/** The published service and protocol contracts for one endpoint, independent of business classes. */
final class MetGenericService {
    private final String            group;
    private final String            name;
    private final String            version;
    private final String            protocol;
    private final PublishedContract contract;
    private final JsonNode          endpoint;
    private final InterAddress      address;

    /**
     * The descriptions use the same data format as published service metadata.
     * For HTTP, requestPath is the effective service path reported by the provider.
     */
    MetGenericService(InterAddress address, String group, String name, String version, String protocol, String requestPath, //
            JsonNode service, JsonNode endpoint) {
        this.address = Objects.requireNonNull(address, "address");
        this.group = Objects.requireNonNull(group, "group");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint").deepCopy();
        this.contract = new PublishedContract(protocol, requestPath, Objects.requireNonNull(service, "service"), this.endpoint);
    }

    InterAddress getAddress() {
        return this.address;
    }

    String getGroup() {
        return this.group;
    }

    String getName() {
        return this.name;
    }

    String getVersion() {
        return this.version;
    }

    String getProtocol() {
        return this.protocol;
    }

    PublishedContract getContract() {
        return this.contract;
    }

    JsonNode getProtocolDescription() {
        return this.endpoint.deepCopy();
    }
}
