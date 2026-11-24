---
title: "优雅上下线"
description: "业务准备完成后上线，退出前等待在途工作完成并关闭上下文。"
---

# 优雅上下线

默认情况下，`context.start()` 会同时开启业务监听。需要先预热资源或完成业务初始化时，可关闭自动上线。

## 准备后上线

在创建上下文前设置 `setAutomaticOnline(false)`：

```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.setAutomaticOnline(false);

// 由应用持有上下文，退出时关闭
MetContext context = config.buildContext();
context.start(); // 完成启动，暂不开启业务监听

// 在此完成业务准备，发布或订阅服务
context.online(); // 开启业务监听
```

`context.isOnline()` 查询业务监听是否在线，`start()` 完成不代表已上线。

## 临时下线

```java
context.offline(); // 停止业务监听，开始关闭已有连接
context.online();  // 重新开启业务监听
```

下线保留上下文，可再次上线；`offline()` 不等待所有连接和业务执行结束。

## 退出应用

先从负载均衡等入口摘除实例，停止提交新业务任务，等待应用自行跟踪的在途任务完成，再关闭监听和上下文：

```java
// 已停止新任务，并等待在途业务完成
context.offline();
context.close(); // 释放上下文持有的资源，关闭后不可再次启动
```

原生 Java 应用由创建方关闭上下文；Spring、Hasor 和 Solon 整合由对应容器关闭。业务任务的等待和外部入口摘除由应用负责，不能以 `offline()` 返回作为业务已完成的依据。
