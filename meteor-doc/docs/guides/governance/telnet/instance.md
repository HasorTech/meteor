---
title: "instance：实例管理"
description: "分别介绍控制台 Telnet 和治理 Telnet 的 instance 命令。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# instance：实例管理

<Tabs groupId="telnet-entry" defaultValue="console">
<TabItem value="console" label="控制台 Telnet">

查看指定实例的详情，实例 ID 使用 [instances](instances.md) 的查询结果：

```text
instance info <实例ID>
```

## 阻断和恢复

```text
instance block <实例ID>
instance unblock <实例ID>
```

操作作用于整个实例的网络业务调用。修改保存到注册中心，应用同步后生效，通过 `instance info` 核对。

可使用 `--cluster <集群ID>` 指定本次操作的集群。Console 不提供远程 `instance offline/online` 命令。

</TabItem>
<TabItem value="application" label="治理 Telnet">

控制当前连接的应用实例，Telnet 管理监听始终保留。

```text
instance block|unblock|offline|online
```

## 阻断请求

```text
instance block
status
```

拒绝当前实例后续的业务请求，业务监听仍然开启。恢复请求：

```text
instance unblock
```

未接入注册中心时，`block`、`unblock` 仅修改当前进程；接入后保存到当前实例，断开时写入失败。`OK` 表示完成，`SAVED` 表示已保存但仍待同步或受上一级限制，通过 `status` 核对。

## 关闭监听

```text
instance offline
status
```

关闭当前应用的业务监听，不退出进程。重新开启：

```text
instance online
```

`offline`、`online` 直接作用于当前进程，不保存为注册中心配置。通过 `status` 的 `Runtime` 和 `Blocked` 分别检查监听和阻断状态；开启监听不会解除阻断。

</TabItem>
</Tabs>
