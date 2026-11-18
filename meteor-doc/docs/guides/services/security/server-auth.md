---
title: "单向认证"
description: "让客户端校验服务端证书和连接目标的主机身份。"
---

# 单向认证

单向认证由服务端提供证书，客户端验证该证书是否受信任、是否有效，以及是否与连接目标匹配。服务端不要求客户端提供证书。TLS、HTTPS 和 DTLS 均可使用这种方式。

## 服务端

以 HTTPS 为例，服务端加载自己的私钥和证书。`tlsClientAuth=NONE` 是默认值，表示不要求客户端证书：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http").bind("0.0.0.0", 8443)
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("tlsClientAuth", "NONE")
        .option("tlsKeyStore", "/etc/meteor/server.p12")
        .option("tlsKeyStorePassword", "your-password")
        .protocol("jsonrpc").addressScheme("https");
```

## 客户端

客户端无需配置自己的私钥，只需信任服务端证书的签发者。开发测试证书可按[证书准备](certificates.md)导入信任库：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http")
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .protocol("jsonrpc").addressScheme("https");
```

未配置 `tlsTrustStore` 时使用 JVM 默认信任配置；显式配置信任库时，使用该库中的证书。服务端应提供完整证书链，客户端通常信任签发它的 CA。

## 主机校验

Meteor 根据实际连接地址校验服务端身份：

- 连接 `rpc.example.com` 时，服务端证书应包含对应的 DNS SAN。
- 直接连接 `127.0.0.1` 时，证书应包含该 IP 的 SAN。
- `0.0.0.0` 是监听地址，不能作为客户端的目标地址。

注册中心发布的主机名也应与证书匹配。即使两种地址指向同一台机器，域名和 IP 也不能随意替换。

## 校验失败

信任链错误时，检查信任库和证书链；主机不匹配时，修正连接地址或重新签发证书；证书过期时，更新证书并重启应用。服务端需要验证客户端身份时，改用[双向认证](mutual-tls.md)。
