---
title: "Thrift"
description: "Thrift 使用接口定义文件描述方法和消息，可生成多种语言的客户端，适合已有 Thrift 系统接入。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Thrift

Thrift 使用接口定义文件描述方法和消息，可生成多种语言的客户端，适合已有 Thrift 系统接入。

- 支持 TCP 和 HTTP，协议名分别为 `thrift`、`thrift-http`。
- 支持 Binary、Compact、JSON 编码及 framed、unframed 传输。
- 支持普通请求响应和 oneway，不支持流式调用。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-thrift</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-thrift:@project.lastReleaseVer@'
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
        .protocol("thrift");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.thrift.host=127.0.0.1
meteor.connectors.thrift.port=8080
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("tcp")
        .protocol("thrift");
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
        .protocol("thrift")
        .aliasName("thrift", "EchoService")
        .register();
```

<a id="interface-file"></a>

客户端使用 `echo.thrift` 生成代码：

```thrift title="echo.thrift"
namespace java example.generated
namespace py echo
namespace go echo

service EchoService { string echo(1: string text) }
```

使用 Thrift 0.25.0 编译器生成客户端代码。

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("thrift")
        .aliasName("thrift", "EchoService")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("thrift", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

```bash title="生成客户端代码"
mkdir -p src/main/java
thrift --gen java -out src/main/java echo.thrift
```

客户端依赖：`org.apache.thrift:libthrift:0.25.0`、`org.slf4j:slf4j-api:1.7.36`。

```java title="消费者"
import example.generated.EchoService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;

// 建立 Framed 连接
try (var transport = new TFramedTransport(new TSocket("127.0.0.1", 8080))) {
    transport.open();

    // 创建 Binary 协议客户端
    var client = new EchoService.Client(new TBinaryProtocol(transport));

    // 调用服务；离开 try 后自动关闭连接
    System.out.println(client.echo("hello"));
}
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="生成客户端代码"
python -m pip install thrift==0.25.0
thrift --gen py -out . echo.thrift
```

```python title="消费者"
from thrift.protocol import TBinaryProtocol
from thrift.transport import TSocket, TTransport
from echo import EchoService

# 建立 Framed 连接
transport = TTransport.TFramedTransport(TSocket.TSocket("127.0.0.1", 8080))
try:
    transport.open()

    # 创建 Binary 协议客户端
    client = EchoService.Client(TBinaryProtocol.TBinaryProtocol(transport))

    # 调用服务
    print(client.echo("hello"))
finally:
    # 关闭连接
    transport.close()
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="生成客户端代码"
go mod init example.com/echo
go get github.com/apache/thrift@v0.25.0
thrift --gen go -out . echo.thrift
```

生成工具需加入 `PATH`。调用代码保存到 `cmd/consumer/main.go`，运行 `go run ./cmd/consumer`。

```go title="消费者"
package main
import (
    "context"
    "fmt"
    "log"
    "github.com/apache/thrift/lib/go/thrift"
    "example.com/echo/echo"
)

func main() {
    // 建立 Framed 连接，使用 defer 在结束时关闭
    socket := thrift.NewTSocketConf("127.0.0.1:8080", &thrift.TConfiguration{})
    transport := thrift.NewTFramedTransport(socket)
    if err := transport.Open(); err != nil { log.Fatal(err) }
    defer transport.Close()

    // 创建 Binary 协议客户端
    client := echo.NewEchoServiceClientFactory(transport, thrift.NewTBinaryProtocolFactoryDefault())

    // 调用服务并读取结果
    result, err := client.Echo(context.Background(), "hello")
    if err != nil { log.Fatal(err) }
    fmt.Println(result)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="准备客户端"
npm install thrift@0.25.0
thrift --gen js:node echo.thrift
```

```javascript title="消费者"
import thrift from "thrift";
import EchoService from "./gen-nodejs/EchoService.js";

// 建立 Framed 连接并创建 Binary 协议客户端
const connection = thrift.createConnection("127.0.0.1", 8080, {
    transport: thrift.TFramedTransport, protocol: thrift.TBinaryProtocol,
});
connection.on("error", error => { console.error(error); connection.destroy(); });
const client = thrift.createClient(EchoService, connection);

// 连接成功后调用服务，收到结果后关闭连接
connection.once("connect", () => client.echo("hello", (error, result) => {
    connection.end();
    if (error) throw error;
    console.log(result);
}));
```

</TabItem>
<TabItem value="cpp" label="C++">

安装 Thrift C++ 开发包，使用本文的 `echo.thrift`：

```bash title="准备客户端"
thrift --gen cpp echo.thrift
c++ consumer.cpp gen-cpp/EchoService.cpp $(pkg-config --cflags --libs thrift) -o consumer
./consumer
```

```cpp title="消费者"
#include "gen-cpp/EchoService.h"
#include <thrift/protocol/TBinaryProtocol.h>
#include <thrift/transport/TSocket.h>
#include <thrift/transport/TBufferTransports.h>
#include <iostream>
#include <memory>

int main() {
    // 建立 Framed 连接并创建 Binary 协议客户端
    auto socket = std::make_shared<apache::thrift::transport::TSocket>("127.0.0.1", 8080);
    auto transport = std::make_shared<apache::thrift::transport::TFramedTransport>(socket);
    auto protocol = std::make_shared<apache::thrift::protocol::TBinaryProtocol>(transport);
    EchoServiceClient client(protocol);
    transport->open();

    // 调用服务并关闭连接
    std::string result;
    client.echo(result, "hello");
    std::cout << result << std::endl;
    transport->close();
}
```

</TabItem>
</Tabs>

## 协议配置

已有 IDL 使用 `ThriftIdlResource.load("classpath:echo.thrift")` 加载，通过 `bindResource("thrift", resource)` 绑定。

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.thrift.options.<名称>`。

- `encoding`：默认 `binary`，可选 `compact`、`json`。
- `framed`：默认 `true`。
- `multiplexed`：默认 `false`；启用后客户端也需配置服务名。
- `maxMessageSize`、`maxContainerSize`、`maxDepth`：限制消息及嵌套数据。

## 注意事项

- 两端的编码、分帧方式、字段编号和方法名须一致。
- oneway 方法必须返回 `void` 且不声明异常。
- HTTP 使用 `connector("http").protocol("thrift-http")`；接口资源也按 `thrift-http` 绑定。
