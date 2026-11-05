/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.nacos;
import java.nio.file.Path;
import java.util.UUID;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 为并存的 Nacos 测试应用分配独占数据目录。 */
public final class NacosTestUtils {
    private NacosTestUtils() {
    }

    public static MetConfiguration createConfiguration() {
        Path dataHome = Path.of(System.getProperty("java.io.tmpdir"), "meteor-" + UUID.randomUUID());
        return new MetConfiguration() //
                .setDataHome(dataHome);
    }
}
