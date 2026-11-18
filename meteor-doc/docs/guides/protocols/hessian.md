---
title: "Hessian"
description: "Hessian 是基于 HTTP 的二进制 RPC 协议，可传输对象和集合，适合对接使用 Hessian 的 Java 服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Hessian

Hessian 是基于 HTTP 的二进制 RPC 协议，可传输对象和集合，适合对接使用 Hessian 的 Java 服务。

- 支持普通请求响应和 Hessian 对象编码。
- 可通过服务接口创建原生客户端代理。
- 不支持单向消息或流式调用。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-hessian</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-hessian:@project.lastReleaseVer@'
```

</TabItem>
</Tabs>

## 启用协议 {#configuration}

提供者监听端口并提供服务，消费者通过出站协议调用服务。

<Tabs groupId="protocol-provider-configuration">
<TabItem value="code" label="Java">

```java title="提供者"
configuration.connector("http")
        .bind("127.0.0.1", 8080)
        .protocol("hessian").contextPath("/hessian");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.hessian.host=127.0.0.1
meteor.connectors.hessian.port=8080
meteor.connectors.hessian.context=/hessian
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("hessian")
        .contextPath("/hessian");
```

## 发起 RPC 调用

在已启动的 `MetContext` 中发布 `EchoService`，客户端调用 `echo`，输出 `hello`。

```java title="服务定义"
import net.hasor.meteor.annotation.MetParam;

public interface EchoService {
    String echo(@MetParam(id = 1) String text);
}

public class EchoServiceImpl implements EchoService {
    public String echo(String text) { return text; }
}
```

```java title="提供者"
context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .name("meteor.example.EchoService")
        .protocol("hessian")
        .aliasName("hessian", "echo")
        .register();
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("hessian")
        .aliasName("hessian", "echo")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("hessian", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`com.caucho:hessian:4.0.63`。

```java title="消费者"
import com.caucho.hessian.client.HessianProxyFactory;

// 配置 Hessian 编码
var factory = new HessianProxyFactory();
factory.setHessian2Request(true);
factory.setHessian2Reply(true);

// 通过服务地址创建代理
var client = (EchoService) factory.create(EchoService.class,
        "http://127.0.0.1:8080/hessian/default/1.0.0/echo");

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="准备客户端"
python -m pip install python-hessian==1.2.0 six
```

```python title="消费者"
from pyhessian.client import HessianProxy

# 创建 Hessian 2 客户端
client = HessianProxy("http://127.0.0.1:8080/hessian/default/1.0.0/echo", version=2)

# 调用服务
print(client.echo("hello"))
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="准备客户端"
npm install hessian-proxy@0.0.5
```

```javascript title="消费者"
import proxy from "hessian-proxy";

// 创建客户端
const client = new proxy.Proxy("http://127.0.0.1:8080/hessian/default/1.0.0/echo");

// 发起调用并处理结果
client.invoke("echo", ["hello"], (error, result) => {
    if (error) throw error;
    console.log(result);
});
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.hessian.options.<名称>`。

- `version`：默认 `2`，可选 `1`、`2`，需与客户端一致。
- 服务别名通过发布配置的 `aliasName("hessian", "echo")` 设置。

## 注意事项

- 原生客户端 URL 包含服务名，如 `/hessian/default/1.0.0/echo`。
- 两端接口、对象类型名称、属性和集合映射需兼容。
- 旧版 SDK 在新 JDK 上可能有反射访问限制，应按实际异常检查客户端依赖。
