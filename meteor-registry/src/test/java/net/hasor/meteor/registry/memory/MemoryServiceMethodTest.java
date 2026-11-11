/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.memory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import net.hasor.meteor.registry.shared.storage.domain.*;
import net.hasor.meteor.registry.shared.utils.RegistryJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryServiceMethodTest {

    @Test
    public void methodsAreAppendOnlyDeduplicatedAndIndependentOfLiveProviders() throws Exception {
        // 两个提供者报告同名重载，其中第二份类型图发生变化。
        {
            MemoryRegistryConfiguration configuration = new MemoryRegistryConfiguration();
            try (StorageAccess writer = configuration.openStorage(); StorageAccess reader = configuration.openStorage()) {
                EntInstanceRuntimeData instance = new EntInstanceRuntimeData();
                instance.setInstanceId("one");
                instance.setApplicationName("orders");
                instance.setIpAddress("127.0.0.1");
                instance.setProcessId(1L);
                writer.registerInstance(instance);
                EntServiceRuntimeData service = new EntServiceRuntimeData();
                service.setServiceId("[demo]Orders-1.0");
                service.setRole(MetServiceType.Provider);
                try (InputStream input = getClass().getResourceAsStream("/method-catalog.json")) {
                    service.setDescription(RegistryJson.read(new String(input.readAllBytes(), StandardCharsets.UTF_8), RefServiceDescriptionData.class));
                }
                // 重复上报和持久化往返不能产生重复的方法定义。
                service.getDescription().getMethods().get(0).setMethodId(1L);
                writer.registerService("one", service);
                writer.registerService("one", service);
                ServiceMethodPage first = reader.serviceMethods(service.getServiceId(), "find", new DirectoryQuery());
                assertEquals(1, first.getItems().size());
                assertEquals(2, first.getItems().get(0).getDefinitions().size());
                service.getDescription().setJavaType("example.AnotherInterface");
                writer.registerService("one", service);
                assertEquals(2, reader.serviceMethods(service.getServiceId(), "find", new DirectoryQuery()).getItems().get(0).getDefinitions().size());
                ObjectNode firstDefinition = RegistryJson.copy(first.getItems().get(0).getDefinitions().get(0), ObjectNode.class);
                assertFalse(firstDefinition.has("protocols"));
                assertTrue(firstDefinition.path("types").has("example.Node"));
                assertFalse(firstDefinition.path("types").has("example.Unused"));

                // 相同签名、不同字段结构应保留各自类型图；消费者不能追加目录。
                service.getDescription().getTypes().get("example.Node").putExtension("description", "new shape");
                writer.registerService("one", service);
                assertEquals(4, reader.serviceMethods(service.getServiceId(), "find", new DirectoryQuery()).getItems().get(0).getDefinitions().size());
                service.setRole(MetServiceType.Consumer);
                service.getDescription().getMethods().get(0).setName("consumerOnly");
                writer.registerService("one", service);
                assertTrue(reader.serviceMethods(service.getServiceId(), "consumerOnly", new DirectoryQuery()).getItems().isEmpty());
                writer.unregisterService("one", service.getServiceId());
                assertEquals(4, reader.serviceMethods(service.getServiceId(), "find", new DirectoryQuery()).getItems().get(0).getDefinitions().size());
                assertTrue(reader.serviceMethods(service.getServiceId() + "x", "find", new DirectoryQuery()).getItems().isEmpty());
                assertTrue(reader.serviceMethods(service.getServiceId(), "missing", new DirectoryQuery()).getItems().isEmpty());

                DirectoryQuery query = new DirectoryQuery();
                query.setLimit(1);
                ServiceMethodPage page = reader.serviceMethods(service.getServiceId(), null, query);
                assertTrue(page.isListingSupported());
                assertEquals(1, page.getItems().size());
                assertNotNull(page.getNextCursor());
                query.setCursor(page.getNextCursor());
                ServiceMethodPage next = reader.serviceMethods(service.getServiceId(), null, query);
                assertEquals(1, next.getItems().size());
                assertNotEquals(page.getItems().get(0).getName(), next.getItems().get(0).getName());
                assertNull(next.getNextCursor());
            }
            // 会话退出不删除已经上报的方法目录。
            try (StorageAccess reopened = configuration.openStorage()) {
                assertEquals(4, reopened.serviceMethods("[demo]Orders-1.0", "find", new DirectoryQuery()).getItems().get(0).getDefinitions().size());
            }
        }
    }

    @Test
    public void concurrentProvidersAppendWithoutLosingOverloads() throws Exception {
        // 独立会话同时首次上报同一方法，不能由最后一次写入覆盖其它定义。
        {
            MemoryRegistryConfiguration configuration = new MemoryRegistryConfiguration();
            CyclicBarrier barrier = new CyclicBarrier(4);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int index = 0; index < 4; index++) {
                    int current = index;
                    futures.add(executor.submit(() -> {
                        try (StorageAccess storage = configuration.openStorage()) {
                            EntInstanceRuntimeData instance = new EntInstanceRuntimeData();
                            instance.setInstanceId("concurrent-" + current);
                            instance.setApplicationName("orders");
                            instance.setIpAddress("127.0.0.1");
                            instance.setProcessId(1L);
                            storage.registerInstance(instance);
                            EntServiceRuntimeData service = new EntServiceRuntimeData();
                            service.setServiceId("concurrent-orders");
                            service.setRole(MetServiceType.Provider);
                            try (InputStream input = getClass().getResourceAsStream("/method-catalog.json")) {
                                ObjectNode description = RegistryJson.read(new String(input.readAllBytes(), StandardCharsets.UTF_8), ObjectNode.class);
                                ((ObjectNode) description.path("types").path("example.Node")).withArray("properties").addObject().put("name", "field" + current).put("type", "java.lang.String");
                                service.setDescription(RegistryJson.copy(description, RefServiceDescriptionData.class));
                            }
                            barrier.await(10, TimeUnit.SECONDS);
                            storage.registerService(instance.getInstanceId(), service);
                        }
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get(20, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
            try (StorageAccess reader = configuration.openStorage()) {
                assertEquals(8, reader.serviceMethods("concurrent-orders", "find", new DirectoryQuery()).getItems().get(0).getDefinitions().size());
            }
        }
    }
}
