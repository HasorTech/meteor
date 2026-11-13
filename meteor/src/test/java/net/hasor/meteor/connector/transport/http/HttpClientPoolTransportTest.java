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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.junit.jupiter.api.Assertions.*;

/** 验证真实 HTTP 连接的并发上限、复用和独立请求生命周期。 */
class HttpClientPoolTransportTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void http1UsesFourConcurrentReusableConnections() throws Exception {
        // 固定池大小四，服务端先不回复，以便直接观察同一地址上的并行请求。
        Map<String, String> serverOptions = Map.of();
        Map<String, String> clientOptions = Map.of();
        ConnectorConfig serverConfig = config(true, 1, 1, serverOptions);
        ConnectorConfig clientConfig = config(false, 1, 4, clientOptions);
        LinkedBlockingQueue<Arrival> received = new LinkedBlockingQueue<>();
        Set<NetworkChannel<HttpExchange>> sockets = ConcurrentHashMap.newKeySet();
        AtomicInteger disconnected = new AtomicInteger();
        ClassLoader loader = this.getClass().getClassLoader();
        try (NetworkConnector<HttpExchange> server = newHttpConnector(transport(serverConfig), loader); //
             NetworkConnector<HttpExchange> client = newHttpConnector(transport(clientConfig), loader); //
             NetworkListen listen = server.bind(serverConfig.address(), routes(serverConfig), //
                     serverReceiver(received, sockets, disconnected))) {
            BasicFuture<NetworkChannel<HttpExchange>> connected = new BasicFuture<>();
            client.connect(null, listen.getAddress(), routes(clientConfig), clientReceiver(connected)) //
                    .get(5, TimeUnit.SECONDS);
            NetworkChannel<HttpExchange> channel = connected.get(5, TimeUnit.SECONDS);
            List<HttpExchange> requests = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                HttpExchange request = request(i, 1);
                requests.add(request);
                channel.write(request);
            }

            // 四个响应全部保持未完成时，必须已建立四个不同的物理连接。
            List<Arrival> active = new ArrayList<>();
            Set<NetworkChannel<HttpExchange>> activeSockets = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                Arrival arrival = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(arrival);
                active.add(arrival);
                activeSockets.add(arrival.channel);
            }
            assertEquals(4, activeSockets.size());
            assertTrue(requests.stream().noneMatch(request -> request.response().isDone()));
            for (Arrival arrival : active) {
                arrival.reply();
            }

            // 完成全部请求，核对响应归属与连接数上限；后续请求必须复用这四条连接。
            for (int i = 4; i < requests.size(); i++) {
                Arrival arrival = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(arrival);
                arrival.reply();
            }
            for (int i = 0; i < requests.size(); i++) {
                HttpExchange request = requests.get(i);
                HttpResponse response = request.response().get(5, TimeUnit.SECONDS);
                assertArrayEquals(bytes(i), response.body());
                request.completion().get(5, TimeUnit.SECONDS);
            }
            assertEquals(4, sockets.size());
            assertEquals(0, disconnected.get());
            channel.close().get(5, TimeUnit.SECONDS);
            assertFalse(channel.isOpen());
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void httpsPoolKeepsTlsOnEveryConnection() throws Exception {
        // 固定池大小四，服务端先不回复，以便直接观察同一地址上的并行请求。
        Map<String, String> serverOptions = Map.of("tlsEnabled", "true", //
                "tlsKeyStore", "classpath:/tls/acceptance/server.p12", "tlsKeyStorePassword", "meteor-test");
        Map<String, String> clientOptions = Map.of("tlsEnabled", "true", "httpScheme", "https", //
                "tlsTrustStore", "classpath:/tls/acceptance/trust.p12", "tlsTrustStorePassword", "meteor-test");
        ConnectorConfig serverConfig = config(true, 1, 1, serverOptions);
        ConnectorConfig clientConfig = config(false, 1, 4, clientOptions);
        LinkedBlockingQueue<Arrival> received = new LinkedBlockingQueue<>();
        Set<NetworkChannel<HttpExchange>> sockets = ConcurrentHashMap.newKeySet();
        AtomicInteger disconnected = new AtomicInteger();
        ClassLoader loader = this.getClass().getClassLoader();
        try (NetworkConnector<HttpExchange> server = newHttpConnector(transport(serverConfig), loader); //
             NetworkConnector<HttpExchange> client = newHttpConnector(transport(clientConfig), loader); //
             NetworkListen listen = server.bind(serverConfig.address(), routes(serverConfig), //
                     serverReceiver(received, sockets, disconnected))) {
            BasicFuture<NetworkChannel<HttpExchange>> connected = new BasicFuture<>();
            client.connect(null, listen.getAddress(), routes(clientConfig), clientReceiver(connected)) //
                    .get(5, TimeUnit.SECONDS);
            NetworkChannel<HttpExchange> channel = connected.get(5, TimeUnit.SECONDS);
            List<HttpExchange> requests = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                HttpExchange request = request(i, 1);
                requests.add(request);
                channel.write(request);
            }

            // 四个响应全部保持未完成时，必须已建立四个不同的物理连接。
            List<Arrival> active = new ArrayList<>();
            Set<NetworkChannel<HttpExchange>> activeSockets = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                Arrival arrival = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(arrival);
                active.add(arrival);
                activeSockets.add(arrival.channel);
            }
            assertEquals(4, activeSockets.size());
            assertTrue(requests.stream().noneMatch(request -> request.response().isDone()));
            for (Arrival arrival : active) {
                arrival.reply();
            }

            // 完成全部请求，核对响应归属与连接数上限；后续请求必须复用这四条连接。
            for (int i = 4; i < requests.size(); i++) {
                Arrival arrival = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(arrival);
                arrival.reply();
            }
            for (int i = 0; i < requests.size(); i++) {
                HttpExchange request = requests.get(i);
                HttpResponse response = request.response().get(5, TimeUnit.SECONDS);
                assertArrayEquals(bytes(i), response.body());
                request.completion().get(5, TimeUnit.SECONDS);
            }
            assertEquals(4, sockets.size());
            assertEquals(0, disconnected.get());
            channel.close().get(5, TimeUnit.SECONDS);
            assertFalse(channel.isOpen());
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void http2PreservesMultiplexingAndResponseCorrelationAcrossPool() throws Exception {
        // HTTP/2 使用两条连接，每条连接仍可以保持多个未完成 stream。
        ConnectorConfig config = config(true, 2, 2, Map.of());
        LinkedBlockingQueue<Arrival> received = new LinkedBlockingQueue<>();
        Set<NetworkChannel<HttpExchange>> sockets = ConcurrentHashMap.newKeySet();
        ClassLoader loader = this.getClass().getClassLoader();
        try (NetworkConnector<HttpExchange> server = newHttpConnector(transport(config), loader); //
             NetworkConnector<HttpExchange> client = newHttpConnector(transport(config), loader); //
             NetworkListen listen = server.bind(config.address(), routes(config), //
                     serverReceiver(received, sockets, new AtomicInteger()))) {
            BasicFuture<NetworkChannel<HttpExchange>> connected = new BasicFuture<>();
            client.connect(null, listen.getAddress(), routes(config), clientReceiver(connected)) //
                    .get(5, TimeUnit.SECONDS);
            NetworkChannel<HttpExchange> channel = connected.get(5, TimeUnit.SECONDS);
            List<HttpExchange> requests = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                HttpExchange request = request(i, 2);
                requests.add(request);
                channel.write(request).get(5, TimeUnit.SECONDS);
            }
            List<Arrival> arrivals = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                Arrival arrival = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(arrival);
                arrivals.add(arrival);
            }
            assertEquals(2, sockets.size());
            assertTrue(requests.stream().noneMatch(request -> request.response().isDone()));

            // 倒序回复所有 stream，每个响应仍须对应原请求。
            for (int i = arrivals.size() - 1; i >= 0; i--) {
                arrivals.get(i).reply();
            }
            for (int i = 0; i < requests.size(); i++) {
                HttpResponse response = requests.get(i).response().get(5, TimeUnit.SECONDS);
                assertArrayEquals(bytes(i), response.body());
            }
            channel.close().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void cancelledHttp1RequestDoesNotAbortOtherConnections() throws Exception {
        // 填满四条连接的活动请求，保持响应未完成。
        ConnectorConfig config = config(true, 1, 4, Map.of());
        LinkedBlockingQueue<Arrival> received = new LinkedBlockingQueue<>();
        ClassLoader loader = this.getClass().getClassLoader();
        try (NetworkConnector<HttpExchange> server = newHttpConnector(transport(config), loader); //
             NetworkConnector<HttpExchange> client = newHttpConnector(transport(config), loader); //
             NetworkListen listen = server.bind(config.address(), routes(config), //
                     serverReceiver(received, ConcurrentHashMap.newKeySet(), new AtomicInteger()))) {
            BasicFuture<NetworkChannel<HttpExchange>> connected = new BasicFuture<>();
            client.connect(null, listen.getAddress(), routes(config), clientReceiver(connected)) //
                    .get(5, TimeUnit.SECONDS);
            NetworkChannel<HttpExchange> channel = connected.get(5, TimeUnit.SECONDS);
            List<HttpExchange> requests = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                HttpExchange request = request(i, 1);
                requests.add(request);
                channel.write(request);
            }
            List<Arrival> active = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Arrival arrival = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(arrival);
                active.add(arrival);
            }

            // 取消其中一个交换，其他连接的活动请求仍正常完成。
            int cancelled = active.get(0).index();
            assertTrue(requests.get(cancelled).completion().cancel());
            for (int i = 1; i < active.size(); i++) {
                Arrival arrival = active.get(i);
                arrival.reply();
                HttpExchange request = requests.get(arrival.index());
                assertArrayEquals(bytes(arrival.index()), request.response().get(5, TimeUnit.SECONDS).body());
                request.completion().get(5, TimeUnit.SECONDS);
            }
            assertTrue(channel.isOpen());
            channel.close().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsZeroCapacityBeforeCreatingTransport() {
        // 无效容量在准备端点时失败，不启动网络运行时。
        ConnectorConfig config = config(false, 1, 0, Map.of());
        ClassLoader loader = this.getClass().getClassLoader();
        HttpTransportFactory factory = new HttpTransportFactory();
        TransportConfig transport = transport(config);
        assertThrows(IllegalArgumentException.class, () -> factory.prepare(transport, loader));
    }

    private static ConnectorConfig config(boolean bind, int version, int size, Map<String, String> extra) {
        Map<String, String> options = new HashMap<>(extra);
        options.put("httpVersion", String.valueOf(version));
        options.put("clientPoolSize", String.valueOf(size));
        InterAddress address = InterAddress.forBinding("http", "127.0.0.1", 0);
        ProtocolConfig protocol = ProtocolConfig.newConfig("http", "http", Map.of("contextPath", "/"));
        return new ConnectorConfig("http", address, options, List.of(protocol), bind);
    }

    private static byte[] bytes(int index) {
        return String.valueOf(index).getBytes(StandardCharsets.UTF_8);
    }

    private static HttpExchange request(int index, int version) {
        HttpRequest request = HttpRequest.newBuffered("POST", "/" + index, Map.of(), bytes(index), version, Map.of());
        return new HttpExchange(request);
    }

    private static ChannelListenerFactory<HttpExchange> clientReceiver(//
            BasicFuture<NetworkChannel<HttpExchange>> ready) {
        return channel -> new ChannelListener<>() {
            public void connected() {
                ready.completed(channel);
            }

            public void receive(HttpExchange exchange) {
            }

            public void closed(Throwable cause) {
                ready.failed(cause);
            }
        };
    }

    private static ChannelListenerFactory<HttpExchange> serverReceiver(LinkedBlockingQueue<Arrival> received, //
            Set<NetworkChannel<HttpExchange>> sockets, AtomicInteger disconnected) {
        return channel -> new ChannelListener<>() {
            public void connected() {
                sockets.add(channel);
            }

            public void receive(HttpExchange exchange) {
                received.add(new Arrival(channel, exchange));
            }

            public void closed(Throwable cause) {
                disconnected.incrementAndGet();
            }
        };
    }

    private static final class Arrival {
        private final NetworkChannel<HttpExchange> channel;
        private final HttpExchange                 exchange;

        private Arrival(NetworkChannel<HttpExchange> channel, HttpExchange exchange) {
            this.channel = channel;
            this.exchange = exchange;
        }

        private int index() {
            return Integer.parseInt(this.exchange.request().uri().substring(1));
        }

        private void reply() throws Exception {
            this.exchange.respond(HttpResponse.newBuffered(200, Map.of(), this.exchange.request().body(), Map.of()));
            this.channel.write(this.exchange).get(5, TimeUnit.SECONDS);
        }
    }
}
