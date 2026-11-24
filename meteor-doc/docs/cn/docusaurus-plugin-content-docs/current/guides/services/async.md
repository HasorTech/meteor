---
title: "异步调用"
description: "先提交请求，再通过 MetFuture 取得结果。"
---

# 异步调用

## 工作方式

异步调用提交请求后返回 `MetFuture`，业务线程可以继续处理其他工作，再通过 Future 读取结果。读取时若调用尚未完成，当前线程才等待。

## 适用场景

适合先发起报价查询，再处理订单中的其他信息，或同时发起多项独立查询后汇总结果。提交后立即读取 Future 仍会等待，无法发挥异步处理的作用。

## 示例

`client` 为已创建的客户端，`service` 为订阅时返回的 `MetBindInfo`，接口声明 `String echo(String text)`。

```java
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.MetFuture;

Class<?>[] parameterTypes = { String.class };
Object[] arguments = { "hello" };
MetFuture future = client.asyncInvoke(service, "echo", parameterTypes, arguments);

// 可在等待结果前处理其他工作
Object result = future.getData(3, TimeUnit.SECONDS);
```

## 补充说明

### 读取结果

| 方法 | 返回内容 |
| --- | --- |
| `getData()` | 等待并读取业务结果。 |
| `getData(timeout, unit)` | 在指定等待时间内读取业务结果。 |
| `get(timeout, unit)` | 读取完整的 `MetResponse`。 |

Future 的等待时间与 RPC 的[调用超时](timeouts.md)分别生效。等待结束不保证提供者已经停止业务处理。请求完成后再关闭 `MetContext`。无需主动等待结果时，可以使用[回调调用](callbacks.md)。
