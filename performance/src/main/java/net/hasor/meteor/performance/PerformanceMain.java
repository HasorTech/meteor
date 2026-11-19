package net.hasor.meteor.performance;
import java.lang.management.ManagementFactory;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.logging.LoggerFactory;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.connector.ProtocolProvider;

/** server/client 分别运行在独立 JVM；启动和报告生成均在测量区间之外。 */
public final class PerformanceMain {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private PerformanceMain() {
    }

    public static void main(String[] arguments) {
        int status = 1;
        try {
            PerformanceOptions options = PerformanceOptions.parse(arguments);
            Files.createDirectories(options.workDirectory.resolve("tmp"));
            Files.createDirectories(options.workDirectory.resolve("home"));
            System.setProperty("java.io.tmpdir", options.workDirectory.resolve("tmp").toString());
            System.setProperty("user.home", options.workDirectory.resolve("home").toString());
            LoggerFactory.useJdkLogger();
            Logger.getLogger("").setLevel(Level.WARNING);
            Logger.getLogger("net.hasor.meteor").setLevel(Level.OFF);
            status = StringUtils.equals(options.command, "server") ? server(options) : client(options);
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
        }
        if (status != 0)
            System.exit(status);
    }

    private static MetConfiguration configuration(PerformanceOptions options) throws Exception {
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("meteor-performance-" + options.command) //
                .setDataHome(options.workDirectory.resolve("data")) //
                .setDefaultProtocol(options.protocol) //
                .setDefaultTimeout(options.timeoutMillis).setConnectTimeout(options.timeoutMillis);
        if (options.maximumRequests > 0) {
            configuration.setMaximumRequest(options.maximumRequests);
        }
        var connector = configuration.connector(options.transport);
        if (options.tcpNoDelay != null) {
            connector.option("tcpNoDelay", options.tcpNoDelay);
        }
        if (options.transportWorkers > 0) {
            connector.option("workerThread", options.transportWorkers);
        }
        if (options.taskWorkers > 0) {
            connector.option("taskThread", options.taskWorkers);
        }
        if (options.businessThreads > 0) {
            configuration.setQueueMinPoolSize(options.businessThreads).setQueueMaxPoolSize(options.businessThreads);
        }
        var protocol = connector.protocol(options.protocol);
        if (StringUtils.equals(options.transport, "http")) {
            protocol.contextPath("/rpc").option("httpVersion", options.httpVersion);
            connector.option("httpVersion", options.httpVersion);
        }
        if (StringUtils.equals(options.command, "client") && !StringUtils.equals(options.transport, "udp")) {
            connector.option("clientPoolSize", options.clientPoolSize);
        }
        if (StringUtils.equals(options.command, "server")) {
            int port = options.port;
            if (port == 0 && StringUtils.equals(options.transport, "udp")) {
                // UDP 传输要求明确端口；启动前选择空闲端口，绑定冲突正常报错，不隐式重试。
                try (DatagramSocket available = new DatagramSocket(new InetSocketAddress(options.host, 0))) {
                    port = available.getLocalPort();
                }
            }
            connector.bind(options.host, port);
        }
        return configuration;
    }

    private static int server(PerformanceOptions options) throws Exception {
        Thread owner = Thread.currentThread();
        Thread shutdown = new Thread(() -> {
            owner.interrupt();
            try {
                owner.join(10000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "Meteor-performance-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try (MetContext context = configuration(options).buildContext()) {
            context.publisher().rsfService(EchoService.class, payload -> payload).protocol(options.protocol).register();
            context.start();
            String address = context.getListenAddress(options.protocol).toString();
            Map<String, Object> metadata = environment(options);
            metadata.put("address", address);
            metadata.put("protocol", options.protocol);
            metadata.put("transport", options.transport);
            metadata.put("httpVersion", StringUtils.equals(options.transport, "http") ? options.httpVersion : null);
            write(options.workDirectory.resolve("server.json"), metadata);
            if (options.readyFile != null) {
                Files.createDirectories(options.readyFile.getParent());
                Path pending = options.readyFile.resolveSibling(options.readyFile.getFileName() + ".tmp");
                Files.writeString(pending, address + "\n");
                Files.move(pending, options.readyFile, //
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            System.out.println("PERFORMANCE_READY " + address);
            new CountDownLatch(1).await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException stopping) {
                // JVM 关闭期间，由 hook 等待主线程完成资源释放。
            }
        }
        return 0;
    }

    private static int client(PerformanceOptions options) throws Exception {
        byte[] payload = new byte[options.payloadBytes];
        for (int i = 0; i < payload.length; i++)
            payload[i] = (byte) (i * 31 + 7);
        Map<String, Object> report = environment(options);
        report.put("protocol", options.protocol);
        report.put("transport", options.transport);
        report.put("httpVersion", StringUtils.equals(options.transport, "http") ? options.httpVersion : null);
        report.put("address", options.address);
        report.put("concurrency", options.concurrency);
        report.put("clientPoolSize", options.clientPoolSize);
        report.put("maximumRequestsOverride", options.maximumRequests);
        report.put("tcpNoDelayOverride", options.tcpNoDelay);
        report.put("transportWorkersOverride", options.transportWorkers);
        report.put("taskWorkersOverride", options.taskWorkers);
        report.put("businessThreadsOverride", options.businessThreads);
        report.put("payloadBytes", options.payloadBytes);
        report.put("workload", "EchoPayload containing one binary data field");
        report.put("timeoutMillis", options.timeoutMillis);
        report.put("loadModel", "closed-loop synchronous, one outstanding request per worker");
        report.put("latencyDefinition", "RPC latency excludes validation; no coordinated-omission correction");
        report.put("throughputDefinition", "validated measured completions / elapsed seconds including final drain");
        try (MetContext context = configuration(options).buildContext()) {
            report.put("maximumRequests", context.getSettings().getMaximumRequest());
            var subscription = context.publisher().rsfService(EchoService.class);
            MetBindInfo<EchoService> binding = subscription.protocol(options.protocol).register();
            context.start();
            MetClient client = context.getRsfClient(options.address);
            EchoPayload request = new EchoPayload();
            request.setData(payload);
            Class<?>[] types = { EchoPayload.class };
            Object[] arguments = { request };
            EchoPayload probe = (EchoPayload) client.syncInvoke(binding, "echo", types, arguments);
            if (probe == null || !Arrays.equals(payload, probe.getData()))
                throw new IllegalStateException("Preflight echo validation failed");
            report.putAll(RpcLoad.run(options, payload, () -> {
                EchoPayload response = (EchoPayload) client.syncInvoke(binding, "echo", types, arguments);
                return response == null ? null : response.getData();
            }));
        }
        write(options.output, report);
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        return Boolean.TRUE.equals(report.get("passed")) ? 0 : 1;
    }

    private static Map<String, Object> environment(PerformanceOptions options) {
        Map<String, Object> result = new LinkedHashMap<>();
        ProtocolProvider<?> provider = ServiceLoader.load(ProtocolProvider.class).stream() //
                .map(ServiceLoader.Provider::get) //
                .filter(candidate -> StringUtils.equals(options.protocol, candidate.name())) //
                .findFirst().orElseThrow();
        result.put("protocolDefaultSerialization", provider.defaultSerialize());
        result.put("startedAt", Instant.now().toString());
        result.put("pid", ProcessHandle.current().pid());
        result.put("version", System.getProperty("meteor.performance.version", "unknown"));
        result.put("revision", System.getenv().getOrDefault("METEOR_PERFORMANCE_REVISION", "unknown"));
        result.put("workingTreeDirty", System.getenv().getOrDefault("METEOR_PERFORMANCE_DIRTY", "unknown"));
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("vm", System.getProperty("java.vm.name"));
        result.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        result.put("architecture", System.getProperty("os.arch"));
        result.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        result.put("maximumHeapBytes", Runtime.getRuntime().maxMemory());
        result.put("meteorLogging", "OFF");
        return result;
    }

    private static void write(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }
}
