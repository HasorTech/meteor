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
