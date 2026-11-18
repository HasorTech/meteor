---
title: "接入 Nacos"
description: "消费者通过 Nacos 获取已登记的提供者地址。"
mdx:
  format: mdx
---

# 接入 Nacos

消费者通过 Nacos 获取已登记的提供者地址。

## 接入配置

将 Nacos 地址替换为实际集群地址，双方使用相同 namespace 和 group。认证通过 `setUsername`、`setPassword` 配置。

在消费者的 `buildContext()` 前配置注册中心，订阅和调用代码省略。


```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.nacos.NacosRegistryConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用和接入协议
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");

        // 接入注册中心
        NacosRegistryConfiguration registry = new NacosRegistryConfiguration();
        registry.setAddress("nacos.example.com:8848");
        registry.setGroup("meteor-orders");
        config.addExtension(registry);

        config.connector("tcp").protocol("rsf");

        try (MetContext context = config.buildContext()) {
            ...
        }
    }
}
```


## 配置读取超时

在注册配置加入上下文前设置：

```java
registry.setTimeoutMillis(5000);
```

默认 `5000` 毫秒，用于读取配置及配置冲突后的更新重试期限，不是业务 RPC 超时，也不是通用的 Nacos 建连超时。

## 注意事项

- 双方的服务分组、名称和版本须一致。
- 发现为空时，核对注册中心配置、服务坐标和提供者是否运行。
