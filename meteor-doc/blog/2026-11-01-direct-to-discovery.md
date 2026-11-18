---
slug: direct-to-discovery
title: "从点对点调用到注册中心发现服务"
description: "先验证服务契约与直连调用，再通过注册中心提供地址与运行信息。"
authors: [meteor]
tags: [RPC, 服务发现]
topics: [getting-started, discovery]
language: zh-cn
---

第一次接入 RPC 时，显式指定地址有助于确认服务与客户端是否配置一致。服务实例增加后，可以由注册中心提供地址。两种方式共享业务接口，区别在于服务地址从哪里获取。

<!-- truncate -->

## 先运行直连示例

提供者配置监听地址、挂载协议并发布服务。消费者订阅相同身份的接口，再把地址传给客户端入口：

```java
InterAddress address = new InterAddress("jsonrpc", "127.0.0.1", 8080, "local");
OrderDemoService orders = context.getRsfClient(address).getRemote(binding);
```

这一步先确认协议、服务名称、别名、路径和 DTO 映射。仓库的 JSON-RPC [提供者](../../met-example/peer/java/meteor/src/main/java/net/hasor/example/jsonrpc/ProviderMain.java)与[消费者](../../met-example/peer/java/meteor/src/main/java/net/hasor/example/jsonrpc/ConsumerMain.java)可以作为起点。

## 接入服务发现

注册中心扩展负责登记提供者端点，并向消费者同步可用的服务信息。接入后，消费者通过不指定业务地址的入口获取代理：

```java
OrderDemoService orders = context.getRsfClient().getRemote(binding);
```

注册中心配置、连接方式和所需依赖见[注册中心接入](../docs/guides/discovery/nacos.md)。示例工程分别提供 Nacos 和 ZooKeeper 的独立入口，运行前需要先准备对应服务。

## 区分发现与调用

注册中心提供地址与服务目录，协议仍负责实际调用。发现了实例并不意味着所有协议或所有 SDK 组合都已验证；服务端挂载的协议、消费者订阅的身份和对应路径仍需要匹配。

运行期可通过注册中心管理 API 查看实例与服务，也可以使用[管理控制台](../docs/guides/console/about.md)。
