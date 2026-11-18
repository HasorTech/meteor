---
slug: one-service-many-protocols
title: "一个 Java 服务如何接入多种协议"
description: "从业务接口、协议插件和网络传输三个层面，理解 Meteor 的多协议发布方式。"
authors: [meteor]
tags: [RPC, 协议]
topics: [getting-started, protocols]
language: zh-cn
---

一个服务可能同时面对 Java 应用、浏览器和其他语言的客户端。Meteor 让应用以 Java 接口描述业务，通过协议插件对接不同调用方。这篇文章从配置和职责分工开始，说明如何组织一次多协议发布。

<!-- truncate -->

## 先定义业务接口

业务服务仍然是普通 Java 接口。接口定义服务的方法，实现类负责处理业务，不需要自己拆解 HTTP 请求或编码 RPC 报文。

```java
public interface EchoService {
    String echo(String message);
}
```

协议插件在发布阶段读取接口与类型描述，并准备对应的协议绑定。具体协议需要的字段编号、契约资源和注解，仍应按照该协议的手册配置。

## 在端点挂载协议

以下示例需要引入 `meteor-protocol-jsonrpc` 和 `meteor-protocol-hprose`。它们共享一个 HTTP 连接器，通过不同路径接收请求：

```java
MetConfiguration configuration = new MetConfiguration();
var http = configuration.connector("http") //
        .bind("127.0.0.1", 8080);
http.protocol("jsonrpc") //
        .contextPath("/jsonrpc");
http.protocol("hprose") //
        .contextPath("/hprose");
```

这里的共享来自同一个端点对象。分别创建两个连接器并绑定相同端口，并不会自动合并成一个监听。

## 选择服务暴露范围

容器启动后，在服务发布链中选择协议：

```java
context.publisher()
        .rsfService(EchoService.class, message -> message)
        .protocol("jsonrpc", "hprose")
        .aliasName("jsonrpc", "echo")
        .aliasName("hprose", "echo")
        .register();
```

协议的选择属于服务发布配置，路径和编码属于各自的绑定。为端点增加协议，不应替代对业务服务暴露范围的明确选择。应用结束时，由上下文的创建者关闭 `MetContext`。

## 明确互通边界

统一入口让应用使用方式接近，但不同协议仍有自己的类型、异常、流式能力和外部 SDK 限制。例如标准 gRPC 互通使用 Protobuf；不能把某个协议可传输的 Java 对象直接套用到所有客户端。

先根据调用方选择协议，再对照手册准备契约，最后用实际客户端验证调用方向和数据类型。

继续阅读[协议支持](../docs/guides/protocols/about.md)和[多协议发布](../docs/guides/services/multiple/about.md)。第一次接入可从[快速开始](../docs/guides/start/quick-start.md)中的 JSON-RPC Echo 服务开始。
