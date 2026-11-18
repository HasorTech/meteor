/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.basic.options;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.*;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 独立上下文通过真实 RSF 连接传递请求和响应附加信息。 */
public final class LocalOptionsDemo {
    private LocalOptionsDemo() {
    }

    public static void main(String[] args) throws Exception {
        MetConfiguration providerConfig = new MetConfiguration();
        providerConfig.setApplicationName("local-options-provider");
        providerConfig.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        MetConfiguration consumerConfig = new MetConfiguration();
        consumerConfig.setApplicationName("local-options-consumer");
        consumerConfig.connector("tcp").protocol("rsf");

        try (MetContext provider = providerConfig.buildContext(); MetContext consumer = consumerConfig.buildContext()) {
            provider.publisher().rsfService(PriceService.class, new PriceServiceImpl()).register();
            MetBindInfo<PriceService> service = consumer.publisher().rsfService(PriceService.class).register();
            provider.start();
            consumer.start();

            // 请求附加信息由客户端发送，服务端业务方法通过 Local 工具读取。
            MetClient client = consumer.getRsfClient(provider.getListenAddress("rsf")).withOptions(Map.of("request-id", "order-001"));
            PriceService prices = client.getRemote(service);
            System.out.println("报价=" + prices.quote("SKU-1001"));

            // 完整响应包含业务结果和服务端通过 Local 工具写入的附加信息。
            MetFuture future = client.asyncInvoke(service, "quote", new Class<?>[] { String.class }, new Object[] { "SKU-1001" });
            MetResponse response = future.get(3, TimeUnit.SECONDS);
            System.out.println("processed-by=" + response.getOption("processed-by"));
            System.out.println("报价=" + response.getData());
        }
    }
}
