---
title: "注册中心API"
description: "校验、保存和启用治理脚本，并按需恢复上一级配置。"
---

# 注册中心API

将规则保存到注册中心，应用同步后生效。以下以 ZooKeeper 为例，其他注册中心使用对应配置类。

## 更新规则

将[治理脚本](../capabilities/about.md)保存为 `route.xml`：

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.meteor.registry.admin.MetServiceAdmin;
import net.hasor.meteor.registry.admin.MetValidationResult;
import net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration;

// 地址、root 和管理 token 与应用一致
ZookeeperRegistryConfiguration registry = new ZookeeperRegistryConfiguration();
registry.setAddress("zk.example.com:2181");
registry.setRoot("/meteor-orders");
registry.setManagementToken("your-management-token");

String xml = Files.readString(Path.of("route.xml"));
try (RegistryAdmin admin = registry.buildAdmin()) {
    // 替换为实际服务 ID；此处操作整个服务
    MetServiceAdmin service = admin.getServiceAdmin("服务 ID");
    // 选择本次操作的规则类型
    MetRuleType type = MetRuleType.SERVICE_ROUTE; // 按 IP 分流
    // type = MetRuleType.METHOD_ROUTE; // 按方法分流
    // type = MetRuleType.ARGS_ROUTE;   // 按参数分流
    // type = MetRuleType.FLOW_CONTROL; // 同机房优先、请求限流

    // 校验、保存并启用
    MetValidationResult result = service.validateRule(type, xml);
    if (!result.isValid()) {
        throw new IllegalArgumentException(Arrays.toString(result.getErrors()));
    }
    service.updateRule(type, xml);
    service.enableRule(type);
    System.out.println(service.fetchRule(type));
}
```

## 选择对象

在上面的 `try` 块中，按需选择管理对象：

```java
// 整个服务
var service = admin.getServiceAdmin("服务 ID");
// 某个实例下的服务
var instanceService = admin.getServiceAdmin("服务 ID", "实例 ID");
// 集群默认规则，使用相同的规则操作方法
var defaults = admin.getClusterAdmin();
```

## 恢复规则

以下代码放在上面的 `try` 块内：

```java
service.disableRule(type); // 停用并保留内容，不使用上一级同类规则
service.enableRule(type);  // 启用；未保存内容时使用上一级配置
service.resetRule(type);   // 删除当前内容，保留开关
service.updateRule(type, ""); // 显式清空规则，不使用上一级规则内容
```

恢复上一级规则时，删除当前内容并确保开关已启用。同机房优先和请求限流共用一份流量控制 XML，保存时整体替换，需保留要继续使用的规则。
