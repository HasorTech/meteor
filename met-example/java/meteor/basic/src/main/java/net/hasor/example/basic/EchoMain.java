/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.basic;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

public final class EchoMain {
    private EchoMain() {
    }

    public static void main(String[] args) throws Exception {
        MetConfiguration serverConfig = new MetConfiguration().setApplicationName("basic-echo-server");
        serverConfig.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        MetConfiguration clientConfig = new MetConfiguration().setApplicationName("basic-echo-client");
        clientConfig.connector("tcp").protocol("rsf");

        try (MetContext server = serverConfig.buildContext(); //
             MetContext client = clientConfig.buildContext()) {
            server.publisher().rsfService(EchoService.class, message -> "Hello, " + message + "!").register();
            MetBindInfo<EchoService> service = client.publisher().rsfService(EchoService.class).register();
            server.start();
            client.start();

            EchoService echo = client.getRsfClient(server.getListenAddress("rsf")).getRemote(service);
            System.out.println(echo.echo("Meteor"));
        }
    }
}
