---
title: "对接 Dubbo 注册生态"
description: "消费者通过 Dubbo 原生注册数据发现已有服务。"
mdx:
  format: mdx
---

# 对接 Dubbo 注册生态

消费者通过 Dubbo 原生注册数据发现已有服务。

## 接入配置

以 ZooKeeper 为例，将 `zookeeper://zk.example.com:2181` 替换为已有 Dubbo 注册中心 URL。

在消费者的 `buildContext()` 前配置注册中心，订阅和调用代码省略。


```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.dubbo.DubboRegistryConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用和接入协议
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");

        // 接入注册中心
        DubboRegistryConfiguration registry = new DubboRegistryConfiguration();
        registry.setAddress("zookeeper://zk.example.com:2181");
        config.addExtension(registry);

        config.connector("tcp").protocol("dubbo");

        try (MetContext context = config.buildContext()) {
            ...
        }
    }
}
```


## 元数据查询加密

发现 Dubbo 服务时，可能需要调用对方的元数据服务。对方为元数据 RPC 启用 TLS 时，在注册配置加入上下文前设置信任证书：

```java
registry.setMetadataTrustCertificate("/etc/meteor/dubbo-ca.pem");

// 元数据服务要求双向认证时，再提供客户端证书和私钥
registry.setMetadataClientCertificate("/etc/meteor/metadata-client.pem");
registry.setMetadataClientPrivateKey("/etc/meteor/metadata-client-key.pem");
registry.setMetadataPrivateKeyPassword("your-private-key-password");
```

证书和私钥使用 PEM 文件；私钥未加密时省略密码。这组配置仅用于 Dubbo 元数据查询，业务 RPC 的 TLS 仍在 Meteor 连接器上配置，见 [TLS](../services/security/tls.md)。

## 注意事项

- 订阅时通过 `aliasName` 指定对方的 Dubbo 接口名，并保持服务分组、版本及方法签名一致。
- 未设置分组、版本的原生 Dubbo 服务可通过 `context.getExtension(DubboRegistryClient.class).reference(EchoService.class)` 引用；此时接口全限定名应与提供者一致。
- 此模块使用 Dubbo 原生数据格式，与 [Meteor 的 ZooKeeper 登记](zookeeper.md)不同。业务协议配置见 [Dubbo](../protocols/dubbo.md)。
