---
title: "证书准备"
description: "准备 PKCS12 密钥库和信任库，用于 TLS 和双向认证。"
---

# 证书准备

密钥库存放本端私钥和证书链，信任库存放允许信任的对端证书或 CA 证书。默认文件格式为 PKCS12。

- [单向认证](server-auth.md)：服务端需要密钥库，客户端需要信任服务端证书。
- [双向认证](mutual-tls.md)：两端都需要密钥库和信任库。

## 服务端证书

已有签发证书时，使用包含私钥和完整证书链的 PKCS12 文件。下面用 JDK 自带的 `keytool` 生成开发测试证书；实际连接域名或 IP 应包含在证书的 SAN 中。

```bash
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore server.p12 \
  -storepass "your-password" \
  -dname "CN=rpc.example.com" -validity 365 \
  -ext "SAN=dns:rpc.example.com,ip:127.0.0.1"

keytool -exportcert -rfc -alias server -keystore server.p12 \
  -storepass "your-password" -file server.crt
```

将服务端证书导入客户端信任库：

```bash
keytool -importcert -noprompt -alias server -file server.crt \
  -storetype PKCS12 -keystore client-trust.p12 \
  -storepass "your-password"
```

使用 CA 签发证书时，信任库通常导入对应 CA 证书。未指定 `tlsTrustStore` 时，使用 JVM 默认信任配置。

## 客户端证书

单向 TLS 不需要客户端证书；启用[双向认证](mutual-tls.md)时再准备：

```bash
keytool -genkeypair -alias client -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore client.p12 \
  -storepass "your-password" \
  -dname "CN=order-consumer" -validity 365

keytool -exportcert -rfc -alias client -keystore client.p12 \
  -storepass "your-password" -file client.crt

keytool -importcert -noprompt -alias client -file client.crt \
  -storetype PKCS12 -keystore server-trust.p12 \
  -storepass "your-password"
```

服务端部署 `server.p12` 和 `server-trust.p12`；客户端部署 `client.p12` 和 `client-trust.p12`。下文示例将文件放在各自的 `/etc/meteor/` 目录。

## 加载文件

`tlsKeyStore`、`tlsTrustStore` 支持文件路径和 `classpath:` 资源，例如 `classpath:certs/server.p12`。服务端监听启用加密时必须提供密钥库；私钥密码默认沿用密钥库密码，也可通过 `tlsKeyPassword` 单独设置。

已有 JKS 文件时，在对应连接器上指定实际格式：

```java
config.connector("tcp")
        .option("tlsKeyStore", "/etc/meteor/server.jks")
        .option("tlsKeyStorePassword", "your-password")
        .option("tlsKeyStoreType", "JKS")
        .option("tlsTrustStore", "/etc/meteor/trust.jks")
        .option("tlsTrustStorePassword", "your-password")
        .option("tlsTrustStoreType", "JKS");
```

这段只指定文件及格式；启用加密见 [TLS](tls.md)、[HTTPS](https.md)或 [DTLS](dtls.md)。

检查文件内容：

```bash
keytool -list -v -keystore server.p12 -storepass "your-password"
```

确认密钥库中包含私钥条目、证书未过期，且访问地址与服务端证书的 SAN 匹配。
