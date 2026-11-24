---
title: "TLS"
description: "为 TCP 协议配置 TLS 加密和两端连接。"
---

# TLS

TLS 在 TCP 连接上加密数据并校验证书，不改变服务接口和调用方式。下面以 RSF 为例；证书文件见[证书准备](certificates.md)，证书校验细节见[单向认证](server-auth.md)。

## 服务端

在创建上下文前，为监听连接器开启 TLS：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp").bind("0.0.0.0", 2180)
        .option("tlsEnabled", true)
        .option("tlsKeyStore", "/etc/meteor/server.p12")
        .option("tlsKeyStorePassword", "your-password")
        .protocol("rsf");
```

`tlsKeyStore` 必须包含服务端私钥和证书链。服务发布方式不变，见[RSF](../../protocols/rsf.md)。

## 客户端

出站连接器不调用 `bind`，启用 TLS 并加载客户端信任库：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp")
        .option("tlsEnabled", true)
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .protocol("rsf");
```

通过注册中心或固定地址连接服务端。RSF 的连接地址仍可使用 `rsf://rpc.example.com:2180`，不能仅根据地址前缀判断是否加密；两端都需配置 `tlsEnabled=true`。

## 协议版本

`tlsProtocols` 默认允许 `TLSv1.3,TLSv1.2`，以逗号分隔。需要限制为 TLS 1.3 时，在两端连接器中设置：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("tcp")
        .option("tlsEnabled", true)
        .option("tlsProtocols", "TLSv1.3")
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .protocol("rsf");
```

此段为客户端出站配置。两端需有共同支持的版本；不受当前 JDK 支持的版本会导致初始化失败。

HTTP 协议见[HTTPS](https.md)，UDP 协议见[DTLS](dtls.md)。选项需配置在连接器上，不能放入 `protocol(...).option(...)`。
