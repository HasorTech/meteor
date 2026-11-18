# 基础示例

每个入口可以单独运行。示例使用本机随机端口，无需启动外部进程或注册中心。

| 示例 | 入口 |
| --- | --- |
| 发布并调用 Echo | [EchoMain](src/main/java/net/hasor/example/basic/EchoMain.java) |
| 查询和修改本机治理状态 | [LocalGovernanceMain](src/main/java/net/hasor/example/basic/LocalGovernanceMain.java) |
| 传递请求与响应附加信息 | [LocalOptionsDemo](options.md) |

在 IDEA 中运行对应的 `main`，或从 Meteor 仓库根目录运行：

```bash
./gradlew -p met-example :java:meteor:basic:run
./gradlew -p met-example :java:meteor:basic:run -PmainClass=net.hasor.example.basic.LocalGovernanceMain
./gradlew -p met-example :java:meteor:basic:run -PmainClass=net.hasor.example.basic.options.LocalOptionsDemo
```

Echo 输出 `Hello, Meteor!`。治理示例展示服务停用、实例阻断和监听器上下线。
