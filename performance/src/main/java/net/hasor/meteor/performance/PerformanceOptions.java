package net.hasor.meteor.performance;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.hasor.cobble.StringUtils;

final class PerformanceOptions {
    static final         Set<String> PROTOCOLS = Set.of(//
            "rsf", "dubbo", "grpc", "grpc-web", "connect", "triple", //
            "hessian", "hprose", "jsonrpc", "xmlrpc", "soap", //
            "thrift", "thrift-http", "oncrpc", "oncrpc-udp");
    private static final Set<String> KEYS      = Set.of(//
            "protocol", "host", "port", "address", "concurrency", "payload", //
            "warmup", "duration", "timeout", "client-pool-size", "http-version", "maximum-requests", //
            "work-dir", "ready-file", "output", "tcp-no-delay", "transport-workers", "task-workers", "business-threads");
    final                String      command;
    final                String      protocol;
    final                String      transport;
    final                int         httpVersion;
    final                String      host;
    final                int         port;
    final                String      address;
    final                int         concurrency;
    final                int         clientPoolSize;
    final                int         maximumRequests;
    final                Boolean     tcpNoDelay;
    final                int         transportWorkers;
    final                int         taskWorkers;
    final                int         businessThreads;
    final                int         payloadBytes;
    final                long        warmupNanos;
    final                long        durationNanos;
    final                int         timeoutMillis;
    final                Path        workDirectory;
    final                Path        readyFile;
    final                Path        output;

    private PerformanceOptions(String command, Map<String, String> values) {
        this.command = command;
        this.protocol = values.getOrDefault("protocol", "rsf");
        if (!PROTOCOLS.contains(this.protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + this.protocol);
        }
        this.transport = switch (this.protocol) {
            case "rsf", "dubbo", "thrift", "oncrpc" -> "tcp";
            case "oncrpc-udp" -> "udp";
            default -> "http";
        };
        boolean requiresHttp2 = Set.of("grpc", "triple").contains(this.protocol);
        int defaultVersion = requiresHttp2 ? 2 : 1;
        this.httpVersion = integer(values, "http-version", defaultVersion, 1, 2);
        if (values.containsKey("http-version") && !StringUtils.equals(this.transport, "http")) {
            throw new IllegalArgumentException("--http-version applies to HTTP transports only");
        }
        if (requiresHttp2 && this.httpVersion != 2) {
            throw new IllegalArgumentException(this.protocol + " default framing requires --http-version 2");
        }
        this.host = values.getOrDefault("host", "127.0.0.1");
        this.port = integer(values, "port", 0, 0, 65535);
        this.concurrency = integer(values, "concurrency", 16, 1, 1024);
        this.clientPoolSize = integer(values, "client-pool-size", 1, 1, 1024);
        if (StringUtils.equals(this.transport, "udp") && this.clientPoolSize != 1) {
            throw new IllegalArgumentException("--client-pool-size applies to TCP and HTTP transports only");
        }
        this.maximumRequests = values.containsKey("maximum-requests") ? integer(values, "maximum-requests", 1, 1, 65536) : 0;
        this.tcpNoDelay = optionalBoolean(values, "tcp-no-delay");
        if (this.tcpNoDelay != null && StringUtils.equals(this.transport, "udp")) {
            throw new IllegalArgumentException("--tcp-no-delay applies to TCP and HTTP transports only");
        }
        this.transportWorkers = optionalThreads(values, "transport-workers");
        this.taskWorkers = optionalThreads(values, "task-workers");
        this.businessThreads = optionalThreads(values, "business-threads");
        this.payloadBytes = integer(values, "payload", 1024, 0, 16 * 1024 * 1024);
        this.timeoutMillis = integer(values, "timeout", 3000, 1, 60000);
        this.warmupNanos = seconds(values, "warmup", 5, true);
        this.durationNanos = seconds(values, "duration", 30, false);
        this.address = values.get("address");
        if (StringUtils.equals(command, "client")) {
            if (this.address == null)
                throw new IllegalArgumentException("client requires --address");
            URI uri = URI.create(this.address);
            if (!StringUtils.equals(uri.getScheme(), this.protocol) || uri.getHost() == null || uri.getPort() <= 0) {
                throw new IllegalArgumentException("--address must contain the selected protocol, host and port");
            }
        }
        this.workDirectory = Path.of(values.getOrDefault("work-dir", "build/work/" + command)).toAbsolutePath();
        this.readyFile = values.containsKey("ready-file") ? Path.of(values.get("ready-file")).toAbsolutePath() : null;
        String defaultOutput = this.workDirectory.resolve("result.json").toString();
        this.output = Path.of(values.getOrDefault("output", defaultOutput)).toAbsolutePath();
    }

    static PerformanceOptions parse(String[] arguments) {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("First argument must be server or client");
        }
        String command = arguments[0];
        if (!StringUtils.equals(command, "server") && !StringUtils.equals(command, "client")) {
            throw new IllegalArgumentException("First argument must be server or client");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < arguments.length; i += 2) {
            String key = arguments[i];
            if (!key.startsWith("--") || !KEYS.contains(key.substring(2)) || i + 1 >= arguments.length) {
                throw new IllegalArgumentException("Unknown option or missing value: " + key);
            }
            if (values.putIfAbsent(key.substring(2), arguments[i + 1]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + key);
            }
        }
        return new PerformanceOptions(arguments[0], values);
    }

    private static Boolean optionalBoolean(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            return null;
        }
        if (StringUtils.equalsIgnoreCase(value, "true")) {
            return Boolean.TRUE;
        }
        if (StringUtils.equalsIgnoreCase(value, "false")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid --" + key + ": " + value);
    }

    private static int optionalThreads(Map<String, String> values, String key) {
        return values.containsKey(key) ? integer(values, key, 1, 1, 256) : 0;
    }

    private static int integer(Map<String, String> values, String key, int fallback, int minimum, int maximum) {
        int value = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException("Invalid --" + key + ": " + value);
        return value;
    }

    private static long seconds(Map<String, String> values, String key, double fallback, boolean allowZero) {
        double value = Double.parseDouble(values.getOrDefault(key, Double.toString(fallback)));
        if (!Double.isFinite(value) || value > 3600 || (allowZero ? value < 0 : value < 0.001)) {
            throw new IllegalArgumentException("Invalid --" + key + ": " + value);
        }
        return (long) (value * 1_000_000_000L);
    }
}
