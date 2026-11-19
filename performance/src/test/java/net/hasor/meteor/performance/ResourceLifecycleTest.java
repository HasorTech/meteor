package net.hasor.meteor.performance;
import java.lang.management.ManagementFactory;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import com.sun.management.UnixOperatingSystemMXBean;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class ResourceLifecycleTest {
    @TempDir
    Path directory;

    static Stream<String> protocols() {
        return PerformanceOptions.PROTOCOLS.stream().sorted();
    }

    @ParameterizedTest
    @MethodSource("protocols")
    @Timeout(60)
    void closingContextsReleasesConnectionsAndOwnedThreads(String protocol) throws Exception {
        // 先初始化协议 SPI、编码器及类路径资源，避免把一次性类加载描述符当作连接泄漏。
        this.exercise(protocol, 0);
        long descriptors = this.descriptors();
        Set<Thread> existing = Thread.getAllStackTraces().keySet();
        for (int cycle = 1; cycle <= 2; cycle++) {
            this.exercise(protocol, cycle);
        }

        // 只检查本场景新增且属于网络、连接器或业务执行器的线程，不终止借用的公共线程。
        List<Thread> created = new ArrayList<>(Thread.getAllStackTraces().keySet());
        created.removeAll(existing);
        for (Thread thread : created) {
            if (ownedThread(thread)) {
                thread.join(TimeUnit.SECONDS.toMillis(3));
                assertFalse(thread.isAlive(), thread.getName());
            }
        }
        if (descriptors >= 0) {
            long remaining = this.descriptors();
            assertTrue(remaining <= descriptors + 1, "Context close retained descriptors: " + descriptors + " -> " + remaining);
        }
    }

    private void exercise(String protocol, int cycle) throws Exception {
        Path root = this.directory.resolve(protocol + "-" + cycle);
        MetConfiguration server = this.configuration("server", protocol, root.resolve("server"));
        MetConfiguration client = this.configuration("client", protocol, root.resolve("client"));
        try (MetContext provider = server.buildContext(); MetContext consumer = client.buildContext()) {
            provider.publisher().rsfService(EchoService.class, payload -> payload).protocol(protocol).register();
            var binding = consumer.publisher().rsfService(EchoService.class).protocol(protocol).register();
            provider.start();
            consumer.start();
            var caller = consumer.getRsfClient(provider.getListenAddress(protocol).toString());
            EchoPayload request = new EchoPayload();
            byte[] expected = new byte[] { 7, 19, 31, 43 };
            request.setData(expected);
            Class<?>[] types = { EchoPayload.class };
            Object[] arguments = { request };
            // HTTP 随机池在同一上下文内反复取连接，其余协议复用已建立通道。
            for (int call = 0; call < 64; call++) {
                EchoPayload reply = (EchoPayload) caller.syncInvoke(binding, "echo", types, arguments);
                assertArrayEquals(expected, reply.getData());
            }
        }
    }

    private MetConfiguration configuration(String role, String protocol, Path data) throws Exception {
        String[] arguments = { "server", "--protocol", protocol };
        PerformanceOptions options = PerformanceOptions.parse(arguments);
        MetConfiguration configuration = new MetConfiguration().setApplicationName("lifecycle-" + role) //
                .setDataHome(data).setDefaultProtocol(protocol);
        var connector = configuration.connector(options.transport);
        var mount = connector.protocol(protocol);
        if (StringUtils.equals(options.transport, "http")) {
            connector.option("httpVersion", options.httpVersion).option("clientPoolSize", 8);
            mount.contextPath("/rpc").option("httpVersion", options.httpVersion);
        }
        if (StringUtils.equals(role, "server")) {
            int port = 0;
            if (StringUtils.equals(options.transport, "udp")) {
                try (DatagramSocket available = new DatagramSocket(new InetSocketAddress("::1", 0))) {
                    port = available.getLocalPort();
                }
            }
            connector.bind("::1", port);
        }
        return configuration;
    }

    private long descriptors() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof UnixOperatingSystemMXBean unix ? unix.getOpenFileDescriptorCount() : -1;
    }

    private static boolean ownedThread(Thread thread) {
        String name = thread.getName();
        return name.startsWith("Neta-") || name.startsWith("Meteor(") || //
                name.startsWith("Meteor-Connector-") || name.startsWith("RSF-Biz-");
    }
}
