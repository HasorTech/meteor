/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.dubbo;
import java.nio.file.Path;
import java.util.UUID;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 为每个测试应用分配独占的数据目录。 */
public final class DubboTestUtils {
    private DubboTestUtils() {
    }

    public static MetConfiguration createConfiguration() {
        Path dataHome = Path.of(System.getProperty("java.io.tmpdir"), "meteor-" + UUID.randomUUID());
        return new MetConfiguration() //
                .setDataHome(dataHome);
    }
}
