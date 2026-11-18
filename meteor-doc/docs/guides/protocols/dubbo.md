---
title: "Dubbo"
description: "Dubbo 二进制协议通过 TCP 传输请求，常用于 Java 服务之间调用。Meteor 可发布供 Apache Dubbo 客户端调用的服务，也可调用已有 Dubbo 服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Dubbo

Dubbo 二进制协议通过 TCP 传输请求，常用于 Java 服务之间调用。Meteor 可发布供 Apache Dubbo 客户端调用的服务，也可调用已有 Dubbo 服务。

- 支持请求响应、单向调用和 Dubbo attachments。
- 默认使用 Hessian2 序列化。
- 不支持流式调用；需要流式能力时使用 [Triple](triple.md)。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-dubbo</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-dubbo:@project.lastReleaseVer@'
```

</TabItem>
</Tabs>

## 启用协议 {#configuration}

提供者监听端口并提供服务，消费者通过出站协议调用服务。

<Tabs groupId="protocol-provider-configuration">
<TabItem value="code" label="Java">

```java title="提供者"
configuration.connector("tcp")
        .bind("0.0.0.0", 8080)
        .protocol("dubbo");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.dubbo.host=0.0.0.0
meteor.connectors.dubbo.port=8080
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("tcp")
        .protocol("dubbo");
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
        .protocol("dubbo")
        .aliasName("dubbo", "meteor.example.EchoService")
        .register();
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("dubbo")
        .aliasName("dubbo", "meteor.example.EchoService")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("dubbo", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`org.apache.dubbo:dubbo:3.3.6`。

```java title="消费者"
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

// 配置消费者应用
ApplicationConfig application = new ApplicationConfig("echo-consumer");
application.setQosEnable(false);
DubboBootstrap bootstrap = DubboBootstrap.getInstance()
        .application(application)
        .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));

// 声明服务引用和直连地址
ReferenceConfig<EchoService> reference = new ReferenceConfig<>();
reference.setInterface(EchoService.class); // 本地代理接口
reference.setInterface("meteor.example.EchoService"); // 提供者的 Dubbo 服务名
reference.setGroup("default");
reference.setVersion("1.0.0");
reference.setUrl("dubbo://127.0.0.1:8080/meteor.example.EchoService?serialization=hessian2");
try {
    // 启动客户端并调用服务
    bootstrap.reference(reference)
            .start();
    EchoService client = reference.get();
    System.out.println(client.echo("hello"));
} finally {
    // 关闭客户端
    bootstrap.destroy();
}
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="准备客户端"
go mod init example.com/dubbo-client
go get dubbo.apache.org/dubbo-go/v3@v3.3.2
```

```go title="消费者"
package main
import (
    "context"
    "fmt"
    "dubbo.apache.org/dubbo-go/v3/client"
    "dubbo.apache.org/dubbo-go/v3/common/constant"
    "dubbo.apache.org/dubbo-go/v3/common/extension"
    _ "dubbo.apache.org/dubbo-go/v3/cluster/cluster/failfast"
    _ "dubbo.apache.org/dubbo-go/v3/cluster/loadbalance/random"
    _ "dubbo.apache.org/dubbo-go/v3/filter/echo"
    _ "dubbo.apache.org/dubbo-go/v3/filter/graceful_shutdown"
    _ "dubbo.apache.org/dubbo-go/v3/protocol/dubbo"
    _ "dubbo.apache.org/dubbo-go/v3/proxy/proxy_factory"
)

func main() {
    // 配置直连地址、服务分组和版本
    consumer, err := client.NewClient(client.WithClientProtocolDubbo(),
        client.WithClientURL("dubbo://127.0.0.1:8080"),
        client.WithClientGroup("default"), client.WithClientVersion("1.0.0"),
        client.WithClientSerialization(constant.Hessian2Serialization),
        client.WithClientClusterFailFast(), client.WithClientLoadBalanceRandom(),
        client.WithClientFilter("echo"))
    if err != nil { panic(err) }
    defer extension.GetProtocol("dubbo").Destroy()

    // 声明服务接口和方法
    info := &client.ClientInfo{InterfaceName: "meteor.example.EchoService", MethodNames: []string{"echo"}}
    connection, err := consumer.DialWithInfo(info.InterfaceName, info)
    if err != nil { panic(err) }

    // 调用服务并读取返回值
    var result string
    if err := connection.CallUnary(context.Background(), []any{"hello"}, &result, "echo"); err != nil { panic(err) }
    fmt.Println(result)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="准备客户端"
npm install dubbo2.js@2.3.9
```

```javascript title="消费者"
import dubbo from "dubbo2.js";
const {DirectlyDubbo, java} = dubbo;

// 配置直连地址和服务坐标
const client = DirectlyDubbo.from({dubboAddress: "127.0.0.1:8080", dubboVersion: "2.0.2", dubboInvokeTimeout: 5});
const service = client.proxyService({
    dubboInterface: "meteor.example.EchoService", group: "default", version: "1.0.0",
    methods: {echo(text) { return [java.String(text)]; }},
});

// 调用服务；此 SDK 没有关闭入口，独立示例进程在输出后退出
const result = await service.echo("hello");
if (result.err) throw result.err;
process.stdout.write(result.res + "\n", () => process.exit(0));
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.dubbo.options.<名称>`。

- `heartbeatInterval`：默认 `60000` 毫秒。
- `heartbeatTimeout`：心跳超时；`maxFrameSize`：单帧大小上限。
- `maxPendingRequests`：限制尚未完成的请求数量。

## 注意事项

- 接口全名、参数类型、分组、版本和序列化方式必须匹配。
- Java 对象类型名称也可能参与解码，不是字段相同就能兼容。
- 使用 Dubbo 注册数据时需引入注册适配模块，见[Dubbo](../discovery/dubbo.md)。

原生 Dubbo SDK 可能将回环地址替换为本机 IP，因此示例提供者监听 `0.0.0.0`；跨机器调用时使用提供者实际可达的 IP。
