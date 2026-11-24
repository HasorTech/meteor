---
title: "Hprose"
description: "Hprose 是支持多语言的 RPC 协议，使用自己的值编码传输参数和返回值。Meteor 提供 HTTP 接入。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Hprose

Hprose 是支持多语言的 RPC 协议，使用自己的值编码传输参数和返回值。Meteor 提供 HTTP 接入。

- 支持 HTTP 请求响应和 Hprose 值编码。
- 支持通过函数名调用，并将对象、集合等值映射到客户端类型。
- 不支持 `Flow.Publisher<T>` 流式方法。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-hprose</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-hprose:@project.lastReleaseVer@'
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
        .protocol("hprose").contextPath("/hprose");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.hprose.host=127.0.0.1
meteor.connectors.hprose.port=8080
meteor.connectors.hprose.context=/hprose
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("hprose")
        .contextPath("/hprose");
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
        .protocol("hprose")
        .aliasName("Hprose", "echo")
        .register();
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("hprose")
        .aliasName("Hprose", "echo")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("hprose", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`org.hprose:hprose-java:2.0.38`。

```java title="消费者"
import hprose.client.HproseHttpClient;
import hprose.util.concurrent.Threads;

// 创建客户端
var client = new HproseHttpClient("http://127.0.0.1:8080/hprose/default/1.0.0");
try {
    // 获取服务代理并调用
    EchoService echo = client.useService(EchoService.class, "echo");
    System.out.println(echo.echo("hello"));
} finally {
    // 关闭客户端
    client.close();
    Threads.runShutdownHandler();
}
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="准备客户端"
python -m pip install hprose==1.4.3
```

```python title="消费者"
from hprose import HproseHttpClient

# 创建客户端
client = HproseHttpClient("http://127.0.0.1:8080/hprose/default/1.0.0")

# 调用服务
print(client.echo_echo("hello"))
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="准备客户端"
go mod init example.com/hprose-client
go get github.com/hprose/hprose-golang@v2.0.6+incompatible
```

```go title="消费者"
package main
import (
    "fmt"
    "github.com/hprose/hprose-golang/rpc"
)

func main() {
    // 创建客户端，退出时关闭连接
    client := rpc.NewHTTPClient("http://127.0.0.1:8080/hprose/default/1.0.0")
    defer client.Close()
    var service struct {
        Echo func(string) (string, error) `name:"echo_echo"`
    }
    client.UseService(&service)

    // 调用服务
    result, err := service.Echo("hello")
    if err != nil { panic(err) }
    fmt.Println(result)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="安装依赖"
npm install hprose@2.0.51
```

```javascript title="消费者"
import hprose from "hprose";

// 连接服务并加载方法
const client = hprose.Client.create("http://127.0.0.1:8080/hprose/default/1.0.0");
const service = await client.ready();

// 调用服务
console.log(await service.echo_echo("hello"));
```

</TabItem>
<TabItem value="browser" label="JavaScript">

```bash title="准备客户端"
npm install hprose-html5@2.0.36
```

通过前端构建工具打包运行；跨域时在提供者配置 `corsOrigins`。

```javascript title="消费者"
import hprose from "hprose-html5";

// 创建客户端并声明方法
const client = hprose.Client.create("http://127.0.0.1:8080/hprose/default/1.0.0", ["echo_echo"]);

// 调用服务
client.echo_echo("hello").then(result => console.log(result));
```

</TabItem>
</Tabs>

## 协议配置

- HTTP 路径通过 `contextPath` 设置。
- 服务别名通过 `aliasName("Hprose", "echo")` 设置；原生函数名例如 `echo_echo`。

## 注意事项

- Hprose 函数名和报文格式不能与 JSON-RPC 混用。
- 对象、日期、字节和引用的映射需与客户端 SDK 一致。
