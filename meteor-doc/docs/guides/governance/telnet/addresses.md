---
title: "addresses：提供者地址"
description: "通过治理 Telnet 查询当前应用已知的提供者地址。"
---

# addresses：提供者地址

此命令仅适用于**治理 Telnet**。

查看当前应用已知的提供者地址，通常在消费者实例中执行。服务 ID 使用 [services](services.md) 的实际输出值。

```text
addresses <serviceId> [--all|--available|--invalid|--local-unit]
```

## 查询地址

```text
addresses '[default]EchoService-1.0.0'
addresses '[default]EchoService-1.0.0' --available
addresses '[default]EchoService-1.0.0' --invalid
addresses '[default]EchoService-1.0.0' --local-unit
```

默认使用 `--all`，查询全部地址。`--available` 查询可用地址，`--invalid` 查询失效地址，`--local-unit` 查询同机房地址；每次选择一种。

输出为 JSON 地址数组。查询范围是当前应用，不是注册中心中的全部实例。
