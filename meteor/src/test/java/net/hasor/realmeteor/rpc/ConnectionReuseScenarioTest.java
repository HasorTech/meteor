/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.realmeteor.rpc;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import net.hasor.example.bootstrap.TestConfigurationUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 连续及并发真实 RPC 的客户端源端口保持稳定，不随请求数量增长。 */
public class ConnectionReuseScenarioTest {
    @ParameterizedTest(name = "{0} 个调用线程复用一条连接")
    @ValueSource(ints = { 1, 8 })
    public void repeatedCallsKeepOneClientPort(int workers) throws Exception {
        MetConfiguration provider = TestConfigurationUtils.create();
        provider.connector("tcp") //
                .bind("127.0.0.1", 0) //
                .protocol("rsf");
        MetConfiguration consumer = TestConfigurationUtils.create();
        consumer.connector("tcp") //
                .protocol("rsf");
        Set<Integer> ports = ConcurrentHashMap.newKeySet();
        try (MetContext server = provider.buildContext(); MetContext client = consumer.buildContext()) {
            server.publisher().rsfService(Echo.class, value -> value).register();
            server.publisher().bindFilter("observe-peer", (request, response, chain) -> {
                ports.add(request.getRemoteAddress().getPort());
                chain.doFilter(request, response);
            });
            MetBindInfo<Echo> service = client.publisher().rsfService(Echo.class).register();
            server.start();
            client.start();
            Echo remote = client.getRsfClient(server.getListenAddress("rsf")).getRemote(service);
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<?>> calls = new ArrayList<>();
                for (int worker = 0; worker < workers; worker++) {
                    calls.add(executor.submit(() -> {
                        start.await();
                        for (int i = 0; i < 1000; i++) {
                            assertEquals(i, remote.echo(i));
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> call : calls) {
                    call.get(60, TimeUnit.SECONDS);
                }
                assertEquals(1, ports.size(), "RPC 数量增加不能导致新的客户端源端口持续出现");
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    public interface Echo {
        int echo(int value);
    }
}
