---
title: "扩展 RPC 协议"
description: "在已有 HTTP、TCP 或 UDP 传输上实现服务绑定和 RPC 消息收发。"
---

# 扩展 RPC 协议

需要接入新的 RPC 消息格式时，实现协议提供者。协议负责服务和方法的对应关系、请求响应编码及请求编号，不直接创建网络监听。

## 选择传输

- TCP：实现 `TcpProtocolProvider`，接收字节块，自行处理拆包和粘包。
- UDP：实现 `UdpProtocolProvider`，每次接收完整数据报。
- HTTP：实现 `HttpProtocolProvider`，接收 `HttpExchange`，声明路由标识、支持的 HTTP 版本和接入点用途。

已有传输无法满足时，再[扩展网络传输](transport.md)，通过公共 `ProtocolProvider<M>` 接入。

## 实现协议

以下是 TCP 协议的实现骨架，省略的部分需按实际消息格式完成：

```java
import java.util.function.ObjLongConsumer;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.ProtocolSession;
import net.hasor.meteor.connector.ProtocolSessionContext;
import net.hasor.meteor.connector.RouteProbeResult;
import net.hasor.meteor.connector.transport.NetworkChannel;
import net.hasor.meteor.connector.transport.tcp.TcpProtocolProvider;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.payload.Payload;

public class ExampleProtocol implements TcpProtocolProvider {
    @Override
    public String name() {
        return "example";
    }

    @Override
    public String defaultSerialize() {
        return "Json";
    }

    @Override
    public ProtocolBinding prepareBinding(ProtocolConfig config,
            MetContext context, ProtocolMeta meta) {
        // 校验支持的方法和参数；需要接口文件时在这里解析
        ...
        return meta.build(config, null,
                meta.createServiceDescription(config, defaultSerialize()));
    }

    @Override
    public RouteProbeResult probeProtocol(ProtocolConfig config, byte[] prefix) {
        // 数据不足返回 NEED_MORE，符合本协议返回 MATCH，否则返回 REJECT
        ...
    }

    @Override
    public ProtocolSession<byte[]> createSession(ProtocolConfig config,
            ProtocolSessionContext context, NetworkChannel<byte[]> channel,
            ObjLongConsumer<Payload> messages) {
        // 为当前连接创建独立会话，把解析后的 RPC 消息交给 messages
        ...
    }
}
```

`prepareBinding()` 在服务登记时调用，校验失败应抛出异常，或返回 `null` 拒绝绑定。解析好的协议定义可作为 `meta.build()` 的第二个参数保存，供会话使用。

## 处理会话

`ProtocolSession` 继承 `ChannelListener`，需完成以下行为：

- `connected()`、`ready()`：处理连接激活和协议握手；无需握手时直接完成就绪通知。
- `receive(message)`：解析消息，区分请求和响应，保留请求编号，通过 `messages` 交给 RPC 调度。
- `send(payload)`：把 RPC 消息编码后交给 `channel.write(...)`，传播写入失败。
- `prepareDrain()`、`closed(cause)`：结束协议内待处理的请求，释放会话资源。

发送完成不等于业务执行成功。单向消息的受理通知需遵循协议自身能力，不能凭空增加对端不认识的报文。

## 注册使用

在 `META-INF/services/net.hasor.meteor.connector.ProtocolProvider` 中填写实现类全名：

```text
com.example.rpc.ExampleProtocol
```

应用挂载协议后，服务发布方式不变：

```java
MetConfiguration config = new MetConfiguration();
config.connector("tcp").bind("0.0.0.0", 2180).protocol("example");
```

消费者使用同一协议模块，配置不带 `bind(...)` 的出站连接器。双方挂载名称须对应协议的 `name()`。

## 注意事项

协议提供者应支持并发调用，连接状态放在各自会话中。TCP、UDP 的入站多协议监听依赖协议探测，探测不能消费数据；HTTP 通过路径选路，不使用字节前缀探测。新增协议至少要验证请求、响应、异常和连接关闭，不能只验证 SPI 能加载。
