---
title: "框架扩展"
description: "按需扩展应用、序列化、RPC 协议、网络传输和注册中心。"
---

# 框架扩展

Meteor 提供应用扩展和 SPI 接口，可接入自己的基础设施或通信方式。先通过[架构和工作流程](architecture.md)了解各扩展所在的位置。

## 使用指引

- [编写应用扩展](lifecycle.md)：随上下文初始化和释放资源，向应用提供扩展服务。
- [自定义 JSON 序列化](json.md)：替换业务数据的 JSON 编解码实现。
- [扩展序列化方式](serialization.md)：增加新的命名编码器。
- [扩展 RPC 协议](protocol.md)：在已有传输上实现新的 RPC 消息格式。
- [扩展连接处理链](pipeline.md)：在协议处理前增加网络处理步骤。
- [扩展网络传输](transport.md)：接入新的网络连接和数据传输实现。
- [扩展注册中心](registry.md)：使用新的存储保存服务登记和治理配置。

## 注册方式

应用扩展通过 `config.addExtension(factory)` 安装；协议、传输和序列化编码器通过 `META-INF/services` 注册。连接处理链通过连接器的 `transportPipeline` 参数指定。

拦截业务调用见[拦截器](../services/filters.md)，监听应用和服务事件见[生命周期监听](../services/lifecycle/listeners.md)。
