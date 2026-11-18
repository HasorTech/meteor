/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.blog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

public final class HttpPathsMain {
    private HttpPathsMain() {
    }

    public static void main(String[] args) throws Exception {
        MetConfiguration configuration = new MetConfiguration().setApplicationName("blog-http-paths");
        configuration.connector("http").bind("127.0.0.1", 0).protocol("jsonrpc").contextPath("/rpc");

        try (MetContext server = configuration.buildContext()) {
            server.publisher().rsfService(EchoService.class, message -> message) //
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo") //
                    .contextPathByPrefix("jsonrpc", "/public").register();
            server.start();

            int port = server.getListenAddress("jsonrpc").getPort();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/rpc/public")) //
                    .timeout(Duration.ofSeconds(5)) //
                    .header("Content-Type", "application/json") //
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"jsonrpc":"2.0","id":1,"method":"echo.echo","params":["Meteor"]}
                            """)) //
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
            }
            System.out.println(response.body());
        }
    }

    public interface EchoService {
        String echo(String message);
    }
}
