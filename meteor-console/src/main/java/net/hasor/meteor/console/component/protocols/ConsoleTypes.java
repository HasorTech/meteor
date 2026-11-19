/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.protocols;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.hasor.cobble.ResourcesUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.setting.MergedSettings;
import net.hasor.cobble.setting.SettingNode;
import net.hasor.cobble.setting.Settings;
import net.hasor.cobble.setting.provider.StreamType;
import net.hasor.core.Init;
import net.hasor.core.Singleton;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** 从各协议包的同路径 XML 读取参数编辑提示。 */
@Singleton
public final class ConsoleTypes {
    private final Map<String, Map<String, ConsoleParameterType>> protocols = new LinkedHashMap<>();

    @Init
    public void init() throws IOException {
        MergedSettings settings = new MergedSettings();
        for (var resource : ResourcesUtils.getResources("META-INF/meteor/console-types.xml")) {
            try (var stream = resource.openStream()) {
                settings.loadStream(stream, StreamType.Xml);
            }
        }
        this.load(settings);
    }

    void load(Settings settings) {
        Map<String, ConsoleParameterType> defaults = new LinkedHashMap<>();
        for (SettingNode node : settings.getNodeArray("defaults")) {
            readTypes(node, defaults);
        }

        this.protocols.clear();
        for (SettingNode node : settings.getNodeArray("protocols.protocol")) {
            String name = node.getSubValue("name");
            if (StringUtils.isBlank(name)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CONSOLE_TYPES_PROTOCOL_ENTRY_IS);
            }
            Map<String, ConsoleParameterType> types = this.protocols.computeIfAbsent(name, key -> new LinkedHashMap<>(defaults));
            readTypes(node, types);
        }
    }

    public Map<String, ConsoleParameterType> types(String protocol) {
        return Map.copyOf(this.protocols.getOrDefault(protocol, Map.of()));
    }

    private static void readTypes(SettingNode node, Map<String, ConsoleParameterType> types) {
        for (SettingNode type : node.getSubNodes("type")) {
            String name = type.getSubValue("name");
            String label = type.getSubValue("type");
            String component = type.getSubValue("component");
            if (StringUtils.isBlank(name) || StringUtils.isBlank(label) || !(StringUtils.equals(component, "input") || StringUtils.equals(component, "textarea") || StringUtils.equals(component, "json"))) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_CONSOLE_TYPES_TYPE_CONFIGURATION, name);
            }
            types.put(name, new ConsoleParameterType(label, component, type.getSubValue("default")));
        }
    }
}
