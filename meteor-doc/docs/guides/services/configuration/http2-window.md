---
title: "HTTP/2 接收窗口"
description: "设置 HTTP/2 单条连接允许对端发送的数据量。"
---

# HTTP/2 接收窗口

HTTP/2 通过接收窗口控制对端可以继续发送的数据量。传输较大消息时，可在接收端调整连接窗口：提供者接收请求，消费者接收响应。

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http")
        .option("httpVersion", 2)
        .option("http2ConnectionWindowSize", 4 * 1024 * 1024)
        .protocol("grpc");
```

提供者需另行配置监听地址和端口，见 [gRPC](../../protocols/grpc.md#configuration)。

`http2ConnectionWindowSize` 默认为 `1048576` 字节（1 MiB），范围为 `65535`～`2147483647`。它通过 `WINDOW_UPDATE` 增加本端的连接接收额度，单流初始窗口仍为 `65535` 字节。若需保持最小连接窗口，显式设为 `65535`。

窗口按连接计算，不是线程数、请求并发数或超时时间。HTTP/2 明文和加密连接都适用；HTTPS 下的 HTTP/2 协商见 [HTTPS](../security/https.md)。
