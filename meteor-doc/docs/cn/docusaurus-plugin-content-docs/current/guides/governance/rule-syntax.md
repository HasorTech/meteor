---
title: "规则语法"
description: "使用变量、条件和集合筛选编写路由脚本。"
---

# 规则语法

路由脚本从当前可用地址中筛选调用目标，通过[本地 API](api/local.md)、[注册中心API](api/registry.md)或 [Telnet](telnet/rule.md)设置。按 IP、方法和参数分流的现成写法见[治理能力](capabilities/about.md)。

## 编写脚本

下面读取服务元信息中的 `targetHost`：已配置时只调用该主机，未配置时保留全部候选地址。元信息的设置见[元信息](metadata.md)。

```xml
<route>
    <!-- bind 保存表达式结果，变量只在本次选址中有效 -->
    <bind name="targetHost" value="service.meta['targetHost']"/>
    <if test="!utils.isBlank(targetHost)">
        <!-- from 指定集合，item 是逐项判断时的变量，name 保存结果 -->
        <filter name="selected" from="addresses" item="address"
                test="utils.equals(address.host, targetHost)"/>
        <return value="selected"/>
    </if>
    <!-- return 返回地址列表并结束脚本；addresses 表示保留全部候选 -->
    <return value="addresses"/>
</route>
```

`route` 内按顺序执行 `bind`、`if`、`filter`、`return`，只有 `if` 能包含其他步骤。以上属性均必填，不支持其他标签或属性。变量名使用字母或下划线开头，后接字母、数字或下划线，不能覆盖下面的输入名。

## 读取输入

- `addresses`：当前候选地址列表，地址可读取 `host`、`port`、`schema`、`path`。
- `env`：消费者环境，可读取 `applicationName`、`unitName`。
- `service`：当前服务，可读取 `id`、`group`、`name`、`version`、`meta`。
- `method`：当前方法，可读取 `name`、`signature`。
- `args`：实参数组，例如 `args[0]`，对象属性可按名称读取。
- `utils`：提供 `equals`、`equalsIgnoreCase`、`isBlank`、`defaultIfBlank`、`contains`、`hash`；例如 `utils.defaultIfBlank(service.meta['targetHost'], '10.0.0.10')`。

## 返回地址

`return.value` 是表达式，`selected` 是示例自定义变量，不是固定关键字。返回值必须是候选地址组成的列表，不能新增地址；返回空列表会使调用失败。每条执行分支都应返回结果，否则本次选址报错。
