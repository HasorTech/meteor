---
title: "meta：元信息"
description: "分别介绍控制台 Telnet 和治理 Telnet 的 meta 命令。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# meta：元信息

<Tabs groupId="telnet-entry" defaultValue="console">
<TabItem value="console" label="控制台 Telnet">

按键维护元信息，例如记录维护团队。使用 `--scope cluster` 操作集群，使用 `--scope service` 操作服务。

## 查询

```text
meta list --scope cluster
meta list '[default]EchoService-1.0.0' --scope service
```

## 设置和删除

值使用 JSON，字符串值需保留双引号：

```text
meta put '[default]EchoService-1.0.0' owner '"payments"' --scope service
meta remove '[default]EchoService-1.0.0' owner --scope service
```

操作集群时省略服务 ID：

```text
meta put owner '"platform"' --scope cluster
meta remove owner --scope cluster
```

`--scope service` 可配合 `--instance <实例ID>` 操作实例下的服务；所有操作可使用 `--cluster <集群ID>` 指定集群。保存后通过 `meta list` 核对，属性用途见[元信息](../metadata.md)。

</TabItem>
<TabItem value="application" label="治理 Telnet">

管理当前实例下服务的自定义属性，例如用 `owner` 记录维护团队。添加属性本身不会改变调用行为，应用和规则可读取这些属性。服务 ID 使用 [services](services.md) 的实际输出值。

```text
meta list <serviceId>
meta put <serviceId> <key> <JSON>
meta remove <serviceId> <key>
```

## 查看属性

```text
meta list '[default]EchoService-1.0.0'
```

以 JSON 输出当前服务的元信息。

## 设置属性

```text
meta put '[default]EchoService-1.0.0' owner '"payments"'
meta put '[default]EchoService-1.0.0' priority 10
meta put '[default]EchoService-1.0.0' labels '{"environment":"production"}'
```

同名属性会被覆盖。值必须是非 `null` 的 JSON；字符串保留双引号，外层单引号用于保留命令参数。

## 删除属性

```text
meta remove '[default]EchoService-1.0.0' owner
meta list '[default]EchoService-1.0.0'
```

未接入注册中心时，修改仅在当前进程生效；接入后保存到当前实例下的服务，断开时写入失败。`OK` 表示完成，`SAVED` 表示已保存但仍待同步或受上一级限制，通过 `meta list` 和 `status` 核对。

</TabItem>
</Tabs>
