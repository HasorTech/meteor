---
title: "扩展连接处理链"
description: "通过 TransportPipeline 在已有网络传输中加入自定义处理步骤。"
---

# 扩展连接处理链

只需在现有传输中观察或处理连接数据时，使用 `TransportPipeline`，无需重新实现 RPC 协议或网络连接器。以下为 TCP 连接统计接收字节数，不改变数据。

## 编写处理器

```java
import net.hasor.neta.bytebuf.ByteBuf;
import net.hasor.neta.channel.ProtoContext;
import net.hasor.neta.channel.ProtoHandler;
import net.hasor.neta.channel.ProtoStatus;
import net.hasor.neta.channel.data.ProtoRcvQueue;
import net.hasor.neta.channel.data.ProtoSndQueue;

public class ReceivedBytesHandler implements ProtoHandler<ByteBuf, ByteBuf> {
    private long receivedBytes;

    @Override
    public ProtoStatus onMessage(ProtoContext context,
            ProtoRcvQueue<ByteBuf> source, ProtoSndQueue<ByteBuf> target) {
        while (source.hasMore() && target.hasSlot()) {
            ByteBuf bytes = source.takeMessage();
            receivedBytes += bytes.readableBytes();
            target.offerMessage(bytes); // 将原数据交给下一个处理器
        }
        return ProtoStatus.Next;
    }

    @Override
    public void onClose(ProtoContext context) {
        System.out.println("连接累计接收字节数：" + receivedBytes);
    }
}
```

## 安装处理器

```java
import net.hasor.meteor.connector.transport.TransportConfig;
import net.hasor.meteor.connector.transport.TransportPipeline;
import net.hasor.neta.channel.ProtoBuildContext;

public class ReceivedBytesPipeline implements TransportPipeline {
    @Override
    public void initialize(TransportConfig config, ProtoBuildContext stack) {
        stack.addLastDecoder("received-bytes", new ReceivedBytesHandler());
    }
}
```

连接器启动时创建处理链提供者，每条连接分别调用 `initialize()`。需要区分客户端和服务端时，使用 `stack.getChannel().isClient()`。

## 配置使用

```java
MetConfiguration config = new MetConfiguration();
config.connector("tcp")
        .bind("0.0.0.0", 2180)
        .option("transportPipeline", ReceivedBytesPipeline.class.getName())
        .protocol("rsf");
```

使用 Spring 等框架加载配置文件时，也可指定类名，类位于 `com.example.rpc` 包时：

```properties
meteor.connectors.rsf.host=0.0.0.0
meteor.connectors.rsf.port=2180
meteor.connectors.rsf.options.transportPipeline=com.example.rpc.ReceivedBytesPipeline
```

## 注意事项

- 提供者类和无参构造方法必须为 `public`；此入口使用类名配置，不通过 SPI 注册。
- 当前内置 Socket 传输按安全处理、自定义处理链、协议处理的顺序组装，接收数据按该顺序处理，发送方向相反。启用 TLS 时，此处观察到的是解密后的数据。
- 提供者由同一传输的连接共享，有状态的处理器须在 `initialize()` 中为每条连接单独创建。
- 不阻塞网络处理线程。不再向后传递的 `ByteBuf` 由当前处理器释放；已经传递的对象不能再次释放。
