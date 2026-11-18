/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.basic;
import java.nio.file.Path;
import java.util.Map;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetUpdater;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 直接使用现有 API 治理当前应用；不连接注册中心。 */
public final class LocalGovernanceMain {
    private LocalGovernanceMain() {
    }

    public static void main(String[] arguments) throws Exception {
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("local-governance") //
                .setDataHome(Path.of("build", "local-governance"));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        try (MetContext context = configuration.buildContext()) {
            // 发布关系由 publisher 维护，元信息直接属于服务绑定。
            MetBindInfo<EchoService> binding = context.publisher() //
                    .rsfService(EchoService.class, value -> value).register();
            binding.setMetaData(Map.of("owner", "payments"));
            System.out.println("Owner: " + binding.getMetaData("owner"));
            binding.removeMetaData("owner");
            context.start();

            // 更新器管理运行配置，停用不会撤销服务发布。
            MetUpdater updater = context.getUpdater();
            updater.setServiceEnabled(binding.getBindID(), false);
            System.out.println("Service enabled: " + updater.isServiceEnabled(binding.getBindID()));
            updater.setServiceEnabled(binding.getBindID(), true);
            updater.updateFlowControl(binding.getBindID(), "<controlSet/>");
            System.out.println("Flow control: " + updater.flowControl(binding.getBindID()));
            updater.updateFlowControl(binding.getBindID(), "");

            // 阻断只拒绝业务请求，不等于监听器下线。
            updater.setBlock(true);
            System.out.println("Blocked: " + updater.isBlock() + ", online: " + context.isOnline());
            updater.setBlock(false);
            context.offline();
            System.out.println("Offline: " + !context.isOnline());
            context.online();
            System.out.println("Restored: " + context.isOnline());
        }
    }
}
