---
title: "连接池"
description: "为同一提供者配置多条 TCP 或 HTTP 出站连接。"
---

# 连接池

消费者默认对同一目标使用一条连接。需要增加出站连接数时，在创建上下文前为连接器设置 `clientPoolSize`：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp")
        .option("clientPoolSize", 4)
        .protocol("rsf");
```

HTTP 连接器使用同一选项：

```java
config.connector("http")
        .option("clientPoolSize", 4)
        .protocol("jsonrpc");
```

`clientPoolSize` 默认为 `1`，必须为正整数，仅适用于 TCP、HTTP。每个目标地址分别建池，每次调用随机选择一个连接位置，首次选中时才建立连接，之后复用。

连接池大小不限制同时等待响应的请求数；请求并发上限见[线程和并发](resources.md#在途请求)。连接断开不影响池内其他连接，后续调用可重新建立连接；已提交的请求不会自动重发。
