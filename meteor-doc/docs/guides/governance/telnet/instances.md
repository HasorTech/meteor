---
title: "instances：实例列表"
description: "通过控制台 Telnet 使用 instances 命令。"
---

# instances：实例列表

此命令仅适用于**控制台 Telnet**。

列出所选集群中的实例：

```text
instances --cluster production
```

## 按应用查询

```text
instances --application order-app
```

`--application` 使用 [applications](applications.md) 返回的应用名称。

## 筛选和分页

```text
instances --filter 10.0.0.10 --limit 10
instances --filter 10.0.0.10 --limit 10 --cursor <nextCursor>
```

下一页传入返回的 `nextCursor`，保持筛选条件不变。将结果中的实例 ID 用于 [instance](instance.md) 或其他命令的 `--instance` 参数。
