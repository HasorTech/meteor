---
title: "同机房优先"
description: "优先调用同机房实例，在本机房可用实例不足时允许跨机房调用。"
---

# 同机房优先

优先调用同机房实例；同机房没有可用实例或数量占比不足时，允许跨机房调用。配置入口：[控制台](../console/web.md)、[Telnet](telnet/rule.md)和 [API](api.md)。

## 标记机房

在应用的 `MetConfiguration` 中设置机房，再创建上下文。同机房的提供者和消费者填写相同值：

```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.setUnitName("east"); // 当前应用所属机房

try (MetContext context = config.buildContext()) {
    context.start();
    // 发布或订阅服务……
}
```

## 编写规则

```xml
<controlSet>
    <flowControl enable="true" type="unit">
        <threshold>0.3</threshold>
    </flowControl>
</controlSet>
```

`threshold` 是同机房可用地址的最低占比，范围为 0～1。共有 10 个地址时，同机房有 3 个就只使用这 3 个；只有 2 个则允许使用全部地址。

## 设置例外

`exclusions` 允许指定地址不受机房限制，多个 IP 模式用逗号分隔：

```xml
<controlSet>
    <flowControl enable="true" type="unit">
        <threshold>0.3</threshold>
        <exclusions>10.20.*,10.30.*</exclusions>
    </flowControl>
</controlSet>
```

例外地址加入同机房的可选地址，也参与占比计算；之后仍会经过路由规则筛选。

与[请求限流](limits.md)组合时，将两种 `flowControl` 放在同一个 `controlSet` 中保存。
