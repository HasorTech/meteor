---
title: "服务发现"
description: "通过直连、地址本或注册中心获取提供者地址。"
---

# 服务发现

消费者通过固定地址、地址本或注册中心获取提供者地址，本节介绍消费者侧的配置。

## 使用指引

- [使用固定地址](direct.md)，在代码中指定提供者地址。
- [使用地址本](address-book.md)，通过 XML 文件维护提供者地址。
- [接入 Nacos](nacos.md)，从 Nacos 获取服务地址。
- [接入 ZooKeeper](zookeeper.md)，从 ZooKeeper 获取服务地址。
- [接入 JDBC 注册中心](jdbc.md)，从数据库获取服务地址。
- [对接 Dubbo 注册生态](dubbo.md)，发现原生 Dubbo 注册数据中的服务。

## 更新配置

Nacos、ZooKeeper 和 JDBC 注册扩展通过订阅及周期核对同步注册数据和治理配置。周期核对默认每 `1000` 毫秒执行一次，在注册配置加入上下文前设置：

```java
registry.setRefreshMillis(1000);
```

该间隔必须为正数，不是 RPC 超时，也不是[地址缓存](../services/configuration/cache.md)的刷新间隔；网络和存储延迟会影响实际生效时间。
