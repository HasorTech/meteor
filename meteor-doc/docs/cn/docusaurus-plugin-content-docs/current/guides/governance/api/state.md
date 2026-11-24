---
title: "服务状态 API"
description: "通过本地或注册中心 API 启停服务和阻断实例。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 服务状态 API

操作前确认范围：当前应用、整个服务，或某个实例。停用效果见[服务停用](../enabled.md)。

<Tabs groupId="governance-api" defaultValue="local">
<TabItem value="local" label="本地应用">

在已有的 `MetContext` 中通过 `MetUpdater` 修改运行状态。服务仍保持发布，阻断也不会改变监听器在线状态：

```java
// 只停用当前上下文中已登记的服务
context.getUpdater().setServiceEnabled(binding.getBindID(), false);
context.getUpdater().setServiceEnabled(binding.getBindID(), true);

// 阻断或恢复当前实例的业务请求
context.getUpdater().setBlock(true);
context.getUpdater().setBlock(false);
```

这些操作只修改当前进程，不写注册中心，重启后恢复启动配置。
上下线使用 `context.offline()` / `context.online()`，通过 `context.isOnline()` 查询；服务发布继续使用 `context.publisher()`。

在 @project.developmentVersion@ 中，`MetAccessPolicy` 为本地绑定按条件开放网络 RPC。`allow(binding, MetAccessEnum.INSTANCE_BLOCKED)` 允许绑定在实例阻断期间继续调用，`SERVICE_DISABLED` 用于服务停用期间。多个限制同时生效时，绑定必须获准通过每一项。

本地 API、Telnet 和注册中心同步最终更新当前上下文的同一组运行状态。入站和出站网络 RPC 都会检查这两项状态。解除实例阻断后，服务原有的停用状态继续生效。

管理 RPC 的服务端和客户端通过 `context.getExtension(MetAccessPolicy.class).allow(binding, MetAccessEnum.values())` 登记全部条件。目标端仍会校验管理凭据，绑定注销时授权自动撤销。使用 `asShadow()` 隐藏发布的业务服务同样受实例阻断约束。

</TabItem>
<TabItem value="registry" label="注册中心">

取得 [RegistryAdmin](registry.md#更新规则) 后，在其 `try` 块内调用：

```java
// 操作整个服务
var service = admin.getServiceAdmin("服务 ID");
service.disable();
System.out.println(service.isEnable());
service.enable();

// 只停用某个实例下的服务
admin.getServiceAdmin("服务 ID", "实例 ID").disable();

// 阻断或恢复整个实例
admin.getInstanceAdmin("实例 ID").block();
admin.getInstanceAdmin("实例 ID").unblock();
```

注册中心保存后需等待应用同步。服务整体停用时，单独启用某个实例下的服务不能解除限制。

</TabItem>
</Tabs>
