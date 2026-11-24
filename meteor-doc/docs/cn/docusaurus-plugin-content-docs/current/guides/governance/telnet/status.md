---
title: "status：运行状态"
description: "分别介绍控制台 Telnet 和治理 Telnet 的 status 命令。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# status：运行状态

<Tabs groupId="telnet-entry" defaultValue="console">
<TabItem value="console" label="控制台 Telnet">

查看 Web、Telnet 是否启用，以及当前 Telnet 连接选择的集群 ID，无需先选择集群。

```text
status
```

返回的 `web`、`telnet` 表示入口开关，`cluster` 表示当前连接选择的集群；未选择时为 `null`。此命令不查询业务实例的运行状态，实例状态使用 [instance](instance.md)。

</TabItem>
<TabItem value="application" label="治理 Telnet">

查询当前连接的应用实例：

```text
status
```

## 查看结果

- `Application`、`Instance`：应用名称和实例 ID。
- `Runtime`：业务监听状态，`ONLINE` 为在线，`OFFLINE` 为离线。
- `Blocked`：是否阻断业务请求。
- `Management`：Telnet 管理监听地址。
- `Protocols`：运行中的协议。
- `Governance`：`LOCAL` 为本地修改，`REGISTRY` 为通过注册中心保存。
- `Registry`、`Connection`：注册中心类型和连接状态。

接入注册中心时，还会显示 `Last sync`（最近同步成功时间）、`Last attempt`（最近同步时间）和 `Last result`（同步结果）。未成功同步时，`Last sync` 为 `NEVER`。

</TabItem>
</Tabs>
