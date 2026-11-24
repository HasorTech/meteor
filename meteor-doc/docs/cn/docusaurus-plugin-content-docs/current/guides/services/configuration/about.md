---
title: "框架配置"
description: "配置调用超时、网络、连接池、并发、地址缓存和数据目录。"
---

# 框架配置

框架配置用于设置连接方式、调用等待时间和资源上限。修改应用配置后重启生效；框架中的配置入口见[普通项目](../../application/java.md)、[Spring](../../application/spring.md)、[Hasor](../../application/hasor.md)和 [Solon](../../application/solon.md)。

## 使用指引

- [超时和异常](../timeouts.md)：设置等待上限，处理连接、调用及异步结果超时。
- [网络配置](network.md)：设置监听地址和注册中心发布地址。
- [线程和并发](resources.md)：设置业务线程、队列和在途请求上限。
- [连接池](connections.md)：为同一提供者配置多条出站连接。
- [HTTP/2 接收窗口](http2-window.md)：调整单条连接允许对端发送的数据量。
- [地址缓存](cache.md)：配置地址刷新、磁盘缓存和失效等待时间。
- [数据目录](data-home.md)：保存实例身份和缓存，为多个实例分配独立目录。

Java 示例均在创建 `MetContext` 前配置；构建后修改 `MetConfiguration` 不会改变已创建的上下文。TLS 和管理认证见[安全通信](../security/about.md)。
