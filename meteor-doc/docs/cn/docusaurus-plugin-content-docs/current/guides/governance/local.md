---
title: "本地优先"
description: "本地优先默认开启；登记本地实现后直接调用，也可强制使用网络。"
---

# 本地优先

本地优先默认开启，无需配置。默认客户端优先调用同一 `MetContext` 中的服务实现，没有本地实现时通过网络调用。

## 本地调用

在已启动的 `context` 中登记实现并调用：

```java
// 登记本地实现
var binding = context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .register();

// 默认直接调用本地实现
EchoService echo = context.getRsfClient().getRemote(binding);
System.out.println(echo.echo("hello"));
```

## 分布式调用

`asDistributedCall()` 改为通过网络调用，自身地址也参与选址。

```java
// 已开启 RSF 监听；本例通过网络调用自身
var binding = context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .asDistributedCall() // 关闭此服务的本地优先
        .bindAddress(context.getListenAddress("rsf"))
        .register();

EchoService echo = context.getRsfClient().getRemote(binding);
System.out.println(echo.echo("hello"));
```
