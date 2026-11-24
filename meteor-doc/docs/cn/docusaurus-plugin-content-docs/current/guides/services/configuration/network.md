---
title: "网络配置"
description: "设置本机监听地址和提供给消费者的连接地址。"
---

# 网络配置

监听地址决定服务绑定本机哪张网卡，发布地址决定消费者连接哪个主机。两者可以不同，例如本机监听 `0.0.0.0`，向注册中心发布消费者可访问的域名。

## 监听地址

在创建上下文前配置连接器：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp").bind("0.0.0.0", 2180).protocol("rsf");
```

`0.0.0.0` 监听本机多个 IPv4 接口，不能作为消费者连接目标。测试时可用端口 `0` 自动分配，固定部署应指定端口。多个协议的端口安排见[端口复用](../multiple/ports.md)。

## 发布地址

使用注册中心时，在加入扩展前设置发布主机。以下以 ZooKeeper 为例：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp").bind("0.0.0.0", 2180).protocol("rsf");

ZookeeperRegistryConfiguration registry = new ZookeeperRegistryConfiguration();
registry.setAddress("zk1:2181,zk2:2181,zk3:2181");
registry.setPublishHost("rpc.internal.example");
config.addExtension(registry);
```

发布主机必须从消费者网络可达。`setPublishHost` 只修改主机，不会自动修改 NAT 或代理映射的端口。

## 代理转发

代理需转发对应协议的连接和路径：gRPC 使用 HTTP/2，WebSocket 需要升级转发，浏览器跨域请求需要 CORS。SOAP 对外生成的接口文件地址可通过 `publicBaseUrl` 调整，具体见[SOAP](../../protocols/soap.md)。

部署后从消费者环境检查域名解析和端口连接，再核对注册中心公布的地址。
