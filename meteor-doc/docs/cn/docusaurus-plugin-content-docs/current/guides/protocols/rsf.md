---
title: "RSF"
description: "RSF 是 Meteor 内置的二进制 RPC 协议，适合 Meteor Java 应用之间的服务调用。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# RSF

RSF 是 Meteor 内置的二进制 RPC 协议，适合 Meteor Java 应用之间的服务调用。

- 通过 TCP 传输，复用连接。
- 支持请求响应、单向消息、隐式传参，以及同步、异步和回调调用。
- 当前 RSF/1 不支持流式调用。

## 引入依赖

RSF 已包含在内核中，无需额外协议模块。

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
```

</TabItem>
</Tabs>

## 启用协议 {#configuration}

提供者监听端口并提供服务，消费者通过出站协议调用服务。

<Tabs groupId="protocol-provider-configuration">
<TabItem value="code" label="Java">

```java title="提供者"
configuration.connector("tcp")
        .bind("127.0.0.1", 8080)
        .protocol("rsf");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=8080
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("tcp")
        .protocol("rsf");
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
        .protocol("rsf")
        .register();
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("rsf")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
</Tabs>

## 协议配置

- 序列化通过服务发布配置的 `serialize(...)` 选择。内核包含 `Json`（默认）和 `Java`；使用 `Java` 时，传输的对象需实现 `Serializable`。两端需安装所选编码器。
- 请求和建连超时使用应用配置，见[超时和异常](../services/timeouts.md)。

## 注意事项

- 提供者和消费者按分组、服务名、版本匹配服务。
- 历史 RSF 版本的报文格式不能仅凭名称判断兼容，升级前核对[不兼容更新](../../releases/5.x/v5.0.0.md#不兼容更新)。
