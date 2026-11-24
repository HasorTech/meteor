---
title: "Connect"
description: "Connect 是基于 HTTP 的 RPC 协议，可使用 Protobuf 或 JSON 消息，适合浏览器和多语言客户端接入。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Connect

Connect 是基于 HTTP 的 RPC 协议，可使用 Protobuf 或 JSON 消息，适合浏览器和多语言客户端接入。

- 支持普通请求响应、服务端流、客户端流和双向流。
- 普通调用可使用 HTTP/1.1；双向流要求 HTTP/2。
- 可绑定 `.proto` 文件，使用 Connect SDK 调用。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-connect</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-connect:@project.lastReleaseVer@'
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
        .protocol("connect").contextPath("/connect");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.connect.host=127.0.0.1
meteor.connectors.connect.port=8080
meteor.connectors.connect.context=/connect
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("connect")
        .contextPath("/connect");
```

## 发起 RPC 调用

在已启动的 `MetContext` 中发布 `EchoService`，客户端调用 `echo`，输出 `hello`。

```java title="服务定义"
import net.hasor.meteor.annotation.MetField;
import net.hasor.meteor.annotation.MetParam;

public interface EchoService {
    Message echo(@MetParam(id = 1) Message message);
}

public class Message {
    @MetField(id = 1)
    private String value;

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}

public class EchoServiceImpl implements EchoService {
    public Message echo(Message message) { return message; }
}
```

```java title="提供者"
import net.hasor.meteor.protocol.grpc.GrpcProtoResource;

context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .name("meteor.example.EchoService")
        .protocol("connect")
        .bindResource("connect", GrpcProtoResource.load("classpath:echo.proto"))
        .register();
```

<a id="interface-file"></a>

将 `echo.proto` 放入 Meteor 提供者和消费者的 classpath 根目录；其他客户端使用同一文件生成消息类型：

```protobuf title="echo.proto"
syntax = "proto3";
package meteor.example;
option java_package = "example.generated";
option java_outer_classname = "EchoContract";
option go_package = "example.com/echo;echo";

message Message { string value = 1; }
service EchoService { rpc echo(Message) returns (Message); }
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.protocol.grpc.GrpcProtoResource;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("connect")
        .bindResource("connect", GrpcProtoResource.load("classpath:echo.proto"))
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("connect", "127.0.0.1", 8080))
        .getRemote(binding);

// 构造请求并调用服务
Message request = new Message();
request.setValue("hello");
System.out.println(client.echo(request).getValue());
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

```bash title="生成客户端代码"
mkdir -p src/main/java
protoc -I . --java_out=src/main/java echo.proto
```

客户端依赖：`com.connectrpc:connect-kotlin-okhttp:0.5.1`、`com.connectrpc:connect-kotlin-google-java-ext:0.5.1`、`com.google.protobuf:protobuf-java:3.25.9`。

```java title="消费者"
import java.util.Collections;
import com.connectrpc.*;
import com.connectrpc.extensions.GoogleJavaProtobufStrategy;
import com.connectrpc.impl.ProtocolClient;
import com.connectrpc.okhttp.ConnectOkHttpClient;
import com.connectrpc.protocols.NetworkProtocol;
import kotlin.jvm.JvmClassMappingKt;
import okhttp3.OkHttpClient;
import example.generated.EchoContract;

// 创建 HTTP 客户端
OkHttpClient http = new OkHttpClient();
try {
    // 配置协议和服务地址
    var config = new ProtocolClientConfig("http://127.0.0.1:8080/connect/default/1.0.0",
            new GoogleJavaProtobufStrategy(), NetworkProtocol.CONNECT);
    var client = new ProtocolClient(new ConnectOkHttpClient(http), config);

    // 定义调用方法
    var type = JvmClassMappingKt.getKotlinClass(EchoContract.Message.class);
    var method = new MethodSpec<EchoContract.Message, EchoContract.Message>(
            "meteor.example.EchoService/echo", type, type, StreamType.UNARY, Idempotency.UNKNOWN);

    // 构造请求
    var request = EchoContract.Message.newBuilder()
            .setValue("hello")
            .build();

    // 发起调用并读取结果
    var response = client.unaryBlocking(request, Collections.emptyMap(), method)
            .execute();
    if (response instanceof ResponseMessage.Failure<EchoContract.Message> failure) {
        throw failure.getCause();
    }
    System.out.println(((ResponseMessage.Success<EchoContract.Message>) response).getMessage().getValue());
} finally {
    // 释放连接资源
    http.dispatcher().executorService().shutdownNow();
    http.connectionPool().evictAll();
}
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="准备客户端"
python -m pip install connectrpc==0.13.0 protoc-gen-connectrpc==0.13.0 grpcio-tools
mkdir -p generated
python -m grpc_tools.protoc -I. --python_out=generated --connectrpc_out=generated --connectrpc_opt=protobuf=google echo.proto
```

```python title="消费者"
from connectrpc.protocol import ProtocolType
from generated.echo_connect import EchoServiceClientSync
from generated.echo_pb2 import Message

# 配置协议和服务地址，退出 with 后自动关闭客户端
with EchoServiceClientSync("http://127.0.0.1:8080/connect/default/1.0.0",
                           protocol=ProtocolType.CONNECT) as client:
    # 构造请求并调用服务
    print(client.echo(Message(value="hello")).value)
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="生成客户端代码"
go mod init example.com/echo
go get connectrpc.com/connect@v1.19.1 google.golang.org/protobuf@v1.36.12
go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.12
go install connectrpc.com/connect/cmd/protoc-gen-connect-go@v1.19.1
protoc -I . --go_out=. --go_opt=paths=source_relative \
  --connect-go_out=. --connect-go_opt=paths=source_relative echo.proto
```

生成工具需加入 `PATH`。调用代码保存到 `cmd/consumer/main.go`，运行 `go run ./cmd/consumer`。

```go title="消费者"
package main
import (
    "context"
    "fmt"
    "log"
    "net/http"
    "connectrpc.com/connect"
    echo "example.com/echo"
    "example.com/echo/echoconnect"
)

func main() {
    // 指定服务地址并创建客户端
    client := echoconnect.NewEchoServiceClient(http.DefaultClient,
        "http://127.0.0.1:8080/connect/default/1.0.0")

    // 构造请求并调用服务
    result, err := client.Echo(context.Background(), connect.NewRequest(&echo.Message{Value: "hello"}))
    if err != nil { log.Fatal(err) }
    fmt.Println(result.Msg.Value)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="生成客户端代码"
npm install --save-dev @bufbuild/protoc-gen-es@2.16.0
mkdir -p generated
protoc -I . --plugin=protoc-gen-es=./node_modules/.bin/protoc-gen-es \
  --es_out=generated --es_opt=target=js echo.proto
```

```bash title="安装依赖"
npm install @connectrpc/connect@2.2.0 @connectrpc/connect-node@2.2.0 @bufbuild/protobuf@2.16.0
```

```javascript title="消费者"
import {create} from "@bufbuild/protobuf";
import {createClient} from "@connectrpc/connect";
import {createConnectTransport} from "@connectrpc/connect-node";
import {EchoService, MessageSchema} from "./generated/echo_pb.js";

// 配置服务地址并创建客户端
const transport = createConnectTransport({
    baseUrl: "http://127.0.0.1:8080/connect/default/1.0.0", httpVersion: "1.1",
});
const client = createClient(EchoService, transport);

// 构造请求并调用服务
console.log((await client.echo(create(MessageSchema, {value: "hello"}))).value);
```

</TabItem>
<TabItem value="browser" label="JavaScript">

```bash title="准备客户端"
npm install @bufbuild/protobuf @connectrpc/connect @connectrpc/connect-web
npm install --save-dev @bufbuild/protoc-gen-es
protoc --plugin=protoc-gen-es=./node_modules/.bin/protoc-gen-es --es_out=. --es_opt=target=js echo.proto
```

通过前端构建工具打包运行；跨域时在提供者配置 `corsOrigins`。

```javascript title="消费者"
import {create} from "@bufbuild/protobuf";
import {createClient} from "@connectrpc/connect";
import {createConnectTransport} from "@connectrpc/connect-web";
import {EchoService, MessageSchema} from "./echo_pb.js";

// 配置服务地址并创建客户端
const transport = createConnectTransport({baseUrl: "http://127.0.0.1:8080/connect/default/1.0.0"});
const client = createClient(EchoService, transport);

// 构造请求并调用服务
console.log((await client.echo(create(MessageSchema, {value: "hello"}))).value);
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.connect.options.<名称>`。

- `serializeType`：默认 `Protobuf`，也可选择 `Json`。
- `compression`：默认 `identity`。
- `get=true`：允许 GET 调用，仅适用于普通请求响应方法。
- 流式调用启用 `streamBody`；双向流还需在连接器设置 `httpVersion=2`。

## 注意事项

- 基础地址后还需添加 `.proto` 中的完整服务名和方法名，通常由 SDK 完成。
- 浏览器跨域调用需设置 CORS；携带凭据时使用明确的允许来源。
- HTTP/1.1 普通调用成功不代表双向流配置已生效。
