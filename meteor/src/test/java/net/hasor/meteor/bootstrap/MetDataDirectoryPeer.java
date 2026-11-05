/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.io.IOException;
import java.nio.file.Path;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;

/** 独立 JVM 通过公开构建入口验证操作系统级目录锁。 */
public final class MetDataDirectoryPeer {
    private MetDataDirectoryPeer() {
    }

    public static void main(String[] arguments) throws Exception {
        MetConfiguration configuration = new MetConfiguration() //
                .setDataHome(Path.of(arguments[1])) //
                .setApplicationName("persistent-instance-test");
        configuration.connector("tcp") //
                .protocol("rsf");

        // probe 在已被占用的目录中构建，持锁模式等待外部进程结束自己。
        if (StringUtils.equals("probe", arguments[0])) {
            try (MetContext ignored = configuration.buildContext()) {
                System.out.println("METEOR_LOCK_UNEXPECTEDLY_ACQUIRED");
                System.exit(24);
            } catch (IOException failure) {
                System.out.println("METEOR_LOCK_REJECTED: " + failure.getMessage());
                System.exit(23);
            }
        }

        try (MetContext context = configuration.buildContext()) {
            System.out.println("METEOR_LOCK_READY=" + context.getInstanceID());
            System.out.flush();
            System.in.read();
        }
    }
}
