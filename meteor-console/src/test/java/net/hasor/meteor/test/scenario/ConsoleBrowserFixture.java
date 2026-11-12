/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.test.scenario;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.registry.RegistryConfiguration;
import static net.hasor.meteor.test.scenario.ConsoleTestSupport.*;

/** 浏览器连接真实 HTTP、H2 注册中心和 Meteor RPC，界面不注入演示数据。 */
public final class ConsoleBrowserFixture {
    @MetService(group = "commerce", version = "v1")
    public interface OrderService {
        String find(String id);
    }

    @MetService(group = "inventory", version = "v1")
    public interface InventoryService {
        int available(String id);
    }

    public static void main(String[] args) throws Exception {
        Path fixtureDirectory = Files.createTempDirectory(Files.createDirectories(Path.of("build", "console-browser")), "run-");
        var registry = registry("jdbc:h2:mem:console-browser;DB_CLOSE_DELAY=-1");
        registry("jdbc:h2:mem:console-other;DB_CLOSE_DELAY=-1");
        var provider = application(fixtureDirectory.resolve("provider"), registry, "order-app", "east", true);
        provider.connector("http") //
                .bind("127.0.0.1", 18770) //
                .protocol("hprose") //
                .contextPath("/hprose");
        provider.connector("http") //
                .bind("127.0.0.1", 18771) //
                .protocol("hprose") //
                .addressScheme("browser-hprose") //
                .contextPath("/hprose");
        // 默认协议刻意不取第一个 RSF，用于验证治理页面使用提供者的真实上报值。
        provider.setDefaultProtocol("hprose");
        var replica = application(fixtureDirectory.resolve("replica"), registry, "order-app", "west", true);
        // 与 HTTP/管理端口一样固定在浏览器 fixture 范围，避免宿主临时端口段耗尽时无法监听。
        provider.getConnectors().get(0).bind("127.0.0.1", 18769);
        replica.getConnectors().get(0).bind("127.0.0.1", 18772);
        var consumer = application(fixtureDirectory.resolve("consumer"), registry, "storefront", "east", false);
        MetConfiguration offline = new MetConfiguration() //
                .setDataHome(fixtureDirectory.resolve("offline")) //
                .setApplicationName("direct-order-app");
        offline.addExtension(new RegistryConfiguration().setOnline(false).setManagementToken("browser-direct-令牌+secret"));
        offline.connector("tcp") //
                .bind("127.0.0.1", 18767) //
                .protocol("rsf");
        var configDirectory = Files.createDirectories(fixtureDirectory.resolve("console"));
        var config = console(configDirectory, Map.of("hasor.boot.web.connectors.http.port", "18765"));
        var anonymousDirectory = Files.createDirectories(fixtureDirectory.resolve("anonymous"));
        var anonymous = console(anonymousDirectory, Map.of(//
                "hasor.boot.web.connectors.http.port", "18766", //
                "security.enable", "false"));
        var remoteSettings = Files.createDirectories(configDirectory.resolve("remote")).resolve("meteor.properties");
        Files.writeString(remoteSettings, "hasor.boot.web.connectors.http.enabled=false\nrpc.host=127.0.0.1\nrpc.port=18768\nmanagement.token=browser-remote-secret\n" + "storage.type=jdbc\nstorage.database=h2\nstorage.username=sa\n" + "data.directory=" + configDirectory.resolve("remote") + "\n" + "storage.address=jdbc:h2:mem:console-browser;DB_CLOSE_DELAY=-1\n");
        Boot remoteBoot = new Boot()//
                .sources(ConsoleMain.class)//
                .hconfigFile(remoteSettings.toUri().toString());
        Boot consoleBoot = new Boot()//
                .sources(ConsoleMain.class)//
                .hconfigFile(config);
        Boot publicConsoleBoot = new Boot()//
                .sources(ConsoleMain.class)//
                .hconfigFile(anonymous);
        try (BootApplication remote = remoteBoot.start(); //
             MetContext a = provider.buildContext(); //
             MetContext b = replica.buildContext(); //
             MetContext c = consumer.buildContext(); //
             MetContext direct = offline.buildContext(); //
             BootApplication server = consoleBoot.start(); //
             BootApplication publicServer = publicConsoleBoot.start()) {
            a.publisher().rsfService(OrderService.class, id -> {
                if (StringUtils.equals(id, "debug-fail")) throw new IllegalArgumentException("debug-fail: provider rejected request");
                return "order:" + id;
            }).register();
            a.publisher().rsfService(InventoryService.class, id -> 12).register();
            b.publisher().rsfService(OrderService.class, id -> "replica:" + id).register();
            b.publisher().rsfService(InventoryService.class).register();
            c.publisher().rsfService(OrderService.class).register();
            direct.publisher().rsfService(InventoryService.class, id -> 8).register();
            a.start();
            b.start();
            c.start();
            direct.start();
            System.out.println("METEOR_CONSOLE_READY");
            server.join();
        }
    }
}
