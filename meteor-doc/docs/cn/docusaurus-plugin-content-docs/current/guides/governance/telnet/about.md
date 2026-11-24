---
title: "使用 Telnet"
description: "选择管理入口，连接认证，通过命令查询状态和执行治理。"
---

# 使用 Telnet

Telnet 可以连接业务应用管理当前实例，也可以连接 Meteor Console 管理集群。

## 选择入口

- **治理 Telnet**：连接业务应用，管理当前实例。依赖、启用代码和配置见[接入治理 Telnet](connect.md)。
- **控制台 Telnet**：连接 Console，管理所选集群。启动和监听配置见[无头模式](../../console/headless.md)，Web 模式也可同时开启 Telnet。

## 连接和认证

连接目标的 Telnet 监听地址和端口：

```bash
telnet 127.0.0.1 2182
```

配置了密码时，按 `Password:` 提示输入密码，认证通过后显示 `meteor>`。Console 的 Web 登录账号不用于 Telnet 认证。

```text
meteor> help
meteor> help rule
```

`help` 查看命令列表，`help <命令名>` 查看具体语法。同名命令的差异在各命令页面通过 Tab 区分。

## 控制台 Telnet 的语言

新连接默认使用英文。执行 `lang` 查看当前语言，使用 `lang zh-CN` 切换到简体中文，使用 `lang en-US` 切回英文。设置仅对当前连接生效，重新连接后恢复英文。

```text
meteor> lang zh-CN
OK  当前语言：zh-CN
meteor> help lang
```

Console 命令的帮助、提示和参数错误按所选语言显示。自动化脚本仍可读取 `OK`、`SAVED`、`ERROR` 和 JSON 字段。

## 命令指引

### 查看信息

- [status](status.md)：查看当前连接的运行状态。
- [services](services.md)：查询服务列表。
- [applications](applications.md)：查询应用，仅控制台 Telnet 支持。
- [instances](instances.md)：查询实例，仅控制台 Telnet 支持。
- [addresses](addresses.md)：查询当前应用已知的提供者地址，仅治理 Telnet 支持。

控制台 Telnet 先使用 [clusters](clusters.md) 选择集群，也可在查询或治理命令中指定 `--cluster <集群ID>`。

### 执行治理

- [service](service.md)：查看详情，启用或停用服务。
- [instance](instance.md)：阻断或恢复请求；治理 Telnet 还可关闭或开启业务监听。
- [rule](rule.md)：查看、校验和修改治理规则。
- [meta](meta.md)：查询、设置和删除元信息。

服务 ID、实例 ID 使用查询结果中的实际值，包含空格的参数用引号括起。

## 自动执行


自动化任务可使用 tconsole 的 `TelClient`，连接和密码认证完成后执行对应入口支持的命令：

```java
import java.net.InetSocketAddress;
import net.hasor.tconsole.client.TelClient;

try (TelClient client = new TelClient()) {
    client.setPassword("your-password");
    client.connectTo(new InetSocketAddress("127.0.0.1", 2182));
    System.out.println(client.sendCommand("status"));
}
```
