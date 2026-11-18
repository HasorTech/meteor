---
title: "clusters：集群选择"
description: "通过控制台 Telnet 使用 clusters 命令。"
---

# clusters：集群选择

此命令仅适用于**控制台 Telnet**。

列出 Console 已保存的集群连接：

```text
clusters
```

## 选择集群

使用列表中的集群 ID：

```text
clusters use production
```

选择只对当前 Telnet 连接生效，重连后需重新选择。后续查询和治理命令也可显式指定 `--cluster production`，不会改变当前选择。

## 清除选择

```text
clusters clear
```

此命令不删除已保存的连接。新增连接可通过 [Web 界面](../../console/web.md#连接集群)，或在启动前准备 [clusters.json](../../console/headless.md#集群连接)。
