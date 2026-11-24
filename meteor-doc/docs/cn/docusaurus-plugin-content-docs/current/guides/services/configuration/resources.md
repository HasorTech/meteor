---
title: "线程和并发"
description: "设置业务线程池、排队容量和消费者在途请求上限。"
---

# 线程和并发

提供者的线程池控制业务处理并发，消费者的在途请求上限控制同时等待响应的请求数。两者都不等于每秒请求速率。

## 业务线程

在提供者创建上下文前设置：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.setQueueMinPoolSize(4);
config.setQueueMaxPoolSize(16);
config.setQueueMaxSize(1024);
config.setQueueKeepAliveTime(300); // 秒
```

默认核心线程数为 `1`，最大线程数为 `4`，队列容量为 `4096`，空闲线程保活时间为 `300` 秒。

## 在途请求

在消费者创建上下文前设置：

```java
import net.hasor.meteor.SendLimitPolicy;
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.setMaximumRequest(200);
config.setSendLimitPolicy(SendLimitPolicy.Reject);
```

默认上限为 `200`，策略为 `Reject`，达到上限时拒绝请求。`WaitSecond` 最多等待一秒获取名额。每秒请求数控制见[请求限流](../../governance/limits.md)。

## 调整参数

按业务耗时、CPU 和下游连接池容量调整线程与并发。队列增大会延长等待时间，需同时观察延迟和超时，不能只比较拒绝数量。使用相同请求负载验证调整效果。
