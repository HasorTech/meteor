/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.admin;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import net.hasor.meteor.registry.shared.DecoratedRegistryConfiguration;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import net.hasor.meteor.registry.shared.storage.domain.EntInstanceRuntimeData;
import net.hasor.meteor.registry.shared.storage.domain.EntServiceRuntimeData;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.registry.shared.RegistryTestUtils.createConfiguration;
import static org.junit.jupiter.api.Assertions.*;

/** 大目录验证读取范围和单页契约，不作为真实集群性能基准。 */
public class DirectoryPaginationTest {
    @Test
    public void ipSearchFillsPagesAfterExcludingProcessIdMatches() throws Exception {
        // 每个实例的 PID 都匹配 50，只有交错的三个 IP 真正匹配。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration configuration = createConfiguration() //
                .addExtension(registry);
        try (StorageAccess writer = registry.openStorage(); //
             MetContext context = configuration.buildContext()) {
            for (int index = 0; index < 6; index++) {
                EntInstanceRuntimeData instance = new EntInstanceRuntimeData();
                instance.setInstanceId("node-" + index);
                instance.setApplicationName("orders");
                instance.setProcessId(50L);
                instance.setIpAddress(index % 2 == 1 ? "192.0.2.50" : "198.51.100.7");
                writer.registerInstance(instance);
                EntServiceRuntimeData service = new EntServiceRuntimeData();
                service.setServiceId("orders");
                service.setRole(MetServiceType.Provider);
                writer.registerService(instance.getInstanceId(), service);
            }

            // 通过正式管理入口查询，每页容量在精确 IP 筛选后计算。
            MetServiceAdmin admin = context.getExtension(RegistryAdmin.class).getServiceAdmin("orders");
            MetDirectoryQuery query = new MetDirectoryQuery();
            query.setField("ip");
            query.setSearch("50");
            query.setLimit(2);
            MetPage<MetServiceMember> first = admin.providers(null, query);
            assertEquals(2, first.getItems().size());
            assertNotNull(first.getNextCursor());
            Set<String> found = new HashSet<>();
            for (MetServiceMember member : first.getItems()) {
                assertEquals("192.0.2.50", member.getInstance().getIpAddress());
                assertTrue(found.add(member.getInstance().getInstanceId()));
            }

            // 后续页继续排除 PID 命中，且没有重复或丢失 IP 匹配实例。
            query.setCursor(first.getNextCursor());
            MetPage<MetServiceMember> last = admin.providers(null, query);
            assertEquals(1, last.getItems().size());
            assertNull(last.getNextCursor());
            assertTrue(found.add(last.getItems().get(0).getInstance().getInstanceId()));
            assertEquals(Set.of("node-1", "node-3", "node-5"), found);
        }
    }

    @Test
    public void fiftyThousandArchivedInstancesDoNotExpandApplicationPageQueries() throws Exception {
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        // 200 个应用，每个应用 250 个实例；会话退出后仍保留档案。
        try (StorageAccess writer = registry.openStorage()) {
            for (int app = 0; app < 200; app++) {
                for (int member = 0; member < 250; member++) {
                    EntInstanceRuntimeData instance = new EntInstanceRuntimeData();
                    instance.setApplicationName("app-" + app);
                    instance.setInstanceId("app-" + app + "-node-" + member);
                    instance.setUnitName("default");
                    writer.registerInstance(instance);
                }
            }
        }
        Map<String, Integer> reads = new HashMap<>();
        DecoratedRegistryConfiguration observed = new DecoratedRegistryConfiguration(registry, delegate -> {
            return (StorageAccess) Proxy.newProxyInstance(StorageAccess.class.getClassLoader(), new Class<?>[] { StorageAccess.class }, (proxy, method, args) -> {
                String operation = method.getName();
                reads.merge(operation, 1, Integer::sum);
                assertFalse(Set.of("getInstance", "instanceServices", "members").contains(operation), "No enrichment reads");
                if (StringUtils.equals(operation, "instances")) {
                    assertEquals("app-42", args[0]);
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
        });
        observed.setManagementToken("secret");
        MetConfiguration configuration = createConfiguration() //
                .addExtension(observed);
        try (MetContext context = configuration.buildContext()) {
            MetClusterAdmin admin = context.getExtension(RegistryAdmin.class).getClusterAdmin();
            MetDirectoryQuery query = new MetDirectoryQuery();
            query.setLimit(25);
            assertEquals(25, admin.applications(query).getItems().size());
            assertEquals(Integer.valueOf(1), reads.get("applications"));
            assertFalse(reads.containsKey("instances"));

            // 进入一个应用后只读取该应用的 10 页档案，且没有重复或遗漏。
            Set<String> instances = new HashSet<>();
            do {
                MetPage<MetInstanceInfo> page = admin.instances("app-42", query);
                assertTrue(page.getItems().size() <= 25);
                for (MetInstanceInfo instance : page.getItems()) {
                    assertFalse(instance.isOnline());
                    assertTrue(instances.add(instance.getInstanceId()));
                }
                query.setCursor(page.getNextCursor());
            } while (query.getCursor() != null);
            assertEquals(250, instances.size());
            assertEquals(Integer.valueOf(10), reads.get("instances"));
        }
    }
}
