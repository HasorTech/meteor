---
title: "rule：治理规则"
description: "分别介绍控制台 Telnet 和治理 Telnet 的 rule 命令。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# rule：治理规则

<Tabs groupId="telnet-entry" defaultValue="console">
<TabItem value="console" label="控制台 Telnet">

在所选集群中管理治理规则。类型参数使用 `service`（IP 分流）、`method`（方法分流）、`args`（参数分流）或 `flow`（同机房优先和请求限流），脚本写法见[治理能力](../capabilities/about.md)。

## 选择范围

每条命令必须指定 `--scope`，例如：

```text
rule show service --scope cluster
rule show service --scope instance --instance <实例ID>
rule show '[default]EchoService-1.0.0' service --scope service
rule show '[default]EchoService-1.0.0' service --scope service --instance <实例ID>
```

`cluster` 操作集群默认规则，`instance` 操作指定实例，`service` 操作整个服务；`service` 配合 `--instance` 操作实例下的服务。可添加 `--cluster <集群ID>` 指定本次操作的集群。

## 校验和保存

`validate` 只校验，不保存；实例范围不支持独立校验。

```text
rule validate '[default]EchoService-1.0.0' service '<route><return value="addresses"/></route>' --scope service
```

保存多行脚本，以单独一行 `.end` 结束输入：

```text
rule put '[default]EchoService-1.0.0' service --scope service --multiline
<route>
    <filter name="selected" from="addresses" item="address"
            test="utils.equals(address.host, '10.0.0.10')"/>
    <return value="selected"/>
</route>
.end
```

`put` 保存正文，不改变启用开关。用 `show` 查看规则，用 `enable` 启用：

```text
rule show '[default]EchoService-1.0.0' service --scope service
rule enable '[default]EchoService-1.0.0' service --scope service
```

## 停用和清空

```text
rule disable '[default]EchoService-1.0.0' service --scope service
rule clear '[default]EchoService-1.0.0' service --scope service
```

`disable` 停用并保留正文，`clear` 清空规则。配置保存到注册中心，应用同步后生效。

</TabItem>
<TabItem value="application" label="治理 Telnet">

管理当前实例下服务的治理脚本，服务 ID 使用 [services](services.md) 的实际输出值。

```text
rule show|validate|put|clear <serviceId> service|method|args|flow [XML|--multiline]
```

## 规则类型

- `service`：[按 IP 分流](../routing.md)。
- `method`：[按方法分流](../method-routing.md)。
- `args`：[按参数分流](../args-routing.md)。
- `flow`：[同机房优先](../units.md)、[请求限流](../limits.md)。

## 查看规则

```text
rule show '[default]EchoService-1.0.0' service
```

读取当前应用的实际规则，无内容时返回 `EMPTY`。

## 校验规则

```text
rule validate '[default]EchoService-1.0.0' service '<route><return value="addresses"/></route>'
```

只检查脚本能否解析，不修改配置，也不执行调用。

## 保存规则

使用 `--multiline` 输入多行 XML，以单独一行 `.end` 结束：

```text
rule put '[default]EchoService-1.0.0' service --multiline
<route>
    <!-- 只保留指定 IP 的提供者 -->
    <filter name="selected" from="addresses" item="address"
            test="utils.equals(address.host, '10.0.0.10')"/>
    <return value="selected"/>
</route>
.end
rule show '[default]EchoService-1.0.0' service
```

`validate` 也支持 `--multiline`。单行 XML 使用外层单引号保留内容。

注册中心模式下，`put` 保存内容，不改变规则启用开关；已停用的规则需通过[控制台](../../console/web.md)或[注册中心 API](../api/registry.md#恢复规则)启用。

## 清空规则

```text
rule clear '[default]EchoService-1.0.0' service
```

清空指定类型的规则，不删除其他类型。未接入注册中心时，修改仅在当前进程生效；接入后保存到当前实例下的服务，断开时写入失败。`OK` 表示完成，`SAVED` 表示已保存但仍待同步或受上一级限制，通过 `rule show` 和 `status` 核对。

</TabItem>
</Tabs>
