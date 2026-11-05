/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.bootstrap;
import java.nio.file.Path;
import java.util.UUID;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 测试上下文各自持有数据目录，避免并行场景争用实例锁。 */
public final class TestConfigurationUtils {
    private TestConfigurationUtils() {
    }

    public static MetConfiguration create() {
        return new MetConfiguration() //
                .setDataHome(newDataHome());
    }

    public static Path newDataHome() {
        return Path.of(System.getProperty("java.io.tmpdir"), "meteor-" + UUID.randomUUID());
    }
}
