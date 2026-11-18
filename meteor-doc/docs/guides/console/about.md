---
title: "使用控制台"
description: "按 Web、无头和异地管理场景部署 Meteor Console。"
---

# 使用控制台

Meteor Console 用于查看应用和服务、调试 RPC 调用，以及管理服务状态、路由、限流和元信息。可以使用 Web 界面，也可以通过 Telnet 执行管理命令。

## 选择场景

- [Web 模式](web.md)：通过浏览器连接集群，查看服务、调试调用和修改治理配置。
- [无头模式](headless.md)：不启动 Web 容器，通过 Telnet 查询和管理集群，适合命令运维和自动化任务。
- [异地管理](remote.md)：目标网络不能开放 HTTP、Telnet 管理端口时，通过管理 RPC 端口连接远端 Console。

## 运行准备

准备 JDK 17 或以上版本，以及 `meteor-console-@project.lastReleaseVer@.jar` 运行包。按所选场景创建 `meteor.properties`，然后启动：

```bash
java -jar meteor-console-@project.lastReleaseVer@.jar \
  --config=/absolute/path/meteor.properties
```

配置文件必须存在。`--config` 指定配置文件，也可使用 JVM 参数 `-Dconfig=/absolute/path/meteor.properties`；不指定时读取 `~/.meteor/meteor.properties`。

前台运行时按 `Ctrl+C` 停止，由进程管理工具运行时发送 `SIGTERM`。修改配置后重启生效，迁移时保留配置文件和 `data.directory` 目录。

## 使用指引

- [使用 Telnet](../governance/telnet/about.md)：连接认证、选择集群、执行查询和治理命令；Web 模式下同样可用。
