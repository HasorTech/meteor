---
title: "按参数分流"
description: "根据租户编号等调用参数选择提供者实例。"
---

# 按参数分流

假设第一个参数是字符串租户编号，让 `tenant-a` 只调用 `10.0.0.10`。配置入口：[控制台](../console/web.md)、[Telnet](telnet/rule.md)和 [API](api.md)。

## 编写规则

```xml
<route>
    <if test="args.length &gt; 0 and utils.equals(args[0], 'tenant-a')">
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

`args[0]` 是第一个参数。对象参数可通过 getter 读取属性，例如 `args[0].tenantId`，读取前判断对象不为 `null`。XML 中的 `<`、`>`、`&` 需转义。

## 执行效果

`tenant-a` 没有符合条件的地址时调用失败；其他租户使用原有可选地址。

参数路由在服务路由、方法路由之后继续筛选。
