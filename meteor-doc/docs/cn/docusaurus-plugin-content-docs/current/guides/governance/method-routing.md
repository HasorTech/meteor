---
title: "按方法分流"
description: "根据被调用的方法选择提供者实例。"
---

# 按方法分流

让 `findById` 只调用 `10.0.0.10`，其他方法不限制 IP。配置入口：[控制台](../console/web.md)、[Telnet](telnet/rule.md)和 [API](api.md)。

## 编写规则

```xml
<route>
    <if test="utils.equals(method.name, 'findById')">
        <!-- selected 是自定义变量名，保存筛选后的地址列表 -->
        <filter name="selected" from="addresses" item="address"
                test="utils.equals(address.host, '10.0.0.10')"/>
        <!-- 返回上面定义的 selected 列表 -->
        <return value="selected"/>
    </if>
    <!-- 条件不匹配时，返回全部可选地址 -->
    <return value="addresses"/>
</route>
```

`method.name` 是 Java 方法名。Java 重载以不同的对外名称发布时，可用 `method.signature`（完整的 Java 方法签名）在路由规则中区分。

## 执行效果

`findById` 没有符合条件的地址时调用失败；其他方法不受此条件限制。

方法路由只能从服务路由留下的地址中继续筛选。
