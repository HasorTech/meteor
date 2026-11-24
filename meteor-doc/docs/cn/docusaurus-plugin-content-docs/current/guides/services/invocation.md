---
title: "同步调用"
description: "通过服务接口代理调用方法并取得返回值。"
---

# 同步调用

## 工作方式

同步调用通过服务接口取得结果，调用线程会等待本次调用成功、失败或超时。接口代理将方法和参数交给 Meteor，再将返回结果交给业务代码。

## 适用场景

适合查询报价、检查库存等当前步骤必须取得结果才能继续的业务。等待期间当前线程不能继续执行后续代码。

## 示例

`client` 为已创建的客户端，`service` 为订阅时返回的 `MetBindInfo`。`EchoService` 声明 `String echo(String text)`。

```java
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;

public class SyncEchoCall {
    public String call(MetClient client, MetBindInfo<EchoService> service) {
        EchoService echo = client.getRemote(service);
        return echo.echo("hello");
    }
}
```

## 补充说明

### 选择客户端

| 获取方式 | 调用目标 |
| --- | --- |
| `context.getRsfClient("rsf://127.0.0.1:8080")` | 指定地址。 |
| `context.getRsfClient()` | 从地址本或注册中心选择提供者地址。 |

接口代理可以复用。同一个 `MetContext` 中有服务实现时，默认优先本地调用；需要强制通过网络调用时，在服务 Builder 上调用 `asDistributedCall()`。在 Spring、Hasor、Solon 中，也可以直接调用注入的服务接口，见[创建应用](../application/overview.md)。等待上限由[调用超时](timeouts.md)决定。需要在请求期间继续执行其他工作时，使用[异步调用](async.md)。
