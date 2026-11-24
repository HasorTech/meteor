---
title: "无头模式"
description: "关闭 Web，通过 Telnet 管理集群并执行自动化任务。"
---

# 无头模式

无头模式不启动 Web 容器，适合仅需要命令管理或脚本自动化的部署环境。通过 Telnet 仍可查询服务、修改规则、管理服务状态和元信息。

## 启动

创建 `meteor.properties`，按[运行准备](about.md#运行准备)中的命令启动：

```properties
meteor.web.enabled=false
meteor.data.directory=./data/console
meteor.telnet.enabled=true
meteor.telnet.host=127.0.0.1
meteor.telnet.port=2182
meteor.telnet.auth.password=replace-with-your-password
```

## 连接配置

以下 `telnet.*` 参数在 Web 模式下也可使用，无需关闭 Web。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `meteor.telnet.enabled` | `false` | 是否启动 Telnet 监听 |
| `meteor.telnet.host` | `127.0.0.1` | 监听地址 |
| `meteor.telnet.port` | `2182` | 监听端口 |
| `meteor.telnet.auth.password` | 未设置 | 登录密码；环境变量 `METEOR_TELNET_PASSWORD` 优先 |
| `meteor.telnet.auth.max-failures` | `3` | 密码错误次数上限，`-1` 为不限次数 |
| `meteor.telnet.max-sessions` | `8` | 最大连接数 |
| `meteor.telnet.session.max-input-bytes` | `65536` | 单次输入字节数上限，包含多行脚本 |
| `meteor.telnet.session.idle-timeout-millis` | `300000` | 空闲连接超时，单位为毫秒 |

监听非回环地址时必须设置密码；密码错误达到上限后关闭连接。

## 集群连接

在启动前准备 `meteor.data.directory/clusters.json`，也可复制 Web 模式下保存的文件。例如，连接已有 ZooKeeper 集群：

```json title="clusters.json"
[
  {
    "id": "production",
    "name": "生产集群",
    "type": "zookeeper",
    "address": "zk1:2181,zk2:2181,zk3:2181",
    "root": "/meteor"
  }
]
```

`type` 支持 `jdbc`、`nacos`、`zookeeper` 和 `remote`。JDBC 可配置 `database`、`username`、`password`。Nacos 可配置 `namespace`、`group` 和认证信息。Remote 需要被连接端的 `remoteClusterId` 和 `managementToken`。完整示例见[异地管理](remote.md#管理端)。

连接参数应与目标注册中心一致。该文件可能包含凭据，应限制访问权限；直接修改后需重启。数据目录默认是 `~/.meteor/data`，上例显式改为 `./data/console`。

## 管理集群

连接并按提示输入密码：

```bash
telnet 127.0.0.1 2182
```

查看并选择集群，再查询服务：

```text
meteor> clusters
meteor> clusters use production
meteor> services
```

集群选择仅对当前连接有效，重连后需重新选择。自动化任务可在命令中指定 `--cluster production`。治理命令见[使用 Telnet](../governance/telnet/about.md)，自动执行示例见[自动执行](../governance/telnet/about.md#自动执行)。

`status` 查看 Console 运行状态；治理命令返回 `SAVED` 表示注册中心已保存，应用同步后才会生效。通过对应查询命令核对结果。重启 Console 不会停止被管理的业务应用。
