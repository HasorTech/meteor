---
title: "安全通信"
description: "按通信方式和认证要求配置 TLS、HTTPS、DTLS 及管理凭据。"
---

# 安全通信

安全通信包括连接加密和身份认证。TLS 用于 TCP，HTTPS 是 HTTP 上的 TLS，DTLS 用于 UDP；单向认证由客户端校验服务端，双向认证还要求服务端校验客户端。

## 使用指引

- [证书准备](certificates.md)：准备私钥、证书、密钥库和信任库。
- [TLS](tls.md)：为 TCP 协议启用加密，配置两端连接和 TLS 版本。
- [HTTPS](https.md)：为 HTTP 协议启用 TLS，设置 HTTPS 地址及 HTTP/2 参数。
- [DTLS](dtls.md)：为 UDP 启用加密，设置握手和报文限制。
- [单向认证](server-auth.md)：客户端验证服务端证书、信任链和主机名。
- [双向认证](mutual-tls.md)：两端提供证书并相互验证身份。
- [管理认证](management.md)：通过 token 校验实例管理调用，支持动态凭据。
- [反向代理](reverse-proxy.md)：按协议族配置 Nginx，支持明文、TLS 和 DTLS 转发。

加密选项在创建 `MetContext` 前配置到连接器上。证书文件在连接器初始化时读取，更新后需重启应用。证书和 token 认证不替代业务权限控制，业务权限由应用或[拦截器](../filters.md)处理。
