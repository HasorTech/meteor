package net.hasor.meteor.performance;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.connector.ProtocolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceOptionsTest {
    @ParameterizedTest
    @ValueSource(strings = { "rsf", "dubbo", "grpc", "grpc-web", "connect", "triple", "hessian", "hprose", //
            "jsonrpc", "xmlrpc", "soap", "thrift", "thrift-http", "oncrpc", "oncrpc-udp" })
    void acceptsEverySupportedProtocol(String protocol) {
        // 每个正式 RPC 入口均可独立启动压测，不依赖测试工程或示例工程。
        String[] arguments = { "server", "--protocol", protocol };
        PerformanceOptions options = PerformanceOptions.parse(arguments);
        ProtocolProvider<?> provider = ServiceLoader.load(ProtocolProvider.class).stream() //
                .map(ServiceLoader.Provider::get) //
                .filter(candidate -> StringUtils.equals(protocol, candidate.name())) //
                .findFirst().orElseThrow();
        assertEquals(protocol, options.protocol);
        assertEquals(provider.transportType(), options.transport);
    }

    @Test
    void catalogCoversEveryInstalledRpcProvider() {
        // 生产 SPI 新增入口时，压测清单不能静默漏测。
        Set<String> installed = ServiceLoader.load(ProtocolProvider.class).stream() //
                .map(ServiceLoader.Provider::get) //
                .map(ProtocolProvider::name).collect(Collectors.toSet());
        assertEquals(installed, PerformanceOptions.PROTOCOLS);
    }

    @Test
    void selectsUdpForOncRpcDatagrams() {
        // UDP 入口不能误用 HTTP 或 TCP 连接器。
        String[] arguments = { "server", "--protocol", "oncrpc-udp" };
        assertEquals("udp", PerformanceOptions.parse(arguments).transport);
    }

    @Test
    void acceptsHttp2ForAnOrdinaryHttpProtocol() {
        // 相同 RPC 编码可显式比较 HTTP/1 与 HTTP/2。
        String[] arguments = { "server", "--protocol", "hessian", "--http-version", "2" };
        assertEquals(2, PerformanceOptions.parse(arguments).httpVersion);
    }

    @Test
    void rejectsHttp1ForGrpc() {
        // gRPC 不把不兼容版本静默替换为 HTTP/2。
        String[] arguments = { "server", "--protocol", "grpc", "--http-version", "1" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void defaultsTripleToHttp2Framing() {
        // 默认 Hessian2 wrapper 使用 gRPC framing，不能套用普通 HTTP/1 默认值。
        String[] arguments = { "server", "--protocol", "triple" };
        assertEquals(2, PerformanceOptions.parse(arguments).httpVersion);
    }

    @Test
    void rejectsHttpVersionForTcp() {
        // TCP 配置不接收 HTTP 专属参数。
        String[] arguments = { "server", "--protocol", "dubbo", "--http-version", "2" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void acceptsHttpClientPoolSize() {
        // HTTP 压测允许明确配置每个目标的连接数。
        String[] arguments = { "server", "--protocol", "hessian", "--client-pool-size", "8" };
        assertEquals(8, PerformanceOptions.parse(arguments).clientPoolSize);
    }

    @Test
    void rejectsZeroClientPoolSize() {
        // 零容量不能作为启用池的有效配置。
        String[] arguments = { "server", "--protocol", "hessian", "--client-pool-size", "0" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void acceptsTcpClientPoolSize() {
        // 原生 TCP 复用同一连接池配置，保持可选容量显式可见。
        String[] arguments = { "server", "--protocol", "rsf", "--client-pool-size", "8" };
        assertEquals(8, PerformanceOptions.parse(arguments).clientPoolSize);
    }

    @Test
    void rejectsClientPoolForUdp() {
        // UDP 不承诺有连接池，避免静默接受无效参数。
        String[] arguments = { "server", "--protocol", "oncrpc-udp", "--client-pool-size", "8" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void leavesWorkerDefaultsToProductConfiguration() {
        // 未指定时使用生产默认配置；压测不能悄悄改线程规模。
        PerformanceOptions options = PerformanceOptions.parse(new String[] { "server" });
        assertEquals(0, options.transportWorkers);
        assertEquals(0, options.taskWorkers);
        assertEquals(0, options.businessThreads);
    }

    @ParameterizedTest
    @ValueSource(strings = { "transport-workers", "task-workers", "business-threads" })
    void validatesExplicitWorkerCounts(String option) {
        // 显式线程数必须有界且非零，同时允许独立调整各执行层。
        PerformanceOptions valid = PerformanceOptions.parse(new String[] { "server", "--" + option, "8" });
        assertEquals(8, valid.transportWorkers + valid.taskWorkers + valid.businessThreads);
        for (String invalid : new String[] { "0", "-1", "257" }) {
            String[] arguments = { "server", "--" + option, invalid };
            assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
        }
    }

    @Test
    void leavesRequestAdmissionLimitToProductByDefault() {
        // 不指定准入上限时，沿用框架配置，避免压测悄悄放宽默认限制。
        assertEquals(0, PerformanceOptions.parse(new String[] { "server" }).maximumRequests);
    }

    @Test
    void acceptsExplicitRequestAdmissionLimit() {
        // 提高并发时可以单独控制准入，区分连接池、负载并发与在途请求上限。
        String[] arguments = { "server", "--maximum-requests", "1024" };
        assertEquals(1024, PerformanceOptions.parse(arguments).maximumRequests);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, 65537 })
    void rejectsInvalidRequestAdmissionLimits(int limit) {
        // 显式上限必须有界且为正数。
        String[] arguments = { "server", "--maximum-requests", Integer.toString(limit) };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void preservesAnExplicitTcpNoDelayOverride() {
        // 不传时使用产品默认值，显式false不能被默认true吞掉。
        assertNull(PerformanceOptions.parse(new String[] { "server" }).tcpNoDelay);
        assertEquals(Boolean.FALSE, PerformanceOptions.parse(new String[] { "server", "--tcp-no-delay", "false" }).tcpNoDelay);
        assertEquals(Boolean.TRUE, PerformanceOptions.parse(new String[] { "server", "--tcp-no-delay", "true" }).tcpNoDelay);
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(new String[] { "server", "--tcp-no-delay", "maybe" }));
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(new String[] { "server", "--protocol", "oncrpc-udp", "--tcp-no-delay", "true" }));
    }

    @Test
    void rejectsUnknownOption() {
        // 拼错参数必须明确失败，不能回退到默认值。
        String[] arguments = { "server", "--duraton", "5" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void rejectsNonFiniteDuration() {
        // 非有限时长不能进入负载计时器。
        String[] arguments = { "server", "--duration", "NaN" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void rejectsTargetProtocolMismatch() {
        // 客户端地址的协议必须与已选协议一致。
        String[] arguments = { "client", "--address", "grpc://127.0.0.1:19000" };
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(arguments));
    }

    @Test
    void requiresExplicitClientAddress() {
        assertThrows(IllegalArgumentException.class, () -> PerformanceOptions.parse(new String[] { "client" }));
    }
}
