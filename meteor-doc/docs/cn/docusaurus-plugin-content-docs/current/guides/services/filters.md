---
title: "拦截器"
description: "编写并注册 RPC 拦截器，统一记录服务执行耗时。"
---

# 拦截器

实现 `MetFilter` 可在调用前后统一执行日志记录等操作。以下在提供者端统一记录执行耗时。

## 编写 {#write-filter}

```java
import net.hasor.meteor.MetFilter;
import net.hasor.meteor.MetFilterChain;
import net.hasor.meteor.MetRequest;
import net.hasor.meteor.MetResponse;

public class RpcLogFilter implements MetFilter {
    @Override
    public void doFilter(MetRequest request, MetResponse response, MetFilterChain chain) throws Throwable {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            System.out.println("method=" + request.getMethod().getName() + ", elapsedMs=" + elapsedMs);
        }
    }
}
```

`chain.doFilter` 继续调用；`finally` 确保成功和异常均记录耗时。

## 注册

提供者初始化时，全局注册：

```java
context.publisher().bindFilter("rpc-log", new RpcLogFilter());
```

仅拦截单个服务：

```java
context.publisher().rsfService(PriceService.class, new PriceServiceImpl())
        .bindFilter("rpc-log", new RpcLogFilter())
        .register();
```

两种方式任选；同名时服务级配置优先。仅声明 Bean 不会生效，容器注册见[Spring](../application/spring.md#register-filter)、[Hasor](../application/hasor.md#register-filter)。

## 效果

消费者调用：

```java
String price = prices.quote("SKU-1001");
```

提供者日志示例：

```text
method=quote, elapsedMs=12
```

异常调用同样记录日志，异常继续向外抛出。耗时只计算提供者端的执行时间，不含网络往返和后续流消息处理。

消费者端的 `chain.doFilter` 返回时，响应可能尚未到达，不能用这段代码统计完整 RPC 耗时。本地直接调用不执行这些拦截器。
