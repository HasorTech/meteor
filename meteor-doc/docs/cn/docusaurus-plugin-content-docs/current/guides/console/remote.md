---
title: "异地管理"
description: "HTTP、Telnet 端口不能对外开放时，通过管理 RPC 端口连接远端 Console。"
---

# 异地管理

当安全策略不允许目标网络开放 HTTP、Telnet 管理端口时，可仅开放 Console 的管理 RPC 端口，由远程 Console 通过 Remote 连接进行管理。

```text
管理端 Console ──集群 ID 和管理凭据──→ 被连接端 Console → 本机已保存的注册中心
```

被连接端按请求中的集群 ID 查找本机已保存的连接。管理端为每个远端集群保存一条 Remote 连接。

## 被连接端

先在被连接端的页面添加并保存注册中心。在「集群连接」列表找到该集群，点击「复制集群 ID」。管理 RPC 与 Web 读取同一数据目录中的 `clusters.json`。需要关闭 Web 和 Telnet 时，可在保存后用以下配置重启。

```properties
# 不启动网页，仅开放管理 RPC
meteor.web.enabled=false
meteor.telnet.enabled=false
meteor.data.directory=./data/console

# 提供给管理端的监听地址和凭据
meteor.rpc.enabled=true
meteor.rpc.host=0.0.0.0
meteor.rpc.port=19193
meteor.rpc.token=replace-with-your-token
```

按[运行准备](about.md#运行准备)中的命令启动。管理端连接地址使用这台服务器的实际地址，例如 `rsf://console-east:19193`。目标网络仅需向管理端开放该管理 RPC 端口，无需开放 HTTP 或 Telnet 端口。

升级时，两端需使用支持集群 ID 的 Console。在被连接端保存原先的注册中心连接，在管理端编辑已有的 Remote 连接并补填远端集群 ID。

## 管理端

按[Web 模式](web.md)启动管理端 Console，在顶部集群入口新建连接，选择 **Remote**：

- RPC 地址填 `rsf://console-east:19193`。
- 远端集群 ID 填被连接端页面复制的 ID。
- 管理凭据填被连接端的 `meteor.rpc.token`。

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
    "remoteClusterId": "replace-with-saved-cluster-id",
    "managementToken": "replace-with-your-token"
  }
]
```

启动后，通过 Telnet 执行 `clusters use east` 选择它。

## 配置参数

以下参数配置在被连接端：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `meteor.rpc.enabled` | `false` | 启用管理 RPC 监听 |
| `meteor.rpc.host` / `meteor.rpc.port` | `127.0.0.1` / `19193` | 管理 RPC 监听地址和端口 |
| `meteor.rpc.unit` | `default` | 管理 RPC 所属单元 |
| `meteor.rpc.token` | 未设置 | 必填；用于认证管理端，可由环境变量 `METEOR_REGISTRY_MANAGEMENT_TOKEN` 覆盖 |

## 访问范围

管理令牌持有者可按 ID 访问被连接端已保存的集群。删除集群连接后，该 ID 的管理请求会返回未找到。管理端查看业务实例详情或发起调试调用时，仍需能访问业务实例的相应接入点。

Web 登录密码、Telnet 密码和 Remote 管理令牌分别配置。
