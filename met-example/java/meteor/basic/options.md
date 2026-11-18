# 隐式传参

在 IDEA 中打开 `met-example` 并同步 Gradle，运行 [LocalOptionsDemo](src/main/java/net/hasor/example/basic/options/LocalOptionsDemo.java) 的 `main`。示例自动创建提供者和消费者，通过随机端口建立真实 RSF 连接，无需注册中心。

- `withOptions` 发送请求编号，`PriceServiceImpl` 通过 `MetRequestLocal` 读取。
- `MetResponseLocal` 写入响应附加信息，消费者通过完整 `MetResponse` 读取。

输出示例：

```text
报价服务 request-id=order-001, sku=SKU-1001
报价=100.00
报价服务 request-id=order-001, sku=SKU-1001
processed-by=price-service
报价=100.00
```

Local 工具仅访问当前调用线程的上下文，不用于在客户端预设下一次请求，也不会自动向异步线程或下游调用传播。
