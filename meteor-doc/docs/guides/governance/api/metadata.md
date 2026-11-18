---
title: "元信息 API"
description: "通过本地或注册中心 API 查询和按键维护服务属性。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 元信息 API

按键维护负责人、环境等属性，含义和使用场景见[元信息](../metadata.md)。

<Tabs groupId="governance-api" defaultValue="local">
<TabItem value="local" label="本地应用">

元信息由已订阅或发布的 `MetBindInfo` 持有，直接使用 `binding` 读写，无需另取管理对象：

```java
import java.util.Map;

// 读取当前值
System.out.println(binding.getMetaData("owner"));
// 修改指定的键，保留其他属性
binding.setMetaData(Map.of("owner", "payments"));
binding.removeMetaData("owner");
```

</TabItem>
<TabItem value="registry" label="注册中心">

取得 [RegistryAdmin](registry.md#更新规则) 后，在其 `try` 块内调用：

```java
import java.util.Map;

var service = admin.getServiceAdmin("服务 ID");
service.putMeta("owner", "payments");
service.putMeta(Map.of("environment", "staging"));
System.out.println(service.fetchMeta());
service.removeMeta("owner");

// 某个实例下的服务属性
admin.getServiceAdmin("服务 ID", "实例 ID").putMeta("owner", "team-a");
// 集群默认属性
admin.getClusterAdmin().putMeta("environment", "production");
```

删除单独设置的键后，可能重新使用上一级值；修改 `fetchMeta()` 的返回结果不会保存。

</TabItem>
</Tabs>
