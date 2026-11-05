/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.support;
import java.nio.file.Path;
import java.util.UUID;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 为同一测试进程中的各个 Meteor 实例分配独立数据目录。 */
public final class TestConfigurationUtils {
    private TestConfigurationUtils() {
    }

    public static MetConfiguration create() {
        // 测试启动器将 java.io.tmpdir 隔离到所属工程的 build 目录。
        Path dataHome = Path.of(System.getProperty("java.io.tmpdir"), "meteor-" + UUID.randomUUID());
        return new MetConfiguration() //
                .setDataHome(dataHome);
    }
}
