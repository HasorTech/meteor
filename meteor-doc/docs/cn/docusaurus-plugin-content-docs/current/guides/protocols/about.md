---
title: "协议支持"
description: "了解各协议的特点、支持特性、配置方式和原生客户端调用。"
---

# 协议支持

Meteor 可通过不同协议的接入点发布服务，也可调用已有 RPC 服务。各文章依次介绍协议特点、支持特性、依赖、启用方式和原生客户端调用。多协议配置见[多协议发布](../services/multiple/about.md)。

## 使用指引

| RPC 类别 | 支持的语言 | 说明 |
| --- | --- | --- |
| [RSF](rsf.md) | [Java（Meteor）](rsf.md?client=meteor#发起-rpc-调用) | 通过 TCP 在 Meteor Java 应用之间调用。 |
| [gRPC](grpc.md) | Java（[Meteor](grpc.md?client=meteor#发起-rpc-调用)、[SDK](grpc.md?client=java-sdk#发起-rpc-调用)）、[Python](grpc.md?client=python#发起-rpc-调用)、[Go](grpc.md?client=go#发起-rpc-调用)、[Node.js](grpc.md?client=node#发起-rpc-调用)、[C++](grpc.md?client=cpp#发起-rpc-调用) | 通过 HTTP/2 使用 Protobuf 消息，支持消息流。 |
| [gRPC-Web](grpc-web.md) | Java（[Meteor](grpc-web.md?client=meteor#发起-rpc-调用)、[SDK](grpc-web.md?client=java-sdk#发起-rpc-调用)）、[Python](grpc-web.md?client=python#发起-rpc-调用)、[Go](grpc-web.md?client=go#发起-rpc-调用)、[Node.js](grpc-web.md?client=node#发起-rpc-调用)、[JavaScript](grpc-web.md?client=browser#发起-rpc-调用) | 供浏览器使用 gRPC-Web 客户端调用。 |
| [Connect](connect.md) | Java（[Meteor](connect.md?client=meteor#发起-rpc-调用)、[SDK](connect.md?client=java-sdk#发起-rpc-调用)）、[Python](connect.md?client=python#发起-rpc-调用)、[Go](connect.md?client=go#发起-rpc-调用)、[Node.js](connect.md?client=node#发起-rpc-调用)、[JavaScript](connect.md?client=browser#发起-rpc-调用) | 通过 HTTP 使用 Protobuf 或 JSON 消息。 |
| [Triple](triple.md) | Java（[Meteor](triple.md?client=meteor#发起-rpc-调用)、[SDK](triple.md?client=java-sdk#发起-rpc-调用)）、[Python](triple.md?client=python#发起-rpc-调用)、[Go](triple.md?client=go#发起-rpc-调用)、[Node.js](triple.md?client=node#发起-rpc-调用)、[C++](triple.md?client=cpp#发起-rpc-调用) | 对接 Dubbo Triple，使用 Java 包装或 Protobuf 消息。 |
| [Dubbo](dubbo.md) | Java（[Meteor](dubbo.md?client=meteor#发起-rpc-调用)、[SDK](dubbo.md?client=java-sdk#发起-rpc-调用)）、[Go](dubbo.md?client=go#发起-rpc-调用)、[Node.js](dubbo.md?client=node#发起-rpc-调用) | 通过 TCP 对接 Dubbo 二进制协议服务。 |
| [Thrift](thrift.md) | Java（[Meteor](thrift.md?client=meteor#发起-rpc-调用)、[SDK](thrift.md?client=java-sdk#发起-rpc-调用)）、[Python](thrift.md?client=python#发起-rpc-调用)、[Go](thrift.md?client=go#发起-rpc-调用)、[Node.js](thrift.md?client=node#发起-rpc-调用)、[C++](thrift.md?client=cpp#发起-rpc-调用) | 通过 TCP 或 HTTP 对接 Thrift 生成客户端。 |
| [JSON-RPC](jsonrpc.md) | Java（[Meteor](jsonrpc.md?client=meteor#发起-rpc-调用)、[SDK](jsonrpc.md?client=java-sdk#发起-rpc-调用)）、[Python](jsonrpc.md?client=python#发起-rpc-调用)、[Go](jsonrpc.md?client=go#发起-rpc-调用)、[Node.js](jsonrpc.md?client=node#发起-rpc-调用)、[JavaScript](jsonrpc.md?client=browser#发起-rpc-调用)、[C](jsonrpc.md?client=c#发起-rpc-调用)、[C++](jsonrpc.md?client=cpp#发起-rpc-调用)、[Shell](jsonrpc.md?client=shell#发起-rpc-调用) | 通过 HTTP 或 WebSocket 发起 JSON 调用、通知和批量请求。 |
| [XML-RPC](xmlrpc.md) | Java（[Meteor](xmlrpc.md?client=meteor#发起-rpc-调用)、[SDK](xmlrpc.md?client=java-sdk#发起-rpc-调用)）、[Python](xmlrpc.md?client=python#发起-rpc-调用)、[Go](xmlrpc.md?client=go#发起-rpc-调用)、[Node.js](xmlrpc.md?client=node#发起-rpc-调用)、[JavaScript](xmlrpc.md?client=browser#发起-rpc-调用)、[C](xmlrpc.md?client=c#发起-rpc-调用)、[C++](xmlrpc.md?client=cpp#发起-rpc-调用)、[Shell](xmlrpc.md?client=shell#发起-rpc-调用) | 通过 HTTP 对接 XML-RPC 服务。 |
| [SOAP](soap.md) | Java（[Meteor](soap.md?client=meteor#发起-rpc-调用)、[SDK](soap.md?client=java-sdk#发起-rpc-调用)）、[Python](soap.md?client=python#发起-rpc-调用)、[Go](soap.md?client=go#发起-rpc-调用)、[Node.js](soap.md?client=node#发起-rpc-调用)、[JavaScript](soap.md?client=browser#发起-rpc-调用)、[C](soap.md?client=c#发起-rpc-调用)、[C++](soap.md?client=cpp#发起-rpc-调用)、[Shell](soap.md?client=shell#发起-rpc-调用) | 按 WSDL / XSD 接入 Web Service。 |
| [Hessian](hessian.md) | Java（[Meteor](hessian.md?client=meteor#发起-rpc-调用)、[SDK](hessian.md?client=java-sdk#发起-rpc-调用)）、[Python](hessian.md?client=python#发起-rpc-调用)、[Node.js](hessian.md?client=node#发起-rpc-调用) | 通过 HTTP 对接 Hessian Java 服务。 |
| [Hprose](hprose.md) | Java（[Meteor](hprose.md?client=meteor#发起-rpc-调用)、[SDK](hprose.md?client=java-sdk#发起-rpc-调用)）、[Python](hprose.md?client=python#发起-rpc-调用)、[Go](hprose.md?client=go#发起-rpc-调用)、[Node.js](hprose.md?client=node#发起-rpc-调用)、[JavaScript](hprose.md?client=browser#发起-rpc-调用) | 通过 HTTP 对接 Hprose 客户端。 |
| [ONC RPC](oncrpc.md) | Java（[Meteor](oncrpc.md?client=meteor#发起-rpc-调用)、[SDK](oncrpc.md?client=java-sdk#发起-rpc-调用)）、[Python](oncrpc.md?client=python#发起-rpc-调用)、[Go](oncrpc.md?client=go#发起-rpc-调用)、[C](oncrpc.md?client=c#发起-rpc-调用)、[C++](oncrpc.md?client=cpp#发起-rpc-调用) | 通过 TCP 或 UDP 使用 program、version、procedure 和 XDR 编码。 |
