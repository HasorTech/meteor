/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.protocol.hessian;
import java.nio.file.Path;
import java.util.UUID;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 为每个测试上下文分配独立的数据目录。 */
public final class TestConfigurationUtils {
    private TestConfigurationUtils() {
    }

    public static MetConfiguration create() {
        Path dataHome = Path.of(System.getProperty("java.io.tmpdir"), "meteor-" + UUID.randomUUID());
        return new MetConfiguration() //
                .setDataHome(dataHome);
    }
}
