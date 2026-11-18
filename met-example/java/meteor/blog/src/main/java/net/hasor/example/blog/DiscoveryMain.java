/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.blog;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;

public final class DiscoveryMain {
    private DiscoveryMain() {
    }

    public static void main(String[] args) throws Exception {
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        MetConfiguration serverConfig = new MetConfiguration().setApplicationName("blog-discovery-server");
        serverConfig.connector("http").bind("127.0.0.1", 0).protocol("jsonrpc").contextPath("/jsonrpc");
        serverConfig.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        serverConfig.addExtension(registry);

        MetConfiguration clientConfig = new MetConfiguration().setApplicationName("blog-discovery-client");
        clientConfig.connector("http").protocol("jsonrpc").contextPath("/jsonrpc");
        clientConfig.connector("tcp").protocol("rsf");
        clientConfig.addExtension(registry);

        try (MetContext server = serverConfig.buildContext(); //
             MetContext client = clientConfig.buildContext()) {
            server.publisher().rsfService(EchoService.class, message -> "Hello, " + message + "!") //
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo").register();
            MetBindInfo<EchoService> binding = client.publisher().rsfService(EchoService.class) //
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo").register();

            server.start();
            client.start();

            EchoService direct = client.getRsfClient(server.getListenAddress("jsonrpc")).getRemote(binding);
            System.out.println("直连：" + direct.echo("Meteor"));

            // 主动等待发现同步完成，后续调用使用注册中心提供的业务地址。
            client.getExtension(RegistryClient.class).pullSync(binding.getBindID()).get(5, TimeUnit.SECONDS);
            EchoService discovered = client.getRsfClient().getRemote(binding);
            System.out.println("服务发现：" + discovered.echo("Meteor"));
        }
    }

    public interface EchoService {
        String echo(String message);
    }
}
