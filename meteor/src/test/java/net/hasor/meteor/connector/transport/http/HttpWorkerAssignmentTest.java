/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.concurrent.future.BasicFuture;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.ConnectorConfig;
import net.hasor.meteor.connector.transport.*;
import net.hasor.meteor.connector.transport.http.channel.HttpExchange;
import net.hasor.meteor.connector.transport.http.channel.HttpRequest;
import net.hasor.meteor.connector.transport.http.channel.HttpResponse;
import net.hasor.meteor.domain.ProtocolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static net.hasor.meteor.connector.HttpTransportPlans.routes;
import static net.hasor.meteor.connector.TransportTestConfigs.transport;
import static net.hasor.meteor.connector.transport.TransportTestServices.newHttpConnector;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 同一端口的版本探测不能令同版本连接只占用一半顺序工作线程。 */
class HttpWorkerAssignmentTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void http1ConnectionsUseAllOrderedWorkers() throws Exception {
        this.verifyOrderedWorkers(1);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void http2ConnectionsUseAllOrderedWorkers() throws Exception {
        this.verifyOrderedWorkers(2);
    }

    private void verifyOrderedWorkers(int version) throws Exception {
        // 四个工作线程与四条物理连接，通过真实响应观察处理请求的线程。
        InterAddress address = InterAddress.forBinding("http", "::1", 0);
        Map<String, String> options = Map.of("workerThread", "4", "httpVersion", Integer.toString(version));
        ProtocolConfig protocol = ProtocolConfig.newConfig("http", "http", Map.of("contextPath", "/"));
        ConnectorConfig config = new ConnectorConfig("http", address, options, List.of(protocol), true);
        ClassLoader loader = this.getClass().getClassLoader();
        try (NetworkConnector<HttpExchange> server = newHttpConnector(transport(config), loader); //
             NetworkConnector<HttpExchange> client = newHttpConnector(transport(config), loader); //
             NetworkListen listen = server.bind(address, routes(config), this::respondWithWorker)) {
            List<NetworkChannel<HttpExchange>> channels = new ArrayList<>();
            Set<String> workers = new HashSet<>();
            Map<NetworkChannel<HttpExchange>, String> assignments = new IdentityHashMap<>();
            for (int i = 0; i < 4; i++) {
                BasicFuture<NetworkChannel<HttpExchange>> connected = new BasicFuture<>();
                client.connect(null, listen.getAddress(), routes(config), this.captureClient(connected)) //
                        .get(5, TimeUnit.SECONDS);
                NetworkChannel<HttpExchange> channel = connected.get(5, TimeUnit.SECONDS);
                channels.add(channel);
                String worker = this.requestWorker(channel, version);
                workers.add(worker);
                assignments.put(channel, worker);
            }
            assertEquals(4, workers.size());

            // 复用连接时仍由初次分配的线程交付响应。
            Set<String> reusedWorkers = new HashSet<>();
            for (NetworkChannel<HttpExchange> channel : channels) {
                String first = this.requestWorker(channel, version);
                assertEquals(assignments.get(channel), first);
                assertEquals(first, this.requestWorker(channel, version));
                reusedWorkers.add(first);
            }
            assertEquals(workers, reusedWorkers);
        }
    }

    private String requestWorker(NetworkChannel<HttpExchange> channel, int version) throws Exception {
        HttpRequest request = HttpRequest.newBuffered("GET", "/worker", Map.of(), new byte[0], version, Map.of());
        HttpExchange exchange = new HttpExchange(request);
        channel.write(exchange).get(5, TimeUnit.SECONDS);
        HttpResponse response = exchange.response().get(5, TimeUnit.SECONDS);
        assertEquals(200, response.status());
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private ChannelListener<HttpExchange> respondWithWorker(NetworkChannel<HttpExchange> channel) {
        return new ChannelListener<>() {
            @Override
            public void connected() {
            }

            @Override
            public void receive(HttpExchange exchange) {
                byte[] worker = Thread.currentThread().getName().getBytes(StandardCharsets.UTF_8);
                exchange.respond(HttpResponse.newBuffered(200, Map.of(), worker, Map.of()));
                channel.write(exchange);
            }

            @Override
            public void closed(Throwable cause) {
            }
        };
    }

    private ChannelListenerFactory<HttpExchange> captureClient(BasicFuture<NetworkChannel<HttpExchange>> connected) {
        return channel -> new ChannelListener<>() {
            @Override
            public void connected() {
                connected.completed(channel);
            }

            @Override
            public void receive(HttpExchange exchange) {
            }

            @Override
            public void closed(Throwable cause) {
            }
        };
    }
}
