/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.blog;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

public final class MultipleProtocolsMain {
    private MultipleProtocolsMain() {
    }

    public static void main(String[] args) throws Exception {
        MetConfiguration serverConfig = new MetConfiguration().setApplicationName("blog-multiple-server");
        var http = serverConfig.connector("http").bind("127.0.0.1", 0);
        http.protocol("jsonrpc").contextPath("/jsonrpc");
        http.protocol("hprose").contextPath("/hprose");

        MetConfiguration clientConfig = new MetConfiguration().setApplicationName("blog-multiple-client");
        var clientHttp = clientConfig.connector("http");
        clientHttp.protocol("jsonrpc").contextPath("/jsonrpc");
        clientHttp.protocol("hprose").contextPath("/hprose");

        try (MetContext server = serverConfig.buildContext(); //
             MetContext client = clientConfig.buildContext()) {
            server.publisher().rsfService(EchoService.class, message -> message) //
                    .protocol("jsonrpc", "hprose").aliasName("jsonrpc", "echo").aliasName("hprose", "echo").register();
            MetBindInfo<EchoService> binding = client.publisher().rsfService(EchoService.class) //
                    .protocol("jsonrpc", "hprose").aliasName("jsonrpc", "echo").aliasName("hprose", "echo").register();
            server.start();
            client.start();

            for (String protocol : new String[] { "jsonrpc", "hprose" }) {
                EchoService echo = client.getRsfClient(server.getListenAddress(protocol)).getRemote(binding);
                System.out.println(protocol + "：" + echo.echo("Meteor"));
            }
        }
    }

    public interface EchoService {
        String echo(String message);
    }
}
