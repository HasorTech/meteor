---
title: "services：服务列表"
description: "分别介绍控制台 Telnet 和治理 Telnet 的 services 命令。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# services：服务列表

<Tabs groupId="telnet-entry" defaultValue="console">
<TabItem value="console" label="控制台 Telnet">

列出所选集群中的服务：

```text
services --cluster production
```

执行过 `clusters use production` 后，可省略 `--cluster`。

## 筛选和分页

```text
services --filter EchoService --limit 10
services --filter EchoService --limit 10 --cursor <nextCursor>
```

`--filter` 按服务 ID 筛选；下一页传入返回的 `nextCursor`，保持筛选条件不变。

将结果中的服务 ID 用于 [service](service.md)、[rule](rule.md) 或 [meta](meta.md)。

</TabItem>
<TabItem value="application" label="治理 Telnet">

列出当前应用发布和订阅的业务服务，输出服务 ID、角色和启用状态。

```text
services
```

## 筛选

```text
services --role provider
services --role consumer --filter EchoService
```

`--role` 选择提供者或消费者，`--filter` 按服务 ID 中包含的文本匹配。

## 分页

```text
services --page 2 --limit 10
```

默认第 1 页，每页 20 条；页码从 1 开始，每页最多 100 条。筛选和分页参数可组合使用。

将输出的服务 ID 用于 [service](service.md)、[addresses](addresses.md)、[rule](rule.md) 或 [meta](meta.md) 命令。

</TabItem>
</Tabs>
