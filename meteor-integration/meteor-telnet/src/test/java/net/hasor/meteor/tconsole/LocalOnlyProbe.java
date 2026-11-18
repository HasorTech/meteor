/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import java.nio.file.Path;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.tconsole.client.TelClient;

/** 在移除注册中心库的真实类路径中执行，不能意外依赖测试进程的注册中心类型。 */
public final class LocalOnlyProbe {
    private LocalOnlyProbe() {
    }

    @MetService(group = "test", name = "Echo", version = "1")
    public interface Echo {
        String echo(String value);
    }

    public static String run(Path directory) throws Exception {
        MetConfiguration config = new MetConfiguration().setDataHome(directory);
        config.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());
            // 在完全没有注册中心类的情况下，经过正式命令入口执行本地治理。
            String status = client.sendCommand("status");
            String listed = client.sendCommand("services");
            String put = client.sendCommand("rule put '" + id + "' flow '<controlSet/>'");
            String read = client.sendCommand("rule show '" + id + "' flow");
            String metadata = client.sendCommand("meta put '" + id + "' source '\"local\"'");
            String disabled = client.sendCommand("service disable '" + id + "'");
            return status + "\nListed: " + listed.contains(id) + "\nPut: " + put + "\nRead: " + read + "\nMetadata: " + metadata + "\nDisabled: " + disabled + "\nEffective metadata: " + context.getServiceInfo(id).getMetaData("source") + "\nEffective enabled: " + context.getUpdater().isServiceEnabled(id);
        }
    }
}
