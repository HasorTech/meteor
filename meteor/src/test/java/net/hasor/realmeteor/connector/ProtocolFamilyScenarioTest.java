/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.realmeteor.connector;
import java.util.Set;
import net.hasor.example.bootstrap.TestConfigurationUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.bootstrap.MetContextImpl;
import net.hasor.meteor.connector.ConnectorManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtocolFamilyScenarioTest {
    @Test
    public void udpFamilyComesFromThePluginInsteadOfItsAddressScheme() throws Exception {
        // 地址前缀可以由应用指定，不能依赖它识别实际传输类型。
        MetConfiguration configuration = TestConfigurationUtils.create();
        configuration.connector("udp") //
                .protocol("testprotocol").addressScheme("custom-datagram");
        try (MetContext context = configuration.buildContext()) {
            context.start();
            ConnectorManager connectors = ((MetContextImpl) context).getConnectorManager();
            String connector = context.getSettings().getConnectorConfigs().iterator().next().name();
            assertEquals(Set.of("UDP"), connectors.getProtocolFamilies(connector, "testprotocol"));
            assertEquals(Set.of(), connectors.getProtocolFamilies(connector, "missing"));
            assertEquals(Set.of(), connectors.getProtocolFamilies("missing", "testprotocol"));
        }
    }
}
