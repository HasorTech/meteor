# Meteor 示例

先按语言选择目录，再进入对应框架或 SDK。有多个运行方式或兼容版本时，在该目录下按场景或版本分别查找。跨语言示例共用的协议契约位于 [resource](resource/)。

<a id="run"></a>

## 快速开始

准备 JDK 17，在 IDEA 中独立打开 `met-example` 并同步 Gradle。示例工程引用当前仓库的 Meteor 源码。[Java 基础示例](java/meteor/basic/README.md) 的 Echo 在同一进程中启动两个上下文，通过真实 RSF 连接调用。

```bash
./gradlew -p met-example :java:meteor:basic:run
```

<a id="service-examples"></a>

## 按语言浏览

| 语言 | 示例 |
| --- | --- |
| Java | [Meteor、应用框架和 Java SDK](java/README.md) |
| JavaScript | [Node.js 和浏览器](javascript/README.md) |
| Python | [gRPC、Thrift、SOAP、JSON-RPC、XML-RPC](python/README.md) |
| Go | [gRPC、Connect、Thrift](go/README.md) |
| C | [ONC RPC](c/README.md) |
| C++ | [ONC RPC](cpp/README.md) |
| Shell | [JSON-RPC](shell/README.md) |

<a id="framework-integration"></a>

## Java 框架接入

每个示例自带提供者和消费者。先启动提供者，再运行消费者，输出 `Hello, Meteor!`。[框架示例入口](java/README.md#基础与框架)

<a id="management-api"></a>

## 注册中心查询

[Nacos](java/meteor/interop/src/main/java/net/hasor/example/NacosRegistryAdminMain.java) 和 [ZooKeeper](java/meteor/interop/src/main/java/net/hasor/example/ZookeeperRegistryAdminMain.java) 示例查询实例和服务目录。运行前启动对应的注册中心。
