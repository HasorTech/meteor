/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.service;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.hasor.cobble.i18n.I18nUtils;
import net.hasor.core.Inject;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.component.provider.ProviderFactories;
import net.hasor.meteor.console.component.settings.ConsoleCapabilities;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.registry.admin.MetEndpointType;

/** 只输出前端需要的公开定义和本机能力，不输出部署配置或凭据。 */
public final class ConsoleSettingsService {
    private static final Map<Locale, Map<String, String>> WEB_MESSAGES = loadWebBundles();

    @Inject
    private MeteorConfig        config;
    @Inject
    private ConsoleCapabilities capabilities;
    @Inject
    private ProviderFactories   providers;

    public Map<String, Object> languageBundle(Locale locale) {
        return Map.of("locale", locale.toLanguageTag(), "messages", WEB_MESSAGES.get(locale));
    }

    public Map<String, Object> settings(Locale locale) {
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
                        "registryTypes", registryTypes, "jdbcDatabases", this.capabilities.jdbcDatabases(locale)), //
                "endpointTypes", endpointTypes(locale));
    }

    public static List<Map<String, String>> endpointTypes(Locale locale) {
        return Arrays.stream(MetEndpointType.values()) //
                .map(type -> Map.of("key", type.getKey(), "label", type == MetEndpointType.MANAGEMENT ? ConsoleI18n.getMessage(ConsoleMessageKey.LABEL_MANAGEMENT, locale) : type.getLabel())).toList();
    }

    private static Map<Locale, Map<String, String>> loadWebBundles() {
        Map<Locale, Map<String, String>> bundles = new HashMap<>();
        for (Locale locale : List.of(ConsoleI18n.DEFAULT_LOCALE, Locale.US)) {
            String path = "/META-INF/meteor/i18n/web_" + I18nUtils.toI18nKey(locale) + ".properties";
            try (InputStream in = ConsoleSettingsService.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Missing console message resource: " + path);
                }

                Properties source = new Properties();
                source.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                Map<String, String> messages = new HashMap<>();
                source.forEach((key, value) -> messages.put(key.toString(), value.toString()));
                bundles.put(locale, Map.copyOf(messages));
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read console message resource: " + path, failure);
            }
        }
        if (!bundles.get(ConsoleI18n.DEFAULT_LOCALE).keySet().equals(bundles.get(Locale.US).keySet())) {
            throw new IllegalStateException("Console Web message resource keys differ");
        }
        return Map.copyOf(bundles);
    }
}
