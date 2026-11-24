# 博客配套示例

每篇文章对应一个可独立运行的 `main`。示例使用回环地址和随机端口，启动和关闭均在同一进程内完成。

| 文章 | 源码 | 运行结果 |
| --- | --- | --- |
| [从点对点调用到注册中心发现服务](https://www.hasor.net/cn/blog/meteor/direct-to-discovery/) | [DiscoveryMain](src/main/java/net/hasor/example/blog/DiscoveryMain.java) | 固定地址和内存注册中心发现均返回 `Hello, Meteor!` |
| [多协议共享 HTTP 端口的路径配置](https://www.hasor.net/cn/blog/meteor/http-protocol-paths/) | [HttpPathsMain](src/main/java/net/hasor/example/blog/HttpPathsMain.java) | 请求 `/rpc/public`，返回 JSON-RPC 结果 `Meteor` |
| [一个 Java 服务如何接入多种协议](https://www.hasor.net/cn/blog/meteor/one-service-many-protocols/) | [MultipleProtocolsMain](src/main/java/net/hasor/example/blog/MultipleProtocolsMain.java) | 同一端口的 JSON-RPC 和 Hprose 分别返回 `Meteor` |

从 Meteor 仓库根目录运行：

```bash
./gradlew -p met-example :java:meteor:blog:run
./gradlew -p met-example :java:meteor:blog:run -PmainClass=net.hasor.example.blog.HttpPathsMain
./gradlew -p met-example :java:meteor:blog:run -PmainClass=net.hasor.example.blog.MultipleProtocolsMain
```
