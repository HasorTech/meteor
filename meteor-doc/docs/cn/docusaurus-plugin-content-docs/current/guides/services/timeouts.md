---
title: "超时和异常"
description: "处理远程调用中的超时和异常，配置等待上限。"
---

# 超时和异常

超时表示操作未在规定时间内完成，不代表服务端未执行。下单、扣款等操作超时后，应先确认最终状态，避免重复执行。

- **建立连接**：未在规定时间内连接提供者，应检查目标地址和网络。
- **消费者等待响应**：等待时间包括建立连接、网络传输、服务端排队和执行。
- **提供者处理请求**：请求排队或执行超过处理时限。
- **读取异步结果**：限时读取 Future 超时，RPC 可能仍在执行。

调用失败还可能来自无可用地址、连接错误或业务异常。排查时先核对服务坐标、地址和启用状态，再检查网络、TLS、提供者负载和业务日志，并保留原始异常。

## 配置超时

应用级默认值，单位为毫秒：

```properties
meteor.connect-timeout=3000
meteor.default-timeout=6000
# 沿用 default-timeout，即 6 秒
meteor.request-timeout=0
```

单个服务通过 `@MetService(clientTimeout = 1000)` 或 Builder 的 `timeout(1000)` 设置超时。正数配置的优先级如下，不累加：

```text
服务级超时 → request-timeout → default-timeout
```

Builder 设置优先于接口注解。请求未携带正数超时时，提供者使用自身的 `default-timeout`。建立连接时，连接超时或 RPC 超时先到期，本次调用就会失败。

修改应用配置后重启生效。

各框架的配置示例见“创建应用”：

- [Spring 整合](../application/spring.md#call-timeout)
- [Hasor 整合](../application/hasor.md#call-timeout)
- [Solon 整合](../application/solon.md#call-timeout)

## 使用示例 {#处理异常}

### 同步调用

`prices` 为 `PriceService` 的服务代理。先解开异常包装，再处理超时或其他失败：

```java
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import net.hasor.meteor.domain.MetException;
import net.hasor.meteor.domain.MetTimeoutException;

try {
    System.out.println("报价：" + prices.quote("SKU-1001"));
} catch (RuntimeException failure) {
    Throwable cause = failure;
    while ((cause instanceof UndeclaredThrowableException
            || cause instanceof ExecutionException) && cause.getCause() != null) {
        cause = cause.getCause();
    }
    if (cause instanceof InterruptedException) {
        Thread.currentThread().interrupt();
    }
    if (cause instanceof MetTimeoutException || cause instanceof TimeoutException) {
        System.out.println("报价超时，请稍后重试");
    } else if (cause instanceof MetException rpcFailure) {
        System.err.println("RPC 状态：" + rpcFailure.getStatus());
        throw rpcFailure;
    } else {
        throw new IllegalStateException("报价查询失败", cause);
    }
}
```

### 异步调用

`client` 为客户端，`service` 为订阅时返回的 `MetBindInfo`。读取超时不等于 RPC 失败：

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.hasor.meteor.MetFuture;
import net.hasor.meteor.domain.MetTimeoutException;

MetFuture future = client.asyncInvoke(service, "quote",
        new Class<?>[] { String.class }, new Object[] { "SKU-1001" });

try {
    Object price = future.getData(1, TimeUnit.SECONDS);
    System.out.println("报价：" + price);
} catch (TimeoutException waiting) {
    // 读取超时，不取消 RPC，可再次读取
    System.out.println("报价尚未返回，稍后查看");
} catch (ExecutionException failure) {
    Throwable cause = failure.getCause();
    if (cause instanceof MetTimeoutException) {
        // RPC 超时，本次调用失败
        System.out.println("报价超时，请稍后重试");
    } else {
        throw new IllegalStateException("报价查询失败", cause);
    }
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("报价查询被中断", interrupted);
}
```

[回调调用](callbacks.md)在 `failed` 中处理失败原因。

## RPC 状态

`MetException.getStatus()` 返回 Meteor 内部状态，不是 HTTP 响应码。常见失败如下；其他协议的原生客户端会按各自协议表示错误。

| 状态 | 含义 |
| --- | --- |
| `401 Unauthorized` | 管理调用认证失败。 |
| `403 Forbidden` | 服务已停用或实例已阻断。 |
| `404 NotFound` | 服务、方法或可用地址不存在。 |
| `405 QueueFull` | 提供者的业务处理队列已满。 |
| `408 Timeout` | 连接、等待响应或提供者处理超时。 |
| `500 InvokeError` | 执行业务方法失败。 |
| `501 SendLimitPolicy` | 达到消费者的在途请求上限。 |
| `505 ProtocolUndefined` | 无匹配协议、协议选择不唯一或协议版本不支持。 |
| `506 ProtocolError` | RPC 报文编码或解码失败。 |
| `511 SerializeError` | 参数或结果序列化失败。 |
| `600 NetworkError` | 网络连接或收发失败。 |

`102 Accept` 表示请求已接受、仍在处理；`200 OK` 表示正常返回，两者不是失败状态。业务异常还可能保留为原始异常类型，不能只依赖状态码处理。
