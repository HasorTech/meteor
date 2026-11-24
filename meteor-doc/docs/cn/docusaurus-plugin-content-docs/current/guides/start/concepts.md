---
title: "工作方式"
description: "通过整体架构了解 Meteor 在应用中的位置，以及协议接入、注册发现、服务调用和管理之间的关系。"
mdx:
  format: mdx
---

import useBaseUrl from '@docusaurus/useBaseUrl';

# 工作方式

Meteor 嵌入应用进程运行。提供者发布业务接口和实现，消费者通过 Meteor 或对应协议的原生 SDK 调用服务；注册中心提供地址和治理配置，控制台、Telnet 和 API 提供管理入口。

![Meteor 整体架构](/img/architecture/working-model.svg)

实线表示业务调用，蓝色虚线表示注册发现和配置同步，绿色虚线表示管理操作。注册中心不转发业务请求。<a href={useBaseUrl('/img/architecture/working-model.svg')} target="_blank" rel="noreferrer">查看架构大图</a>。

## 协议接入

用户应用中的服务通过 RPC 接入点接收请求，再交给 RPC 调度执行对应业务方法。同一份业务实现可以配置多个协议接入点：

- **Protobuf 协议组**：[gRPC](../protocols/grpc.md)、[gRPC-Web](../protocols/grpc-web.md)、[Connect](../protocols/connect.md)、[Triple](../protocols/triple.md)。
- **HTTP / WebSocket 协议组**：[JSON-RPC](../protocols/jsonrpc.md)、[XML-RPC](../protocols/xmlrpc.md)、[SOAP](../protocols/soap.md)、[Hessian](../protocols/hessian.md)、[Hprose](../protocols/hprose.md)。
- **TCP / UDP 协议组**：[RSF](../protocols/rsf.md)、[Dubbo](../protocols/dubbo.md)、[Thrift](../protocols/thrift.md)、[ONC RPC](../protocols/oncrpc.md)。

分组用于查找协议，具体传输方式、调用能力和客户端语言见各协议文档。连接器管理网络监听和出站连接，可配置[端口复用](../services/multiple/ports.md)、[路径挂载](../services/multiple/http-paths.md)和[安全通信](../services/security/about.md)。

## 注册发现

接入注册中心后，提供者登记服务和接入地址，Meteor 消费者同步这些地址，并在调用时选择提供者。实例上下线或地址发生变化时，消费者更新可用地址；治理配置也通过注册中心同步到应用。

支持 [ZooKeeper](../discovery/zookeeper.md)、[Nacos](../discovery/nacos.md)、[JDBC 注册中心](../discovery/jdbc.md)，也可以[对接 Dubbo 注册生态](../discovery/dubbo.md)。原生客户端按其 SDK 支持的方式配置地址或接入注册发现。

使用[固定地址](../discovery/direct.md)或[地址本](../discovery/address-book.md)时，消费者直接取得提供者地址，不需要注册中心。

## 服务调用

1. 提供者配置接入协议，发布服务接口和业务实现。
2. Meteor 消费者订阅服务接口，通过固定地址、地址本或注册中心取得提供者地址。
3. 消费者选择提供者，按双方使用的协议发送请求。
4. 提供者的 RPC 接入点接收请求，RPC 调度定位服务和方法，执行业务实现并返回结果。

原生 SDK 按对应协议构造请求，直接调用提供者的接入点。客户端与服务端需要使用一致的方法名、参数和数据格式；依赖接口文件的协议还需要对应的 Proto、Thrift IDL 或 WSDL 文件。

Java 端支持同步、异步、回调等[调用方式](../services/calling/about.md)，部分协议支持流式调用。超时、拦截器等行为分别在[框架配置](../services/configuration/about.md)和[生命周期](../services/lifecycle/about.md)中设置。

## 服务管理

通过 [API](../governance/api.md)、[Web 控制台](../console/about.md)或 [Telnet](../governance/telnet/about.md)查询服务、调整状态、设置路由和限流规则。Web 控制台还可以调试方法并查看结果。

本地管理修改当前应用的运行状态；通过注册中心管理时，配置保存到注册中心，由应用同步后生效。管理入口按需启用，具体操作见[服务治理](../governance/about.md)。

接下来按[快速开始](quick-start.md)运行服务并发起调用。
