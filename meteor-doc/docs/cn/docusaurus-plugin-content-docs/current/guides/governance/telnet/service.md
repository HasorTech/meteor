---
title: "service：服务管理"
description: "分别介绍控制台 Telnet 和治理 Telnet 的 service 命令。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# service：服务管理

<Tabs groupId="telnet-entry" defaultValue="console">
<TabItem value="console" label="控制台 Telnet">

查看所选集群中的服务详情，服务 ID 使用 [services](services.md) 的查询结果：

```text
service info '[default]EchoService-1.0.0'
service info '[default]EchoService-1.0.0' --instance <实例ID>
```

不加 `--instance` 时查看整个服务，加上后查看指定实例下的服务。

## 启用和停用

修改指定实例下的服务：

```text
service disable '[default]EchoService-1.0.0' --instance <实例ID>
service enable '[default]EchoService-1.0.0' --instance <实例ID>
```

修改整个服务时，必须显式指定 `--scope service`，不传 `--instance`：

```text
service disable '[default]EchoService-1.0.0' --scope service
service enable '[default]EchoService-1.0.0' --scope service
```

可使用 `--cluster <集群ID>` 指定本次操作的集群。修改保存到注册中心，应用同步后生效，通过 `service info` 核对。停用效果见[服务停用](../enabled.md)。

</TabItem>
<TabItem value="application" label="治理 Telnet">

查看、启用或停用当前实例下的服务。服务 ID 使用 [services](services.md) 的实际输出值。

```text
service info|enable|disable <serviceId>
```

## 查看详情

```text
service info '[default]EchoService-1.0.0'
```

输出 JSON，包括服务 ID、角色、有效启用状态、协议、监听地址、路径和接口描述。

## 停用服务

```text
service disable '[default]EchoService-1.0.0'
```

拒绝该服务后续的业务请求，不关闭应用进程，也不等待已发出的请求结束。

## 启用服务

```text
service enable '[default]EchoService-1.0.0'
service info '[default]EchoService-1.0.0'
```

通过 `enabled` 检查有效状态。注册中心中上一级配置仍为停用时，当前实例下的启用操作不能覆盖该限制。

未接入注册中心时，修改仅在当前进程生效，重启后按启动配置恢复。接入后保存到当前实例下的服务；注册中心断开时写入失败，不会改为本地修改。`OK` 表示完成，`SAVED` 表示已保存但仍待同步或受上一级限制，通过 `service info` 核对。

</TabItem>
</Tabs>
