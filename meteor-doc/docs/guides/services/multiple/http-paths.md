---
title: "路径挂载"
description: "设置 HTTP 协议前缀和服务路径。"
---

# 路径挂载

HTTP 服务路径由**协议前缀 + 服务子路径**组成，例如 `/jsonrpc` + `/echo` = `/jsonrpc/echo`。

## 协议前缀

`contextPath` 设置协议在 HTTP 端口下的路径前缀：

```java
MetConfiguration config = new MetConfiguration();
var http = config.connector("http");
http.bind("127.0.0.1", 8080);
http.protocol("jsonrpc").contextPath("/jsonrpc");
```

## 服务路径

发布时通过 `contextPathByPrefix` 设置服务子路径：

```java
context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .name("EchoService")
        .protocol("jsonrpc")
        .contextPathByPrefix("jsonrpc", "/echo")
        .register();
```

JSON-RPC 请求地址为 `http://127.0.0.1:8080/jsonrpc/echo`。

`contextPathByPrefix` 按目录前缀匹配；`contextPathByExact` 则精确匹配，仍拼接协议前缀，并区分末尾的 `/`。未指定服务子路径时，使用协议的默认规则。

## 增加前缀

`addContextPath` 增加协议可用的前缀，不改变默认前缀，也不会自动把所有服务发布到新前缀。先配置协议，创建上下文后再发布服务：

```java
config.connector("http")
        .bind("127.0.0.1", 8081)
        .protocol("jsonrpc")
        .contextPath("/")
        .addContextPath("/internal");

context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .name("EchoService")
        .protocol("jsonrpc")
        .contextPathByPrefix("jsonrpc", "/internal/echo")
        .register();
```

这里的默认前缀为 `/`，服务显式发布到 `/internal/echo`。未指定服务路径的其他服务仍使用默认规则。

## 调用地址

部分协议有额外的地址规则。例如 Hessian 挂在 `/hessian/echo` 下时，原生客户端还需追加服务名：`http://127.0.0.1:8080/hessian/echo/EchoService`。最终地址可从控制台的[接入点](../../console/web.md)复制。
