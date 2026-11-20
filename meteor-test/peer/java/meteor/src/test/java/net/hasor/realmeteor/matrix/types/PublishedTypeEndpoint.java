/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.matrix.types;
import java.util.Arrays;
import java.util.Objects;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.admin.MetEndpointType;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;

/** 通过同一场景的注册目录读取外部 SDK 使用的 HTTP 接入点。 */
public final class PublishedTypeEndpoint {
    private PublishedTypeEndpoint() {
    }

    public static MemoryRegistryConfiguration registry() {
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("type-matrix-tests");
        return registry;
    }

    public static String httpAddress(MetContext provider, MetBindInfo<?> service, String protocol) {
        return address(provider, service, protocol, MetEndpointType.HTTP);
    }

    public static String schemaAddress(MetContext provider, MetBindInfo<?> service, String protocol) {
        return address(provider, service, protocol, MetEndpointType.SCHEMA);
    }

    private static String address(MetContext provider, MetBindInfo<?> service, String protocol, MetEndpointType type) {
        RegistryAdmin registry = provider.getExtension(RegistryAdmin.class);
        var admin = registry.getServiceAdmin(service.getBindID(), provider.getInstanceID());
        return admin.providers(null, null).getItems().stream() //
                .flatMap(member -> Arrays.stream(member.getEndpoints())) //
                .filter(endpoint -> StringUtils.equals(protocol, endpoint.getProtocolName())) //
                .flatMap(endpoint -> endpoint.getEndpoints().values().stream()) //
                .map(addresses -> addresses.get(type.getKey())) //
                .filter(Objects::nonNull) //
                .findFirst() //
                .orElseThrow(() -> new IllegalStateException("No published " + type.getKey() + " endpoint for " + service.getBindID()));
    }
}
