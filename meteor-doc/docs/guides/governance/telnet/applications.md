---
title: "applications：应用列表"
description: "通过控制台 Telnet 使用 applications 命令。"
---

# applications：应用列表

此命令仅适用于**控制台 Telnet**。

列出所选集群中的应用：

```text
applications --cluster production
```

执行过 `clusters use production` 后，可省略 `--cluster`。

## 筛选和分页

```text
applications --filter order --limit 10
applications --filter order --limit 10 --cursor <nextCursor>
```

`--filter` 筛选应用名称，`--limit` 设置返回条数；下一页传入返回的 `nextCursor`，保持筛选条件不变。

查看应用下的实例使用 [instances](instances.md)。
