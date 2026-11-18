---
title: "管理认证"
description: "为实例管理调用设置 token，区分通信加密和存储认证。"
---

# 管理认证

管理 token 用于校验实例管理 RPC，例如读取实例详情、停用服务和阻断请求。它不替代 TLS，也不替代数据库、Nacos 或 ZooKeeper 自身的认证。

## 业务实例

以 ZooKeeper 为例，在创建上下文前配置管理 token：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp").bind("0.0.0.0", 2180).protocol("rsf");

ZookeeperRegistryConfiguration registry = new ZookeeperRegistryConfiguration();
registry.setAddress("zk1:2181,zk2:2181,zk3:2181");
registry.setManagementToken("your-management-token");
config.addExtension(registry);
```

管理 token 不能为空。管理 RPC 默认使用 `rsf`，所用连接器需要实际启用该协议；需要加密时按[TLS](tls.md)配置。

若业务启用了多个协议，可限制管理 RPC 使用的协议。在上述 `registry` 加入上下文前设置：

```java
registry.setManagementProtocols("rsf"); // 管理 RPC 只通过 RSF 发布
```

该设置不限制业务服务协议，也不会自动启用连接器。无需注册中心的用法见[直连管理](../../governance/api/direct.md)。

## 管理端

管理端使用相同的 token 和注册中心连接信息：

```java
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration;

ZookeeperRegistryConfiguration registry = new ZookeeperRegistryConfiguration();
registry.setAddress("zk1:2181,zk2:2181,zk3:2181");
registry.setManagementToken("your-management-token");

try (RegistryAdmin admin = registry.buildAdmin()) {
    // 替换为目录中实际的实例 ID
    System.out.println(admin.getInstanceAdmin("实例 ID").instance());
}
```

Console 直连实例时填写匹配的管理凭据。通过 Console 的 Remote 入口连接另一个 Console 时，使用被连接端的 `management.token`，具体见[异地管理](../../console/remote.md)。

## 动态凭据

凭据可能轮换时，通过 `setTokenProvider` 提供当前值：

```java
import java.util.concurrent.atomic.AtomicReference;

AtomicReference<String> token = new AtomicReference<>("your-management-token");
registry.setTokenProvider(token::get);

// 凭据更新后，后续管理调用读取新值
token.set("new-management-token");
```

在加入上下文或创建管理端前设置 provider，凭据更新由应用负责同步到管理端和被管理实例。返回空值或无法读取时，管理认证失败。

## 认证范围

实例管理 token 校验管理 RPC，不限制业务方法权限；直接访问注册存储仍由存储自身的认证和权限控制。Web 登录账号、Telnet 密码和 Remote token 各自配置，不能相互替代。
