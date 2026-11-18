---
slug: http-protocol-paths
title: "多协议共享 HTTP 端口的路径配置"
description: "区分协议挂载点与服务子路径，理解 HTTP 端点的路由规则。"
authors: [meteor]
tags: [协议, 配置]
topics: [protocols]
language: zh-cn
---

多个协议共享 HTTP 端口时，先由协议声明物理挂载点，再由服务声明其下的子路径。

<!-- truncate -->

```java
configuration.connector("http") //
        .bind("127.0.0.1", 8080) //
        .protocol("jsonrpc") //
        .contextPath("/rpc");
context.publisher().rsfService(EchoService.class, message -> message) //
        .protocol("jsonrpc") //
        .aliasName("jsonrpc", "echo") //
        .contextPathByPrefix("jsonrpc", "/public") //
        .register();
```

服务目录为 `/rpc/public`。前缀子路径 `/` 表示使用物理挂载点 `/rpc`；精确子路径也会拼接物理挂载点，不会绕过它。未声明子路径时沿用协议默认的分组、版本路由。

前缀和精确模式重复声明时以后一次为准。发布时检查路径格式与冲突，消费者须配置一致的子路径，并以物理挂载点作为 SDK 直连地址。

完整规则见[路径挂载](../docs/guides/services/multiple/http-paths.md)。
