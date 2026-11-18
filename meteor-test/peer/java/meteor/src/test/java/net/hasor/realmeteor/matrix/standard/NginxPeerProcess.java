/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.realmeteor.matrix.standard;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.realmeteor.environment.container.ContainerProcess;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 每个矩阵单元只拥有一个 Nginx 进程，借用 Compose 的独立代理容器。 */
final class NginxPeerProcess implements AutoCloseable {
    private final Path    directory;
    private final Process process;

    private NginxPeerProcess(Path directory, Process process) {
        this.directory = directory;
        this.process = process;
    }

    static NginxPeerProcess start(Path directory, URI upstream, String protocol, String wire, String path) throws Exception {
        Files.createDirectories(directory);
        boolean udp = wire.contains("-udp-");
        boolean http = !List.of("rsf", "dubbo", "thrift", "oncrpc").contains(protocol);
        boolean terminate = StringUtils.equals(path, "nginx-tls-edge") || StringUtils.equals(path, "nginx-tls-bridge");
        boolean encryptUpstream = StringUtils.equals(path, "nginx-tls-bridge");
        Path certificates = Path.of("resource/certs/acceptance").toAbsolutePath();
        String identity = "ssl_certificate " + certificates.resolve("server-chain.pem") + ";\n" + "ssl_certificate_key " + certificates.resolve("server-key.pem") + ";\n" + "ssl_protocols TLSv1.2 TLSv1.3;\n";
        String authority = upstream.getHost() + ":" + upstream.getPort();
        String listen = "listen @PORT@" + (udp ? " udp" : terminate ? " ssl" : "") + ";\n";
        String configuration = "daemon off; master_process off; error_log stderr info; pid nginx.pid;\nevents { worker_connections 64; }\n";
        if (http && (terminate || StringUtils.equals(path, "nginx-clear"))) {
            boolean h2 = List.of("grpc", "triple").contains(protocol);
            String directive = h2 ? "grpc" : "proxy";
            String scheme = h2 ? (encryptUpstream ? "grpcs" : "grpc") : (encryptUpstream ? "https" : "http");
            String location = directive + "_pass " + scheme + "://" + authority + ";\n";
            if (!h2) {
                location += "proxy_http_version 1.1; proxy_set_header Connection \"\";\n";
            }
            if (encryptUpstream) {
                location += directive + "_ssl_verify on;\n" + directive + "_ssl_trusted_certificate " + certificates.resolve("ca.pem") + ";\n" + directive + "_ssl_server_name on;\n" + directive + "_ssl_name " + upstream.getHost() + ";\n";
            }
            configuration += "http { access_log access.log; client_body_temp_path body; proxy_temp_path proxy; server {\n" + listen + (h2 ? "http2 on;\n" : "") + (terminate ? identity : "") + "location / { " + location + " } } }\n";
        } else {
            String tls = terminate ? identity : "";
            if (encryptUpstream) {
                tls += "proxy_ssl on; proxy_ssl_verify on; proxy_ssl_trusted_certificate " + certificates.resolve("ca.pem") + ";\n" + "proxy_ssl_server_name on; proxy_ssl_name " + upstream.getHost() + ";\n";
            }
            configuration += "stream { log_format traffic '$bytes_received $bytes_sent $upstream_addr'; access_log access.log traffic; server {\n" + listen + tls + "proxy_connect_timeout 5s; proxy_timeout 10s; proxy_pass " + authority + "; } }\n";
            // UDP 探测不能向 DTLS 通道注入任意数据；TCP 同端口仅用于进程就绪检查。
            if (udp) {
                configuration += "http { server { listen @PORT@; location / { return 204; } } }\n";
            }
        }
        Files.writeString(directory.resolve("nginx.template"), configuration);
        ProcessBuilder command = new ProcessBuilder("python3", "tools/nginx/launch.py", directory.toString()).redirectOutput(directory.resolve("stdout.log").toFile()).redirectError(directory.resolve("stderr.log").toFile());
        return new NginxPeerProcess(directory, ContainerProcess.start("nginx", command));
    }

    URI target(URI upstream, boolean encrypted) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        Path portFile = this.directory.resolve("port");
        while (!Files.exists(portFile)) {
            assertTrue(this.process.isAlive(), () -> "Nginx startup failed: " + this.directory);
            if (System.nanoTime() >= deadline) {
                throw new IOException("Nginx startup timed out: " + this.directory);
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        int port = Integer.parseInt(Files.readString(portFile));
        String scheme = upstream.getScheme();
        if (StringUtils.equals(scheme, "http") && encrypted) {
            scheme = "https";
        }
        return new URI(scheme, null, ContainerProcess.host(this.process), port, upstream.getPath(), upstream.getQuery(), null);
    }

    void assertForwarded() throws Exception {
        assertTrue(this.process.isAlive(), "Nginx exited before the consumer completed");
        // 成功的业务结果还须有代理流量证据，不能误连提供者后仍报告代理场景通过。
        Path access = this.directory.resolve("access.log");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (Files.exists(access) && Files.readString(access).lines().anyMatch(line -> !line.startsWith("0 "))) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        fail("Nginx recorded no forwarded traffic: " + this.directory);
    }

    @Override
    public void close() throws Exception {
        this.process.destroy();
        if (!this.process.waitFor(10, TimeUnit.SECONDS)) {
            this.process.destroyForcibly();
            assertTrue(this.process.waitFor(5, TimeUnit.SECONDS), "Nginx did not terminate");
        }
        // 保留配置和访问日志，进程清理证据由 ContainerProcess 统一记录。
    }
}
