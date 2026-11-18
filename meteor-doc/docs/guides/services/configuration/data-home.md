---
title: "数据目录"
description: "设置实例身份和地址缓存的保存目录，隔离多实例运行数据。"
---

# 数据目录

数据目录保存实例身份、目录锁和地址磁盘缓存。默认使用 `~/.meteor/应用名`，普通应用不必显式设置。

## 指定目录

在创建上下文前设置：

```java
import java.nio.file.Path;
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.setApplicationName("order-service");
config.setDataHome(Path.of("/var/lib/meteor/order-service-1"));
```

目录必须可写。同一目录同时只能由一个上下文使用；同机运行多个实例时，分别指定目录。Spring、Hasor 和 Solon 项目可使用 `meteor.data-home`，配置入口见[创建应用](../../application/overview.md)。

## 重启和迁移

复用原目录重启可保留实例 ID。迁移实例时先停止原进程，再迁移数据目录；创建新实例应使用独立目录。不要删除正在使用的目录来刷新地址，地址缓存设置见[地址缓存](cache.md)。
