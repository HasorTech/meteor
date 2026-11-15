/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector;
import java.util.Map;
import net.hasor.cobble.concurrent.future.Future;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.domain.ProtocolConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 协议地址转换保留底层通道身份，并跟随底层地址变化。 */
public class ProtocolNetworkChannelTest {
    @Test
    public void remoteAddressPreservesIpv6PortAndRawPath() throws Exception {
        // 底层 HTTP 地址只替换协议头，保留 IPv6、端口和转义后的完整路径。
        MutableChannel network = new MutableChannel();
        network.remote = new InterAddress("http://[::1]:19209/rpc/a%2Fb");
        ProtocolNetworkChannel<byte[]> channel = protocol(network);
        assertEquals("triple://[::1]:19209/rpc/a%2Fb", channel.getRemote().toHostSchema());
        assertEquals("http://[::1]:19209/rpc/a%2Fb", network.remote.toHostSchema());
    }

    @Test
    public void remoteAddressFollowsReplacement() throws Exception {
        // 相同主机的重连也可能更换端口或路径，不能沿用上一次的转换结果。
        MutableChannel network = new MutableChannel();
        network.remote = new InterAddress("http://127.0.0.1:19209/first");
        ProtocolNetworkChannel<byte[]> channel = protocol(network);
        assertEquals("triple://127.0.0.1:19209/first", channel.getRemote().toHostSchema());
        network.remote = new InterAddress("http://127.0.0.1:19210/second");
        assertEquals("triple://127.0.0.1:19210/second", channel.getRemote().toHostSchema());
    }

    @Test
    public void remoteAddressCanBecomeUnavailableAndRecover() throws Exception {
        // 底层撤去地址后立即返回空，重新连接后使用新的地址。
        MutableChannel network = new MutableChannel();
        network.remote = new InterAddress("http://127.0.0.1:19209/rpc");
        ProtocolNetworkChannel<byte[]> channel = protocol(network);
        assertNotNull(channel.getRemote());
        network.remote = null;
        assertNull(channel.getRemote());
        network.remote = new InterAddress("http://127.0.0.2:19209/rpc");
        assertEquals("triple://127.0.0.2:19209/rpc", channel.getRemote().toHostSchema());
    }

    @Test
    public void lazyLocalAddressFollowsConnectionChanges() throws Exception {
        // HTTP 客户端的本地地址可能在首个请求后才出现，重连后还会再次改变。
        MutableChannel network = new MutableChannel();
        ProtocolNetworkChannel<byte[]> channel = protocol(network);
        assertNull(channel.getLocal());
        network.local = new InterAddress("http://[::1]:21000");
        assertEquals("triple://[::1]:21000", channel.getLocal().toHostSchema());
        network.local = new InterAddress("http://[::1]:21001");
        assertEquals("triple://[::1]:21001", channel.getLocal().toHostSchema());
    }

    private static ProtocolNetworkChannel<byte[]> protocol(MutableChannel network) {
        return new ProtocolNetworkChannel<>(network, ProtocolConfig.newConfig("triple", "triple", Map.of()), true, false);
    }

    private static final class MutableChannel implements NetworkChannel<byte[]> {
        private InterAddress local;
        private InterAddress remote;

        @Override
        public InterAddress getLocal() {
            return this.local;
        }

        @Override
        public InterAddress getRemote() {
            return this.remote;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public Future<Void> write(byte[] message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public Future<Void> close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> drainAndClose() {
            throw new UnsupportedOperationException();
        }
    }
}
