---
title: "订阅服务"
description: "登记要调用的接口，取得调用时使用的 MetBindInfo。"
---

# 订阅服务

登记要调用的接口、分组、名称和版本，无需提供实现对象：

```java
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetPublisher;

public class EchoSubscriber {
    public MetBindInfo<EchoService> subscribe(MetContext context) {
        MetPublisher publisher = context.publisher();
        return publisher.rsfService(EchoService.class)
                .group("default")
                .name("EchoService")
                .version("1.0.0")
                .protocol("rsf")
                .register();
    }
}
```

接口、分组、名称和版本须与提供者一致。返回的 `MetBindInfo` 用于后续调用，登记不会调用业务方法。

## 提供目标地址

| 地址来源 | 配置方式 |
| --- | --- |
| 固定地址 | 调用时使用 `context.getRsfClient("rsf://主机:端口")`。 |
| 地址本 | 配置 `setAddressBook` 或 `meteor.address-book`。 |
| 注册中心 | 从注册中心发现地址。 |

使用地址本或注册中心时，通过 `context.getRsfClient()` 选择提供者地址。配置见[使用固定地址](../discovery/direct.md)、[使用地址本](../discovery/address-book.md)、[服务发现](../discovery/zookeeper.md)。调用前确认服务已发布、地址可用，调用方式见[同步调用](invocation.md)。
