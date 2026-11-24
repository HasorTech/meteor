---
title: "端口复用"
description: "让多个协议共用一个端口，或分别监听。"
---

# 端口复用

多个协议可共用一个监听端口，也可分别监听。以下使用 JSON-RPC 和 Hessian：

```groovy
implementation 'net.hasor:meteor-protocol-jsonrpc:@project.lastReleaseVer@'
implementation 'net.hasor:meteor-protocol-hessian:@project.lastReleaseVer@'
```

## 共享端口

在同一个连接器中配置两个协议，共用 `8080`，按路径区分请求：

```java
MetConfiguration config = new MetConfiguration();
var http = config.connector("http");
http.bind("127.0.0.1", 8080);
http.protocol("jsonrpc").contextPath("/jsonrpc");
http.protocol("hessian").contextPath("/hessian");
```

## 独立端口

分别监听 `8080` 和 `8081`，通过端口区分协议，无需额外设置协议路径前缀：

```java
MetConfiguration config = new MetConfiguration();
config.connector("http").bind("127.0.0.1", 8080).protocol("jsonrpc");
config.connector("http").bind("127.0.0.1", 8081).protocol("hessian");
```

服务发布方式不变，见[多协议发布](about.md)。HTTP 路径配置见[路径挂载](http-paths.md)。TCP、UDP 端口能否复用，取决于协议插件是否支持根据请求数据识别协议。
