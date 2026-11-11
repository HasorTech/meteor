/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.shared.storage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.meteor.registry.shared.storage.domain.EntServiceMethodData;
import net.hasor.meteor.registry.shared.storage.domain.RefServiceDescriptionData;
import net.hasor.meteor.registry.shared.utils.RegistryJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ServiceMethodUtilsTest {
    @Test
    public void persistedIntegerWidthDoesNotCreateAnotherDefinition() throws Exception {
        EntServiceMethodData report = new EntServiceMethodData();
        report.setServiceId("orders");
        report.setName("find");
        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        definition.putArray("methods").addObject().put("name", "find").put("methodId", 1L);
        report.getDefinitions().add(RegistryJson.copy(definition, RefServiceDescriptionData.class));
        EntServiceMethodData stored = RegistryJson.read(RegistryJson.write(report), EntServiceMethodData.class);
        assertEquals(1L, stored.getDefinitions().get(0).getMethods().get(0).getMethodId());
        assertEquals(1, ServiceMethodUtils.merge(stored, report).getDefinitions().size());
    }

    @Test
    public void historicalDuplicatesAreRemovedWithoutCollapsingDistinctContracts() throws Exception {
        EntServiceMethodData stored = new EntServiceMethodData();
        stored.setServiceId("orders");
        stored.setName("find");
        ObjectNode first = JsonNodeFactory.instance.objectNode();
        first.putArray("methods").addObject().put("name", "find").put("methodId", 9007199254740992L);
        ObjectNode second = first.deepCopy();
        ((ObjectNode) second.withArray("methods").get(0)).put("methodId", 9007199254740993L);
        stored.getDefinitions().add(RegistryJson.copy(first, RefServiceDescriptionData.class));
        stored.getDefinitions().add(RegistryJson.copy(first, RefServiceDescriptionData.class));
        stored.getDefinitions().add(RegistryJson.copy(second, RefServiceDescriptionData.class));
        EntServiceMethodData detached = ServiceMethodUtils.merge(null, stored);
        assertEquals(2, detached.getDefinitions().size());
        assertEquals(3, stored.getDefinitions().size());
        assertNotSame(stored.getDefinitions().get(0), detached.getDefinitions().get(0));
    }
}
