---
title: "服务停用"
description: "说明服务停用的范围、执行效果和恢复条件。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 服务停用

停用服务会拒绝后续业务请求，不会关闭应用进程，也不等待已发出的请求结束。配置入口：[控制台](../console/web.md#修改状态)、[Telnet](telnet/service.md)和 [API](api/state.md)。

## 停用范围

- 整个服务：停用该服务在所有实例上的网络调用和地址发布。
- 实例下的服务：只停用该实例的这一项服务。
- 实例阻断：阻断该实例的网络业务请求，不影响管理入口。

## 代码示例

<Tabs groupId="governance-api" defaultValue="local">
<TabItem value="local" label="本地 API">

在当前应用已有的 `MetContext` 中，`binding` 是已登记的服务：

```java
// 只停用当前上下文中已登记的服务
context.getUpdater().setServiceEnabled(binding.getBindID(), false);
context.getUpdater().setServiceEnabled(binding.getBindID(), true);

// 阻断或恢复当前实例的业务请求
context.getUpdater().setBlock(true);
context.getUpdater().setBlock(false);
```

</TabItem>
<TabItem value="registry" label="注册中心 API">

通过注册中心操作时，取得 [RegistryAdmin](api/registry.md#更新规则)，在其 `try` 块内调用：

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

</TabItem>
</Tabs>


## 恢复条件

在原对象上重新启用或解除阻断。整个服务仍处于停用状态时，单独启用某个实例下的服务不能恢复调用；解除实例阻断也不会启用原本停用的服务。

注册中心中的修改需等待应用同步后生效。
