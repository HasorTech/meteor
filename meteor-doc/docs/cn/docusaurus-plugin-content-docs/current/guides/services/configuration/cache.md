---
title: "地址缓存"
description: "配置地址刷新、磁盘缓存和失效等待时间。"
---

# 地址缓存

地址缓存用于保存已知的提供者地址，磁盘缓存可在应用重启后恢复地址信息。缓存中的地址不保证仍可连接。

## 分清地址来源

静态地址由应用显式配置；注册发现地址由扩展刷新；本地磁盘缓存用于恢复已有地址信息。它们不代表相同的更新和失效机制。

## 配置缓存

```java
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.setLocalDiskCache(true);
config.setDiskCacheTimeInterval(3600000);
config.setRefreshCacheTime(60000);
config.setInvalidWaitTime(120000);
```

时间单位为毫秒。默认关闭磁盘缓存；默认写入间隔为 `3600000`，刷新间隔为 `60000`，失效等待时间为 `120000`。数据保存在[数据目录](data-home.md)中。

## 发生故障时

注册中心连接异常时，观察存储日志和消费者实际地址。已缓存地址可能仍可连接，也可能已失效，不能据此承诺固定时间内的调用可用性。

使用 `queryAllAddresses`、`queryAvailableAddresses`、`queryInvalidAddresses` 比较状态。静态地址不会随发现刷新自动移除，应由应用维护。

恢复后检查目录和实际调用是否一致。不要通过删除正在使用的数据目录来强制刷新，避免破坏实例身份或目录锁。
