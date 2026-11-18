---
title: "双向认证"
description: "让服务端校验客户端证书，两端同时配置身份和信任。"
---

# 双向认证

双向认证在[单向认证](server-auth.md)的基础上，由服务端再校验客户端证书。适合只允许持有指定证书的应用接入，所需文件见[证书准备](certificates.md)。

## 服务端

服务端同时加载自己的密钥库和信任客户端的信任库：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http").bind("0.0.0.0", 8443)
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("tlsKeyStore", "/etc/meteor/server.p12")
        .option("tlsKeyStorePassword", "your-password")
        .option("tlsTrustStore", "/etc/meteor/server-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .option("tlsClientAuth", "NEED")
        .protocol("jsonrpc").addressScheme("https");
```

## 客户端

客户端除信任服务端外，还需提供自己的证书和私钥：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http")
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("tlsKeyStore", "/etc/meteor/client.p12")
        .option("tlsKeyStorePassword", "your-password")
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .protocol("jsonrpc").addressScheme("https");
```

## 认证方式

`tlsClientAuth` 配置在服务端连接器：

- `NONE`：默认值，不要求客户端证书。
- `WANT`：请求客户端证书；未提供时仍允许连接，提供时需校验通过。
- `NEED`：必须提供受信任的客户端证书，否则握手失败。

TCP 和 DTLS 使用同样的证书和认证选项，开启方式分别见[TLS](tls.md)和 [DTLS](dtls.md)。验证时分别使用受信任证书、未受信任证书和未配置证书的客户端，确认只有符合规则的连接成功。证书认证控制连接接入，业务方法权限仍需由应用处理。
