---
title: "请求限流"
description: "在消费者侧限制请求发送速率，按服务、方法或地址分别限流。"
---

# 请求限流

限制单个消费者每秒发送的请求数，多个消费者分别计算。配置入口：[控制台](../console/web.md)、[Telnet](telnet/rule.md)和 [API](api.md)。

## 编写规则

限制同一服务的持续发送速率为每秒 20 次：

```xml
<controlSet>
    <flowControl enable="true" type="speed">
        <action>Service</action>
        <rate>20</rate>
        <peak>100</peak>
        <timeWindow>10</timeWindow>
    </flowControl>
</controlSet>
```

- `rate`：持续速率，单位为次/秒，必须大于 0。
- `peak`：可积累的请求额度上限，不是每秒请求数。必须大于 0，且不小于 `rate × timeWindow / 1000`。
- `timeWindow`：补充额度的最小间隔，单位为毫秒，至少为 1。

## 计算范围

通过 `action` 选择：

- `Service`：同一服务的所有方法共享额度。
- `Method`：每个方法名分别计算，重载的同名方法共享额度。
- `Address`：每个目标地址分别计算。

## 执行效果

额度不足时等待额度，不会直接拒绝请求。此规则会增加调用耗时，不能代替并发上限，也不能限制多个消费者合计发来的请求数。

与同机房优先组合时，将两种规则放进同一个 `controlSet`，保存时整体替换。并发上限见[资源限制](../services/configuration/resources.md)。
