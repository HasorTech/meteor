/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.service;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.hasor.core.Inject;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.component.provider.ProviderFactories;
import net.hasor.meteor.console.component.settings.ConsoleCapabilities;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.registry.admin.MetEndpointType;

/** 只输出前端需要的公开定义和本机能力，不输出部署配置或凭据。 */
public final class ConsoleSettingsService {
    @Inject
    private MeteorConfig        config;
    @Inject
    private ConsoleCapabilities capabilities;
    @Inject
    private ProviderFactories   providers;

    public Map<String, Object> settings() {
        List<Map<String, String>> registryTypes = this.providers.names().stream().map(name -> {
            String label = switch (name) {
                case "remote" -> "Remote";
                case "nacos" -> "Nacos";
                case "jdbc" -> "JDBC";
                case "zookeeper" -> "ZooKeeper";
                default -> name;
            };
            return Map.of("key", name, "label", label);
        }).toList();
        return Map.of("version", ExampleTemplates.version(), "localUnit", this.config.getUnit(), //
                "features", Map.of("debugProtocols", this.capabilities.debugProtocols(), //
                        "registryTypes", registryTypes, "jdbcDatabases", this.capabilities.jdbcDatabases()), //
                "endpointTypes", endpointTypes());
    }

    public static List<Map<String, String>> endpointTypes() {
        return Arrays.stream(MetEndpointType.values()) //
                .map(type -> Map.of("key", type.getKey(), "label", type.getLabel())).toList();
    }
}
