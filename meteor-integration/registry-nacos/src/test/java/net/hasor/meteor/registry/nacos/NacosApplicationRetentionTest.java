/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.nacos;
import java.util.List;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.admin.MetClusterAdmin;
import net.hasor.meteor.registry.admin.MetDirectoryQuery;
import net.hasor.meteor.registry.admin.MetServiceAdmin;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.registry.nacos.NacosTestData.instance;
import static net.hasor.meteor.registry.nacos.NacosTestData.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 运行登记退出后，新管理会话仍能枚举持久应用目录。 */
public class NacosApplicationRetentionTest {
    @Test
    public void applicationRemainsListedAfterLastInstanceSessionCloses() throws Exception {
        // 最后一个应用会话登记实例和服务，正常关闭后再创建独立管理上下文。
        try (MockNacosSdk sdk = new MockNacosSdk()) {
            NacosRegistryConfiguration registry = sdk.configuration();
            registry.setManagementToken("nacos-test-management");
            try (StorageAccess application = registry.openStorage()) {
                application.registerInstance(instance("one"));
                application.registerService("one", service("echo"));
            }

            // 管理查询从扩展正式入口获得，不依赖已经关闭会话中的本地目录。
            MetConfiguration configuration = NacosTestUtils.createConfiguration() //
                    .addExtension(registry);
            try (MetContext management = configuration.buildContext()) {
                RegistryAdmin admin = management.getExtension(RegistryAdmin.class);
                MetClusterAdmin cluster = admin.getClusterAdmin();
                MetDirectoryQuery query = new MetDirectoryQuery();
                assertEquals(List.of("orders"), cluster.applications(query).getItems());
                assertTrue(cluster.instances("orders", query).getItems().isEmpty());

                // 服务定义同样保留，但已经没有运行中的提供者关系。
                MetServiceAdmin service = admin.getServiceAdmin("echo");
                assertEquals("echo", service.info().getServiceId());
                assertTrue(service.providers(null, query).getItems().isEmpty());
            }
        }
    }

    @Test
    public void applicationRemainsListedAfterNamingConnectionIsLost() throws Exception {
        // 应用登记完成后模拟 SDK 连接意外断开，不执行存储会话的主动撤回。
        try (MockNacosSdk sdk = new MockNacosSdk()) {
            NacosRegistryConfiguration registry = sdk.configuration();
            registry.setManagementToken("nacos-test-management");
            try (StorageAccess application = registry.openStorage()) {
                application.registerInstance(instance("one"));
                application.registerService("one", service("echo"));
                sdk.namingClients.get(0).shutDown();

                // 断开后新建管理连接，持久目录仍列出应用，临时实例和成员均已消失。
                MetConfiguration configuration = NacosTestUtils.createConfiguration() //
                        .addExtension(registry);
                try (MetContext management = configuration.buildContext()) {
                    RegistryAdmin admin = management.getExtension(RegistryAdmin.class);
                    MetClusterAdmin cluster = admin.getClusterAdmin();
                    MetDirectoryQuery query = new MetDirectoryQuery();
                    assertEquals(List.of("orders"), cluster.applications(query).getItems());
                    assertTrue(cluster.instances("orders", query).getItems().isEmpty());
                    MetServiceAdmin service = admin.getServiceAdmin("echo");
                    assertEquals("echo", service.info().getServiceId());
                    assertTrue(service.providers(null, query).getItems().isEmpty());
                }
            }
        }
    }
}
