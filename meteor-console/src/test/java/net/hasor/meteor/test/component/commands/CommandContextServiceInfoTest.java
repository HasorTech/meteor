/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.component.commands;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import net.hasor.cobble.setting.Settings;
import net.hasor.core.AppContext;
import net.hasor.core.Hasor;
import net.hasor.meteor.console.component.commands.CommandDispatcher;
import net.hasor.meteor.console.component.commands.dto.service.ServiceInfoRequest;
import net.hasor.meteor.console.component.provider.ProviderConnection;
import net.hasor.meteor.console.component.provider.ProviderFactories;
import net.hasor.meteor.console.component.provider.ProviderFactory;
import net.hasor.meteor.console.dao.ClusterDao;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.auth.ConsoleCookies;
import net.hasor.meteor.console.global.auth.SessionService;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.RegistryTokenProvider;
import net.hasor.meteor.registry.admin.MetServiceAdmin;
import net.hasor.meteor.registry.admin.MetServiceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CommandContextServiceInfoTest {
    @TempDir
    Path temporary;

    @Test
    void wholeServiceInformationDoesNotReadInstanceDescriptionsOrMembers() throws Exception {
        // 整体服务查询只允许读取摘要和开关，读取实例描述或成员时立即失败。
        MetServiceInfo info = new MetServiceInfo();
        MetServiceAdmin service = (MetServiceAdmin) Proxy.newProxyInstance(getClass().getClassLoader(), //
                new Class<?>[] { MetServiceAdmin.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "info" -> info;
                    case "isEnable" -> true;
                    default -> throw new AssertionError("Unexpected instance query: " + method.getName());
                });
        RegistryAdmin registry = (RegistryAdmin) Proxy.newProxyInstance(getClass().getClassLoader(), //
                new Class<?>[] { RegistryAdmin.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getServiceAdmin" -> service;
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected registry query: " + method.getName());
                });
        ManagementTarget target = new ManagementTarget();
        target.setServiceId("orders");
        ServiceInfoRequest request = new ServiceInfoRequest(target);

        ProviderFactory factory = new ProviderFactory() {
            @Override
            public String name() {
                return "jdbc";
            }

            @Override
            public ProviderConnection open(ClusterProfile profile, RegistryTokenProvider credentials) {
                return ProviderConnection.registry(registry);
            }
        };
        Hasor hasor = Hasor.create().registerShutdownHook(false).addModules(binder -> {
            binder.bindType(MeteorConfig.class).asEagerSingleton();
            binder.bindType(ClusterDao.class).asEagerSingleton();
            binder.bindType(ConsoleCookies.class).asEagerSingleton();
            binder.bindType(SessionService.class).asEagerSingleton();
            binder.bindType(CommandDispatcher.class);
            binder.bindType(ProviderFactories.class);
            binder.bindType(ProviderFactory.class).toInstance(factory);
        });
        hasor.addSettings(Settings.DefaultNameSpace, "hasor.boot.web.connectors.http.enabled", false);
        hasor.addSettings(Settings.DefaultNameSpace, "data.directory", this.temporary.toString());
        hasor.addSettings(Settings.DefaultNameSpace, "security.enable", false);

        // 通过容器中的命令入口查询；没有实例范围时不访问任何实例清单。
        try (AppContext app = hasor.build()) {
            ClusterProfile profile = new ClusterProfile();
            profile.setId("commands");
            profile.setName("服务摘要测试");
            profile.setType("jdbc");
            profile.setDatabase("h2");
            profile.setAddress("jdbc:h2:mem:commands");
            app.getInstance(ClusterDao.class).save(profile);
            var result = app.getInstance(CommandDispatcher.class).execute("commands", request);
            assertSame(info, result.info());
            assertTrue(result.enabled());
            assertNull(result.description());
            assertEquals(0, result.endpoints().length);
        }
    }
}
