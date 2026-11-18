---
title: "发布服务"
description: "将业务实现登记为服务提供者。"
---

# 发布服务

将服务接口和实现对象注册到 Meteor：

```java
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetPublisher;

public class EchoPublisher {
    public MetBindInfo<EchoService> publish(MetContext context) {
        EchoService implementation = text -> "Hello, " + text;
        MetPublisher publisher = context.publisher();
        return publisher.rsfService(EchoService.class, implementation)
                .group("default")
                .name("EchoService")
                .version("1.0.0")
                .protocol("rsf")
                .register();
    }
}
```

`EchoService` 声明 `String echo(String text)`。端口配置和应用启动见[创建应用](../application/overview.md)。

## 提供实现对象

| 方式 | 适用情况 |
| --- | --- |
| `rsfService(接口, 实例)` | 使用已有对象。 |
| `rsfService(接口, 实现类)` | 由 Meteor 创建对象。 |
| `rsfService(接口, Supplier)` | 通过 Supplier 返回对象。 |

`register()` 返回 `MetBindInfo`，`getBindID()` 返回服务 ID。应用仍需调用 `context.start()` 启动。容器自动发布见[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)整合；多协议发布见[开放多个协议](multiple/about.md)。
