---
title: "自定义 JSON 序列化"
description: "通过 JsonSerializeDelegate 调整当前上下文的业务 JSON 编解码。"
---

# 自定义 JSON 序列化

需要自定义业务对象的 JSON 格式时，实现 `JsonSerializeDelegate`，在启动前交给当前上下文的 JSON 编码器。

## 实现委托

以下以 Jackson 为例，设置业务属性采用下划线命名：

```java
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import net.hasor.meteor.serialize.coder.JsonSerializeDelegate;

public class BusinessJson implements JsonSerializeDelegate {
    private final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Override
    public byte[] encode(Object value) throws IOException {
        return mapper.writeValueAsBytes(value);
    }

    @Override
    public Object decode(byte[] json, Class<?> type) throws IOException {
        return mapper.readValue(json, type);
    }
}
```

## 设置委托

```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.serialize.coder.JsonSerializeCoder;

MetConfiguration config = new MetConfiguration();
try (MetContext context = config.buildContext()) {
    JsonSerializeCoder json = (JsonSerializeCoder) context.getSerializeCoder("Json");
    json.setDelegate(new BusinessJson());
    context.start();
    // 发布或调用使用 Json 编码器的服务
    ...
}
```

## 影响范围

只影响当前上下文中使用该 `JsonSerializeCoder` 的业务数据，不改变注册中心记录、框架内部 JSON 或其他协议自带的编码规则。双方须使用兼容的字段格式。

委托应支持并发调用，使用 UTF-8 编解码；初始化完成后不要再修改共享的 `ObjectMapper`。增加独立编码格式见[扩展序列化方式](serialization.md)。
