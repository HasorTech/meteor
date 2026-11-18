---
title: "异地管理"
description: "HTTP、Telnet 端口不能对外开放时，通过管理 RPC 端口连接远端 Console。"
---

# 异地管理

当安全策略不允许目标网络开放 HTTP、Telnet 管理端口时，可仅开放 Console 的管理 RPC 端口，由远程 Console 通过 Remote 连接进行管理。

```text
管理端 Console ──管理 RPC 端口──→ 被连接端 Console → 注册中心
```

被连接端关闭 Web 和 Telnet，通过 `storage.*` 连接注册中心。管理端使用 Remote 地址和管理令牌连接它，用户在管理端使用网页或 Telnet 执行管理操作。

## 被连接端

以 ZooKeeper 为例，创建 `meteor.properties`：

```properties
# 不启动网页，仅开放管理 RPC
console.web.enabled=false
telnet.enabled=false
data.directory=./data/console

# 连接目标注册中心
storage.type=zookeeper
storage.address=zk1:2181,zk2:2181,zk3:2181
storage.root=/meteor

# 提供给管理端的监听地址和凭据
rpc.host=0.0.0.0
rpc.port=19193
management.token=replace-with-your-token
```

按[运行准备](about.md#运行准备)中的命令启动。管理端连接地址使用这台服务器的实际地址，例如 `rsf://console-east:19193`。目标网络仅需向管理端开放该管理 RPC 端口，无需开放 HTTP 或 Telnet 端口。

`storage.*` 决定向 Remote 客户端提供哪个注册中心，与 Web、Telnet 使用的 `clusters.json` 分别配置，不会自动导出其中的全部集群。

## 管理端

按[Web 模式](web.md)启动管理端 Console，在顶部集群入口新建连接，选择 **Remote**：

- 地址：`rsf://console-east:19193`。
- 管理凭据：被连接端的 `management.token`。

测试成功后保存并进入该集群：

- “应用列表”和“服务目录”查看远端注册中心中的应用、实例及服务。
- “路由策略”“流量控制”和“元信息”管理当前服务，“全局配置”管理集群级配置。

界面操作与直连注册中心相同，页面示例见[Web 模式](web.md#查看服务)。

管理端使用无头模式时，在数据目录的 `clusters.json` 中保存同样的连接：

```json title="clusters.json"
[
  {
    "id": "east",
    "name": "东部集群",
    "type": "remote",
    "address": "rsf://console-east:19193",
    "managementToken": "replace-with-your-token"
  }
]
```

启动后，通过 Telnet 执行 `clusters use east` 选择它。

## 配置参数

以下参数配置在被连接端：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `storage.type` | `jdbc` | 注册中心类型：`jdbc`、`nacos`、`zookeeper` 或 `remote` |
| `storage.address` | 未设置 | 注册中心地址；设置后启用管理 RPC |
| `storage.database` | `h2` | JDBC 数据库类型 |
| `storage.username` / `storage.password` | 未设置 | 注册中心认证信息 |
| `storage.namespace` | 未设置 | Nacos 命名空间 |
| `storage.group` | `meteor` | Nacos 分组 |
| `storage.root` | `/meteor` | ZooKeeper 根路径 |
| `storage.managementToken` | 未设置 | 目标注册中心的管理凭据 |
| `rpc.host` / `rpc.port` | `127.0.0.1` / `19193` | 管理 RPC 监听地址和端口 |
| `rpc.unit` | `default` | 管理 RPC 所属单元 |
| `management.token` | 未设置 | 必填；用于认证管理端，可由环境变量 `METEOR_REGISTRY_MANAGEMENT_TOKEN` 覆盖 |

## 访问范围

Remote 转发注册中心管理操作，不提供通用网络代理。管理端查看业务实例详情或发起调试调用时，仍需能访问业务实例的相应接入点。

Web 登录密码、Telnet 密码和 Remote 管理令牌分别配置。
