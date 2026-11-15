/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.connector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 经正式 RSF 调用入口验证多个 TCP 会话的响应归属、上限和复用。 */
public class NativeTcpPoolTest {
    @TempDir
    Path directory;

    public interface Echo {
        String echo(String value);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void boundedTcpMembersServeDistinctConcurrentCallsAndRemainReusable() throws Exception {
        // 两个上下文各自拥有目录，服务端从请求过滤器观察实际 TCP 对端端口。
        MetConfiguration serverConfig = new MetConfiguration().setDataHome(this.directory.resolve("server")) //
                .setDefaultProtocol("rsf").setDefaultTimeout(5000).setConnectTimeout(5000);
        serverConfig.connector("tcp").bind("::1", 0).protocol("rsf");
        MetConfiguration clientConfig = new MetConfiguration().setDataHome(this.directory.resolve("client")) //
                .setDefaultProtocol("rsf").setDefaultTimeout(5000).setConnectTimeout(5000);
        clientConfig.connector("tcp").option("clientPoolSize", 4).protocol("rsf");
        Set<Integer> ports = ConcurrentHashMap.newKeySet();
        try (MetContext server = serverConfig.buildContext(); //
             MetContext client = clientConfig.buildContext()) {
            server.publisher().bindFilter("physical-peers", (request, response, chain) -> {
                if (!request.isLocal() && request.getMethod().getDeclaringClass() == Echo.class) {
                    ports.add(request.getRemoteAddress().getPort());
                }
                chain.doFilter(request, response);
            });
            server.publisher().rsfService(Echo.class, value -> value).protocol("rsf").register();
            MetBindInfo<Echo> binding = client.publisher().rsfService(Echo.class).protocol("rsf").register();
            server.start();
            client.start();
            Echo remote = client.getRsfClient(server.getListenAddress("rsf")).getRemote(binding);

            // 256 个不同参数的并发调用要分别匹配自己的结果，并在随机选取下用到四个成员。
            ExecutorService callers = Executors.newFixedThreadPool(8);
            try {
                List<Future<?>> pending = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    int worker = i;
                    pending.add(callers.submit(() -> {
                        for (int j = 0; j < 32; j++) {
                            String value = "worker-" + worker + "-" + j;
                            assertEquals(value, remote.echo(value));
                        }
                    }));
                }
                for (Future<?> result : pending) {
                    result.get(10, TimeUnit.SECONDS);
                }
                assertEquals(4, ports.size());
                Set<Integer> initialPorts = Set.copyOf(ports);

                // 后续请求继续复用既有连接，不能额外创建第五个成员。
                for (int i = 0; i < 64; i++) {
                    String value = "reuse-" + i;
                    assertEquals(value, remote.echo(value));
                }
                assertEquals(initialPorts, ports);
            } finally {
                callers.shutdownNow();
                assertTrue(callers.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }
}
