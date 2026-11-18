---
title: "按 IP 分流"
description: "通过服务路由指定或排除提供者 IP。"
---

# 按 IP 分流

通过服务路由保留或排除指定 IP，地址必须已存在。配置入口：[控制台](../console/web.md)、[Telnet](telnet/rule.md)和 [API](api.md)。

## 指定 IP

只调用 `10.0.0.10` 和 `10.0.0.11`：

```xml
<route>
    <!-- addresses 是可选地址列表；selected 是自定义变量名，保存筛选结果 -->
    <filter name="selected" from="addresses" item="address"
            test="utils.equals(address.host, '10.0.0.10') or utils.equals(address.host, '10.0.0.11')"/>
    <!-- 返回筛选结果；value 与上面的 name 一致，selected 不是固定值 -->
    <return value="selected"/>
</route>
```

`address.host` 是 IP。单个 IP 删除 `or` 后的条件；同一 IP 的不同进程用 `address.port` 区分。

## 排除 IP

维护 `10.0.0.10` 时，改为保留其他地址：

```xml
<route>
    <filter name="selected" from="addresses" item="address"
            test="!utils.equals(address.host, '10.0.0.10')"/>
    <return value="selected"/>
</route>
```

## 执行效果

筛选后没有可用地址，调用失败，不会改用已排除的实例。

自定义脚本见[规则语法](rule-syntax.md)。临时指定单个目标见[使用固定地址](../discovery/direct.md#临时指定地址)。
