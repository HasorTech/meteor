---
title: "HTTPS"
description: "为 HTTP 协议启用 TLS，配置安全地址和 HTTP/2。"
---

# HTTPS

HTTPS 是通过 TLS 传输 HTTP 请求，适用于 JSON-RPC、Hessian、gRPC 等 HTTP 协议。以下以 JSON-RPC 为例，使用[证书准备](certificates.md)中的文件。

## 服务端

同时启用 TLS 和 HTTPS 地址：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http").bind("0.0.0.0", 8443)
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("tlsAlpn", "h2,http/1.1")
        .option("tlsKeyStore", "/etc/meteor/server.p12")
        .option("tlsKeyStorePassword", "your-password")
        .protocol("jsonrpc").addressScheme("https");
```

服务接口和发布方式不变。客户端使用服务实际公布的 HTTPS 地址，例如 `https://rpc.example.com:8443/jsonrpc/echo`；具体路径由协议挂载和服务路径决定，见[路径挂载](../multiple/http-paths.md)。

## 客户端

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http")
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("httpVersion", 1)
        .option("tlsAlpn", "http/1.1")
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .protocol("jsonrpc").addressScheme("https");
```

`httpScheme=https` 和协议的 `addressScheme("https")` 用于安全地址，`tlsEnabled=true` 才会开启连接加密。原生 SDK 客户端也需配置证书信任，具体调用方式见[协议支持](../../protocols/about.md)。

## 主机名校验

Meteor 客户端启用 TLS 后，会自动校验服务端证书的信任链、有效期和主机身份，无需另开认证开关。校验目标取自实际连接地址中的主机名。

例如访问 `https://rpc.example.com:8443/...`，证书的 SAN（使用者备用名称）应包含 `DNS:rpc.example.com`；访问 `https://10.0.0.10:8443/...`，证书应包含 `IP:10.0.0.10`。将证书导入信任库只解决信任问题，不会取消主机名校验。

### 域名和 IP

证书只有域名，但服务实际部署在某个 IP 时，将域名通过 DNS 或 hosts 解析到该 IP，调用地址仍使用域名。使用注册中心发现服务时，公布的主机名也应与证书匹配。

监听地址 `0.0.0.0` 不参与客户端的身份校验，不能用作调用目标。主机不匹配时，应修正调用地址或重新签发包含目标域名、IP 的证书。

### Host 和 SNI

- HTTP `Host` 请求头用于请求路由，在 TLS 握手后发送。修改它不能改变证书校验目标。
- SNI 在 TLS 握手中告知服务端要访问的域名，帮助服务端选择证书。Meteor 使用连接目标的域名发送 SNI，直接连接 IP 时不发送域名 SNI。
- 证书主机名校验仍检查连接目标。SNI、HTTP `Host` 和证书信任是不同环节。

反向代理终止 TLS 时，客户端校验代理提供的证书；代理到后端若也使用 HTTPS，需由代理独立校验后端证书。

### 验证证书

使用测试证书检查域名是否匹配：

```bash
openssl s_client -connect rpc.example.com:8443 \
  -servername rpc.example.com -verify_hostname rpc.example.com \
  -verify_return_error -CAfile server.crt </dev/null
```

`-servername` 设置 SNI，`-verify_hostname` 才是主机名校验。直接检查 IP 时改用 `-verify_ip 10.0.0.10`。证书生成和信任库准备见[证书准备](certificates.md)。

## ALPN 协商

ALPN 在 TLS 握手时协商 HTTP 版本：`h2` 表示 HTTP/2，`http/1.1` 表示 HTTP/1.1。

- `tlsAlpn`：连接器在 TLS 握手中支持的协议，多个值用逗号分隔。
- `httpVersion`：Meteor 客户端实际使用的 HTTP 版本；未指定时沿用所选 RPC 协议或连接器的默认值，连接器默认为 `1`。

两项需匹配。仅设置 `httpVersion=2` 不能保证协商成功，仅配置 `tlsAlpn=h2` 也不会将 HTTP/1.1 客户端切换为 HTTP/2。

### 服务端

上面的监听配置使用 `tlsAlpn=h2,http/1.1`，同一端口可按协商结果接收两种版本。只需要 HTTP/2 时设置 `tlsAlpn=h2`，只需要 HTTP/1.1 时设置 `tlsAlpn=http/1.1`。

服务端按每条连接的实际 ALPN 结果选择 HTTP 处理方式；`httpVersion` 用于出站连接的版本选择，不替代服务端的 ALPN 配置。

### 客户端

上面的客户端示例使用 HTTP/1.1。改用 HTTP/2 时，设置 `httpVersion=2` 和 `tlsAlpn=h2`：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.connector("http")
        .option("tlsEnabled", true)
        .option("httpScheme", "https")
        .option("httpVersion", 2)
        .option("tlsAlpn", "h2")
        .option("tlsTrustStore", "/etc/meteor/client-trust.p12")
        .option("tlsTrustStorePassword", "your-password")
        .protocol("jsonrpc").addressScheme("https");
```

HTTP/2 客户端必须协商出 `h2`，否则关闭连接，不会自动降级为 HTTP/1.1。客户端不要同时声明 `h2,http/1.1` 后假定会自动切换。gRPC 等要求 HTTP/2 的协议也必须满足此条件。

### 反向代理

代理终止 TLS 时，入口和回源连接分别协商 HTTP 版本，前端协商为 HTTP/2 不表示后端也是 HTTP/2。Nginx 的明文和加密转发示例见[反向代理](reverse-proxy.md)。

### 协商失败

- 出现 `HTTP/2 requires ALPN h2`：检查客户端是否发送 `h2`，TLS 服务端或代理是否支持并选择 `h2`。
- TLS 握手因无共同应用协议而失败：核对两端 `tlsAlpn`，例如只支持 `h2` 与只支持 `http/1.1` 的配置无法匹配。
- 未协商 ALPN：Meteor 服务端按 HTTP/1.1 处理，不能据此发送 HTTP/2 请求。

使用[证书准备](certificates.md)中的测试证书时，可检查监听端实际的协商结果：

```bash
openssl s_client -connect rpc.example.com:8443 \
  -servername rpc.example.com -alpn h2 -verify_hostname rpc.example.com \
  -verify_return_error -CAfile server.crt </dev/null
```

HTTP/2 应显示 `ALPN protocol: h2`。这个检查只验证 TLS 和 ALPN，RPC 是否可用仍需执行实际调用。

## 证书认证

客户端默认校验服务端，见[单向认证](server-auth.md)。需要服务端校验客户端证书时，使用[双向认证](mutual-tls.md)。不要用跳过证书校验来解决信任链或主机名错误。
