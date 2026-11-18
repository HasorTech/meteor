---
title: "本地 API"
description: "在消费者订阅服务后，通过上下文 API 设置路由或流控规则。"
---

# 本地 API

在消费者完成服务订阅后设置规则，只在当前应用生效，无需注册中心。

## 配置规则

将[治理脚本](../capabilities/about.md)保存为 `route.xml`，在已有的消费者代码中调用：

```java
import java.nio.file.Files;
import java.nio.file.Path;

String xml = Files.readString(Path.of("route.xml"));

// context 是当前消费者上下文，binding 是已订阅的服务
// 按 IP 分流：设置服务路由
boolean updated = context.getUpdater().updateServiceRoute(binding.getBindID(), xml);
```

其他规则使用对应方法，按需选择，不要将同一份 XML 同时设置到不同规则中：

```java
// 按方法分流
context.getUpdater().updateMethodRoute(binding.getBindID(), xml);
// 按参数分流
context.getUpdater().updateArgsRoute(binding.getBindID(), xml);
// 同机房优先、请求限流，共用一份 controlSet XML
context.getUpdater().updateFlowControl(binding.getBindID(), xml);
```

## 查看和清空

```java
// 查看当前服务路由
System.out.println(context.getUpdater().serviceRoute(binding.getBindID()));
// 清空当前服务路由
context.getUpdater().updateServiceRoute(binding.getBindID(), "");
```

更新返回 `false` 时检查日志：规则可能无效、服务地址尚未建立，或内容未变化。修改不保存到注册中心，后续同步可能覆盖本地设置。
