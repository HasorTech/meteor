---
title: "元信息"
description: "为服务附加负责人、环境等属性，供查询、业务代码和路由规则读取。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 元信息

元信息是附加在服务上的自定义属性。配置入口：[控制台](../console/web.md#修改元信息)、[Telnet](telnet/meta.md)和 [API](api/metadata.md)。例如，用 `owner` 记录维护团队，方便排查问题时找到负责人；用 `environment` 标记环境，方便识别服务。业务代码和路由规则也可读取这些属性，但添加属性本身不会改变调用行为。

## 代码示例

<Tabs groupId="governance-api" defaultValue="local">
<TabItem value="local" label="本地 API">

在当前应用中，使用已发布或订阅的服务 `binding` 设置属性：

```java
import java.util.Map;

// 记录维护团队，保留其他属性
binding.setMetaData(Map.of("owner", "payments"));

// 读取属性，供业务代码使用
System.out.println(binding.getMetaData("owner"));

// 删除属性
binding.removeMetaData("owner");
```

</TabItem>
<TabItem value="registry" label="注册中心 API">

通过注册中心保存时，取得 [RegistryAdmin](api/registry.md#更新规则)，在其 `try` 块内调用：

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

</TabItem>
</Tabs>


路由脚本通过 `service.meta['environment']` 读取环境属性，再按需要编写判断条件；读取的是当前服务的属性。

## 生效范围

本地修改只影响当前应用。注册中心可为整个服务、实例下的服务或集群设置属性，应用同步后生效；单独设置的键覆盖上一级值，删除后可能重新使用上一级值。

更新只影响指定的键，修改查询结果不会自动保存。键和值不能为 `null`，合并后最多 1000 条。
