/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.hasor.cobble.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class TconsoleClasspathTest {
    @TempDir
    Path directory;

    @Test
    public void runsWithoutRegistryLibraryOnClasspath() throws Exception {
        // 复用测试已解析的类路径，仅移除可选注册中心模块。
        Set<URL> urls = new LinkedHashSet<>();
        for (String entry : System.getProperty("tconsole.test.classpath").split(File.pathSeparator)) {
            if (!entry.contains("meteor-registry")) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        assertFalse(urls.isEmpty());
        AtomicBoolean registryManagerLoaded = new AtomicBoolean();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (StringUtils.equals(name, "net.hasor.meteor.tconsole.governance.MetRegistryGovernanceManager")) {
                    registryManagerLoaded.set(true);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("net.hasor.meteor.registry.RegistryAdmin"));
            Thread.currentThread().setContextClassLoader(isolated);
            // 接口、公共父类和本地实现均不触发可选依赖解析。
            isolated.loadClass("net.hasor.meteor.tconsole.MetGovernanceManager").getDeclaredMethods();
            isolated.loadClass("net.hasor.meteor.tconsole.governance.AbstractGovernanceManager").getDeclaredMethods();
            isolated.loadClass("net.hasor.meteor.tconsole.governance.MetLocalGovernanceManager").getDeclaredMethods();

            // 在隔离类加载器中完成上下文启动、真实 Telnet 查询及关闭。
            Class<?> probe = isolated.loadClass(LocalOnlyProbe.class.getName());
            String status = (String) probe.getMethod("run", Path.class).invoke(null, this.directory);
            assertTrue(status.contains("Governance : LOCAL"), status);
            assertTrue(status.contains("Listed: true"), status);
            assertTrue(status.contains("Put: OK"), status);
            assertTrue(status.contains("Read: <controlSet/>"), status);
            assertTrue(status.contains("Metadata: OK"), status);
            assertTrue(status.contains("Disabled: OK"), status);
            assertTrue(status.contains("Effective metadata: local"), status);
            assertTrue(status.contains("Effective enabled: false"), status);
            assertFalse(registryManagerLoaded.get(), "Local governance must not load the registry implementation");
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    public void registryLibraryWithoutConfigurationStillUsesLocalGovernance() throws Exception {
        // 保留注册中心依赖，但应用没有接入注册中心。
        Set<URL> urls = new LinkedHashSet<>();
        for (String entry : System.getProperty("tconsole.test.classpath").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            assertNotNull(isolated.loadClass("net.hasor.meteor.registry.RegistryAdmin"));
            Thread.currentThread().setContextClassLoader(isolated);

            // 依赖是否存在不决定治理模式，实际配置才决定。
            Class<?> probe = isolated.loadClass(LocalOnlyProbe.class.getName());
            String status = (String) probe.getMethod("run", Path.class).invoke(null, this.directory);
            assertTrue(status.contains("Governance : LOCAL"), status);
            assertTrue(status.contains("Put: OK"), status);
            assertTrue(status.contains("Effective metadata: local"), status);
            assertTrue(status.contains("Effective enabled: false"), status);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

}
