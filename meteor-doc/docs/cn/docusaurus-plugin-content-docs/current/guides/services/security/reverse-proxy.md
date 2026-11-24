---
title: "反向代理"
description: "按 HTTP、gRPC、TCP、UDP 协议族配置 Nginx，支持明文、TLS 和 DTLS 转发。"
---

# 反向代理

Nginx 为 Meteor 提供统一访问地址，并可处理 TLS 加密和证书校验。

## 支持范围

- [HTTP](#http)：JSON-RPC、XML-RPC、SOAP、Hessian、Hprose、Connect、gRPC-Web、Thrift HTTP（`thrift-http`）。
- [gRPC](#grpc)：gRPC、Triple，使用 HTTP/2。
- [TCP](#tcp)：RSF、Dubbo、Thrift TCP（`thrift`）、ONC RPC TCP。
- [UDP](#udp)：ONC RPC UDP（`oncrpc-udp`），可透传 DTLS。
- [WebSocket](#websocket)：JSON-RPC WebSocket。

HTTP、gRPC、TCP 可使用明文、TLS 透传或代理端终止 TLS；终止后可选择明文或 TLS 回源。

以下配置适用于 Nginx 1.28.0，`http`、`stream` 块位于 `nginx.conf` 顶层。按需启用 HTTP SSL、HTTP/2、gRPC、stream、stream SSL 模块，用 `nginx -V` 检查。示例地址需替换为实际地址。

## 证书准备

代理端终止 TLS 时，将 PEM 证书链和私钥部署为 `/etc/nginx/certs/server.crt`、`/etc/nginx/certs/server.key`，证书须匹配客户端访问域名。

从[PKCS12 文件](certificates.md)导出私钥：

```bash
openssl pkcs12 -in server.p12 -nocerts -nodes -out server.key \
  -passin pass:your-password
chmod 600 server.key
```

加密回源时，`/etc/nginx/certs/upstream-ca.crt` 存放签发 Meteor 服务端证书的 CA 证书；自签名证书则放入对应证书。服务端证书的 SAN 须匹配配置中的 `rpc.example.com`。

## HTTP

以挂载到 `/jsonrpc` 的 [JSON-RPC](../../protocols/jsonrpc.md) 为例，其他 HTTP 协议替换 `location` 路径。gRPC-Web 回源连接 Meteor 的 `grpc-web` 接入点。

### 明文转发

```nginx
http {
    server {
        listen 8081;
        server_name rpc.example.com;

        location /jsonrpc/ {
            # 不附加路径，保留原始请求路径
            proxy_pass http://127.0.0.1:8080;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header Connection "";
        }
    }
}
```

### 加密转发

两段连接均加密，Meteor 先[启用 HTTPS](https.md)，监听 `9443`：

```nginx
http {
    server {
        listen 8443 ssl;
        server_name rpc.example.com;
        http2 on;

        ssl_certificate /etc/nginx/certs/server.crt;
        ssl_certificate_key /etc/nginx/certs/server.key;
        ssl_protocols TLSv1.2 TLSv1.3;

        location /jsonrpc/ {
            # 仅入口加密：改用 http://127.0.0.1:8080，删除下方四项 proxy_ssl_*
            proxy_pass https://127.0.0.1:9443;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header Connection "";

            # 校验 Meteor 服务端证书，并使用该名称发送 SNI
            proxy_ssl_verify on;
            proxy_ssl_trusted_certificate /etc/nginx/certs/upstream-ca.crt;
            proxy_ssl_server_name on;
            proxy_ssl_name rpc.example.com;
        }
    }
}
```

本例入口支持 HTTP/1.1、HTTP/2，回源使用 HTTP/1.1，详见[ALPN 协商](https.md#alpn-协商)。回源证书名称由 `proxy_ssl_name` 指定，与 HTTP `Host` 无关。

## gRPC

[gRPC](../../protocols/grpc.md)、[Triple](../../protocols/triple.md) 使用 `grpc_pass`，Meteor 监听端需启用 HTTP/2。

### 明文转发

```nginx
http {
    server {
        listen 8081;
        http2 on;

        location / {
            grpc_pass grpc://127.0.0.1:8080;
        }
    }
}
```

### 加密转发

Meteor 启用 TLS，`tlsAlpn` 须包含 `h2`：

```nginx
http {
    server {
        listen 8443 ssl;
        server_name rpc.example.com;
        http2 on;

        ssl_certificate /etc/nginx/certs/server.crt;
        ssl_certificate_key /etc/nginx/certs/server.key;
        ssl_protocols TLSv1.2 TLSv1.3;

        location / {
            # 仅入口加密：改用 grpc://127.0.0.1:8080，删除下方四项 grpc_ssl_*
            grpc_pass grpcs://127.0.0.1:9443;

            # gRPC 回源使用 grpc_ssl_*，而非 proxy_ssl_*
            grpc_ssl_verify on;
            grpc_ssl_trusted_certificate /etc/nginx/certs/upstream-ca.crt;
            grpc_ssl_server_name on;
            grpc_ssl_name rpc.example.com;
        }
    }
}
```

流式调用按需设置 `grpc_read_timeout`、`grpc_send_timeout`，控制相邻读写操作的等待时间，不限制整次 RPC 时长。

## TCP

使用 `stream` 按连接转发，不解析 RPC 内容。

### 明文转发

```nginx
stream {
    server {
        listen 2181;
        proxy_pass 127.0.0.1:2180;
        proxy_connect_timeout 5s;
        proxy_timeout 10m;
    }
}
```

### TLS 透传

客户端与 Meteor [启用 TLS](tls.md)，Nginx 沿用明文转发配置，回源指向 TLS 端口，不配置 `listen ssl` 或代理证书。HTTP、gRPC 透传也使用此配置。

ALPN、证书校验由客户端与 Meteor 完成，Meteor 证书须匹配客户端访问代理的域名；Nginx 不按 URL 分流。

### 加密转发

Nginx 终止客户端 TLS，以 TLS 连接 Meteor：

```nginx
stream {
    server {
        listen 2444 ssl;
        ssl_certificate /etc/nginx/certs/server.crt;
        ssl_certificate_key /etc/nginx/certs/server.key;
        ssl_protocols TLSv1.2 TLSv1.3;

        # 仅入口加密：回源改为明文端口 2180，删除下方五项 proxy_ssl*
        proxy_pass 127.0.0.1:2443;
        proxy_connect_timeout 5s;
        proxy_timeout 10m;

        # 开启回源 TLS 并校验证书
        proxy_ssl on;
        proxy_ssl_verify on;
        proxy_ssl_trusted_certificate /etc/nginx/certs/upstream-ca.crt;
        proxy_ssl_server_name on;
        proxy_ssl_name rpc.example.com;
    }
}
```

## UDP

使用 `stream`，监听时指定 `udp`：

```nginx
stream {
    server {
        listen 5001 udp reuseport;
        proxy_pass 127.0.0.1:5000;
        proxy_timeout 1m;  # 应大于允许的会话空闲时间，避免中途更换回源会话
    }
}
```

Meteor 在 `5000` 端口监听 `oncrpc-udp`；两端[启用 DTLS](dtls.md)后，同一配置透传加密报文。Nginx 不配置 `listen ssl` 或 `proxy_ssl`，Meteor 证书须匹配客户端访问代理的域名。

## WebSocket

Meteor 的 JSON-RPC 接入点设置 `upgrade=websocket`，Nginx 转发升级请求头：

```nginx
http {
    server {
        listen 8081;

        location /jsonrpc/ {
            proxy_pass http://127.0.0.1:8080;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_read_timeout 10m;
        }
    }
}
```

WSS 按 [HTTP](#http) 配置监听证书和回源加密，保留 `Upgrade`、`Connection` 请求头。

## 检查配置

```bash
nginx -t
nginx -s reload
```

首次启动执行 `nginx`；客户端连接代理地址。遇到 `502` 时，检查 Nginx 错误日志。

指令说明见 Nginx 官方文档：[HTTP 代理](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)、[gRPC 代理](https://nginx.org/en/docs/http/ngx_http_grpc_module.html)、[TCP/UDP 代理](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html)、[TCP TLS](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html)、[WebSocket](https://nginx.org/en/docs/http/websocket.html)。
