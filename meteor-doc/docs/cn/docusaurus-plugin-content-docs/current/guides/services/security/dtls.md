---
title: "DTLS"
description: "配置 UDP 的 DTLS 加密、握手超时和数据报大小。"
---

# DTLS

UDP 使用 DTLS 加密数据报，默认版本为 `DTLSv1.2`。证书和信任库选项与 TLS 相同，启用开关同样是 `tlsEnabled=true`。证书文件见[证书准备](certificates.md)。

## 服务端

以下给出 ONC RPC UDP 连接器的安全配置；服务定义和接口文件配置见[ONC RPC](../../protocols/oncrpc.md)。

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("udp").bind("0.0.0.0", 2180)
        .option("tlsEnabled", true)
        .option("tlsKeyStore", "/etc/meteor/server.p12")
        .option("tlsKeyStorePassword", "your-password")
        .option("tlsProtocols", "DTLSv1.2")
        .protocol("oncrpc-udp");
```

## 客户端

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("udp")
        .option("tlsEnabled", true)
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .option("tlsProtocols", "DTLSv1.2")
        .protocol("oncrpc-udp");
```

两端均需支持 DTLS，普通明文 UDP 客户端不能直接访问该监听。需要客户端证书时，按[双向认证](mutual-tls.md)增加证书和 `tlsClientAuth` 配置。

## 握手参数

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| `dtlsHandshakeTimeout` | 沿用连接超时 | 握手等待上限，单位为毫秒 |
| `dtlsRetransmitTimeout` | `1000` | 握手重传间隔，单位为毫秒 |
| `dtlsPacketSize` | `1200` | DTLS 报文大小，范围 `512` 至 `65507` 字节 |
| `maxDatagramSize` | `dtlsPacketSize - 128` | 应用数据报大小上限，不得超过此值 |

## 报文限制

DTLS 保留 UDP 消息边界，不提供 TCP 的可靠有序传输。应用数据报需能装入单个 DTLS 记录；仅增加 RPC 超时不能解决报文过大问题。
