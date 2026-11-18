---
title: "接入 ZooKeeper"
description: "消费者通过 ZooKeeper 获取已登记的提供者地址。"
mdx:
  format: mdx
---

# 接入 ZooKeeper

消费者通过 ZooKeeper 获取已登记的提供者地址。

## 接入配置

将 ZooKeeper 地址替换为实际集群连接串，多个节点以逗号分隔；双方使用相同 `root`。

在消费者的 `buildContext()` 前配置注册中心，订阅和调用代码省略。


```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用和接入协议
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");

        // 接入注册中心
        ZookeeperRegistryConfiguration registry = new ZookeeperRegistryConfiguration();
        registry.setAddress("zk.example.com:2181");
        registry.setRoot("/meteor-orders");
        config.addExtension(registry);

        config.connector("tcp").protocol("rsf");

        try (MetContext context = config.buildContext()) {
            ...
        }
    }
}
```


## 认证

ZooKeeper 使用 digest 认证时，在注册配置加入上下文前设置已有账号：

```java
registry.setUsername("meteor-user");
registry.setPassword("your-zookeeper-password");
```

账号需有注册根路径的读写权限，这组凭据不作为实例管理 token。

## 连接超时

```java
registry.setTimeoutMillis(5000);         // 建立连接的等待上限，毫秒
registry.setSessionTimeoutMillis(15000); // 向服务端申请的会话超时，毫秒
```

以上为默认值，均必须大于零，在注册配置加入上下文前设置。会话超时由 ZooKeeper 协商，用于判断会话是否失效，不限制业务 RPC 的执行时间。

## 注意事项

- 双方的服务分组、名称和版本须一致。
- 发现为空时，核对注册中心配置、服务坐标和提供者是否运行。
