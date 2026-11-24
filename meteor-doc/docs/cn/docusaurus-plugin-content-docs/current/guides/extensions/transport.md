---
title: "扩展网络传输"
description: "实现网络连接器，为 RPC 协议接入新的传输方式。"
---

# 扩展网络传输

需要替换底层网络实现或增加新的传输方式时，实现 `TransportFactory<M>`。`M` 是传输层交给协议层的消息类型，须与协议提供者的 `messageType()` 一致。

## 实现传输工厂

下面是工厂骨架，`ExampleConnector` 是需要自行实现的网络连接器：

```java
import java.util.Map;
import net.hasor.meteor.connector.transport.TransportConfig;
import net.hasor.meteor.connector.transport.TransportFactory;
import net.hasor.meteor.connector.transport.TransportPlan;

public class ExampleTransport implements TransportFactory<byte[]> {
    @Override
    public String name() {
        return "example-net";
    }

    @Override
    public Class<byte[]> messageType() {
        return byte[].class;
    }

    @Override
    public boolean sharedRoutes() {
        return false;
    }

    @Override
    public void configRoutes(Map<String, Map<String, String>> routes) {
        // 在这里校验或补充各协议挂载参数，不创建网络资源
    }

    @Override
    public TransportPlan<byte[]> prepare(TransportConfig config, ClassLoader loader) {
        // 此时只准备计划，真正激活连接器时才分配资源
        return TransportPlan.direct(config,
                (settings, services) -> new ExampleConnector(settings, services));
    }
}
```

`sharedRoutes()` 表示多个消息路由能否共用一条已接入的物理连接，不是简单的“是否支持端口复用”开关。只有确实实现路由隔离时才返回 `true`。

## 实现连接器

`ExampleConnector` 实现 `NetworkConnector<byte[]>`，负责：

- `bind(...)`：创建监听，返回 `NetworkListen`；通过传入的工厂为连接安装接收回调。
- `connect(...)`：建立出站连接，使用 `initial` 和 `RouteTable` 选择协议，返回连接结果的 Future。
- `close()`：释放当前连接器持有的监听、网络和线程资源。

每条连接通过 `NetworkChannel` 提供写入、有序执行、立即关闭和排空后关闭。激活、接收和关闭事件通过 `ChannelListener` 通知上层；接收回调须保持连接内顺序，不能并发打乱消息。

`NetworkListen` 还需实现暂停和恢复接入，以配合应用上下线。`write()` 的完成表示写入完成，不表示远端业务已经执行。

## 注册使用

在 `META-INF/services/net.hasor.meteor.connector.transport.TransportFactory` 中填写工厂类全名：

```text
com.example.rpc.ExampleTransport
```

与它配套的协议提供者须返回 `transportType() = "example-net"`，消息类型为 `byte[]`。假设协议名称为 `example-rpc`，应用配置如下：

```java
MetConfiguration config = new MetConfiguration();
config.connector("example-net").bind("0.0.0.0", 2180).protocol("example-rpc");
```

协议的实现和注册见[扩展 RPC 协议](protocol.md)。仅更换传输不能自动改变已有协议所声明的传输类型。

## 注意事项

`prepare()` 不分配运行资源，资源在计划激活时创建。自定义传输使用 `TransportPlan.direct()` 不会自动执行 `TransportPipeline`；需要此能力时，使用 `TransportPlan.withPipeline()` 并将处理链接入自己的网络实现。

消息写出、连接失败和关闭必须完成对应 Future，关闭前后不能遗留等待请求。外部传入的共享网络组件由其创建者管理，不随连接器一并关闭。
