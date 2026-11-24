---
title: "扩展序列化方式"
description: "实现并注册命名编码器，为支持选择编码格式的协议增加序列化方式。"
---

# 扩展序列化方式

协议允许选择序列化方式时，可以实现 `SerializeCoder` 增加编码器。下面注册一个名为 `business-json` 的编码器，与内置 `Json` 分开使用。

## 实现编码器

```java
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import net.hasor.meteor.serialize.SerializeCoder;

public class BusinessJsonCoder implements SerializeCoder {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    public String name() {
        return "business-json";
    }

    @Override
    public byte[] encode(Object value) throws IOException {
        return mapper.writeValueAsBytes(value);
    }

    @Override
    public Object decode(byte[] bytes, Class<?> type) throws IOException {
        return bytes == null ? null : mapper.readValue(bytes, type);
    }
}
```

需要业务类加载器时，可覆盖 `initCoder(ClassLoader)`；框架会在注册编码器前调用它。

## 注册编码器

在资源文件 `META-INF/services/net.hasor.meteor.serialize.SerializeCoder` 中填写实现类全名。假设类位于 `com.example.rpc` 包：

```text
com.example.rpc.BusinessJsonCoder
```

打包时保留 SPI 声明。名称忽略大小写，重复名称会导致加载失败。

## 选择编码器

提供者发布服务、消费者订阅服务时使用相同名称。以下 `context` 为各自的上下文：

```java title="提供者"
context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .serialize("business-json")
        .register();
```

```java title="消费者"
context.publisher().rsfService(EchoService.class)
        .serialize("business-json")
        .register();
EchoService echo = context.getRsfClient().wrapper(EchoService.class);
System.out.println(echo.echo("hello"));
```

## 注意事项

编码器须支持并发使用，两端相同名称必须对应兼容的字节格式。Protobuf、Thrift 等使用固定消息格式的协议不能靠 `serialize(...)` 更换线上格式；只替换已有 JSON 实现见[自定义 JSON 序列化](json.md)。
