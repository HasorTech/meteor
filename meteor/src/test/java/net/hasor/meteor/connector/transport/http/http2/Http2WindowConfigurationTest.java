/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http.http2;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.ConnectorConfig;
import net.hasor.meteor.connector.transport.ChannelListener;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.connector.transport.NetworkConnector;
import net.hasor.meteor.connector.transport.NetworkListen;
import net.hasor.meteor.connector.transport.http.channel.HttpExchange;
import net.hasor.meteor.domain.ProtocolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static net.hasor.meteor.connector.HttpTransportPlans.routes;
import static net.hasor.meteor.connector.TransportTestConfigs.transport;
import static net.hasor.meteor.connector.transport.TransportTestServices.newHttpConnector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在真实连接上验证连接器配置最终回吐的 HTTP/2 窗口。 */
class Http2WindowConfigurationTest {
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void defaultConnectionReceiveCreditIsOneMiB() throws Exception {
        assertEquals(1048576, this.advertisedConnectionWindow(Map.of()));
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void connectorCanRetainTheLegacyConnectionWindow() throws Exception {
        Map<String, String> options = Map.of("http2ConnectionWindowSize", "65535");
        assertEquals(65535, this.advertisedConnectionWindow(options));
    }

    private int advertisedConnectionWindow(Map<String, String> options) throws Exception {
        // 正式 HTTP 传输监听器接受一个只发送连接前言和空 SETTINGS 的对端。
        InterAddress address = InterAddress.forBinding("http", "::1", 0);
        ProtocolConfig protocol = ProtocolConfig.newConfig("http", "http", Map.of("contextPath", "/"));
        ConnectorConfig config = new ConnectorConfig("http", address, options, List.of(protocol), true);
        ClassLoader loader = this.getClass().getClassLoader();
        try (NetworkConnector<HttpExchange> server = newHttpConnector(transport(config), loader); //
             NetworkListen listen = server.bind(address, routes(config), this::receiver); //
             Socket peer = new Socket(listen.getAddress().getHost(), listen.getAddress().getPort())) {
            peer.setSoTimeout(3000);
            peer.getOutputStream().write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            peer.getOutputStream().write(new byte[] { 0, 0, 0, 4, 0, 0, 0, 0, 0 });
            peer.getOutputStream().flush();

            // 初始额度来自协议常量；只将 stream 0 的 WINDOW_UPDATE 累加到连接额度。
            int connectionWindow = 65535;
            InputStream input = peer.getInputStream();
            for (int i = 0; i < 32; i++) {
                byte[] header = input.readNBytes(9);
                assertEquals(9, header.length);
                int length = ((header[0] & 0xff) << 16) | ((header[1] & 0xff) << 8) | (header[2] & 0xff);
                assertTrue(length <= 16384);
                byte[] body = input.readNBytes(length);
                assertEquals(length, body.length);
                int stream = ByteBuffer.wrap(header, 5, 4).getInt() & 0x7fffffff;
                if (header[3] == 8 && stream == 0) {
                    assertEquals(4, length);
                    connectionWindow += ByteBuffer.wrap(body).getInt() & 0x7fffffff;
                } else if (header[3] == 4 && header[4] == 0) {
                    ByteBuffer settings = ByteBuffer.wrap(body);
                    while (settings.hasRemaining()) {
                        int key = settings.getShort() & 0xffff;
                        int value = settings.getInt();
                        if (key == 4) {
                            assertEquals(65535, value, "单流窗口不能随连接总额度扩大");
                        }
                    }
                } else if (header[3] == 4 && header[4] == 1) {
                    return connectionWindow;
                }
            }
            throw new AssertionError("HTTP/2 peer did not acknowledge SETTINGS");
        }
    }

    private ChannelListener<HttpExchange> receiver(NetworkChannel<HttpExchange> channel) {
        return new ChannelListener<>() {
            @Override
            public void connected() {
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
