---
title: "gRPC-Web"
description: "gRPC-Web 面向浏览器，通过浏览器可使用的 HTTP 请求调用 Protobuf 服务。Meteor 可直接提供 gRPC-Web 接入点。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# gRPC-Web

gRPC-Web 面向浏览器，通过浏览器可使用的 HTTP 请求调用 Protobuf 服务。Meteor 可直接提供 gRPC-Web 接入点。

- 支持普通请求响应和服务端流。
- 支持二进制和 Text 两种消息格式。
- 不支持客户端流和双向流。

## 引入依赖

gRPC-Web 与 gRPC 使用同一个协议模块。

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-grpc</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-grpc:@project.lastReleaseVer@'
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
        .protocol("grpc-web").contextPath("/grpc-web")
        .option("corsOrigins", "http://127.0.0.1:8090,http://localhost:8090");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.grpc-web.host=127.0.0.1
meteor.connectors.grpc-web.port=8080
meteor.connectors.grpc-web.context=/grpc-web
meteor.connectors.grpc-web.options.corsOrigins=http://127.0.0.1:8090,http://localhost:8090
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("grpc-web")
        .contextPath("/grpc-web");
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
        .protocol("grpc-web")
        .bindResource("grpc-web", GrpcProtoResource.load("classpath:echo.proto"))
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
        .protocol("grpc-web")
        .bindResource("grpc-web", GrpcProtoResource.load("classpath:echo.proto"))
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("grpc-web", "127.0.0.1", 8080))
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
    var config = new ProtocolClientConfig("http://127.0.0.1:8080/grpc-web/default/1.0.0",
            new GoogleJavaProtobufStrategy(), NetworkProtocol.GRPC_WEB);
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
with EchoServiceClientSync("http://127.0.0.1:8080/grpc-web/default/1.0.0",
                           protocol=ProtocolType.GRPC_WEB) as client:
    # 构造请求并调用服务
    print(client.echo(Message(value="hello")).value)
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="准备客户端"
go mod init example.com/echo
go get connectrpc.com/connect@v1.19.1 google.golang.org/protobuf@v1.36.12
go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.12
protoc --go_out=. --go_opt=module=example.com/echo echo.proto
```

将以下代码保存为 `cmd/consumer/main.go`，执行 `go run ./cmd/consumer`。

```go title="消费者"
package main
import (
    "context"
    "fmt"
    "net/http"
    "connectrpc.com/connect"
    echo "example.com/echo"
)

func main() {
    // 配置 gRPC-Web 客户端和完整方法地址
    client := connect.NewClient[echo.Message, echo.Message](http.DefaultClient,
        "http://127.0.0.1:8080/grpc-web/default/1.0.0/meteor.example.EchoService/echo",
        connect.WithGRPCWeb())

    // 构造请求并调用服务
    result, err := client.CallUnary(context.Background(), connect.NewRequest(&echo.Message{Value: "hello"}))
    if err != nil { panic(err) }
    fmt.Println(result.Msg.Value)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="准备客户端"
npm install @bufbuild/protobuf @connectrpc/connect @connectrpc/connect-web
npm install --save-dev @bufbuild/protoc-gen-es
protoc --plugin=protoc-gen-es=./node_modules/.bin/protoc-gen-es --es_out=. --es_opt=target=js echo.proto
```

```javascript title="消费者"
import {create} from "@bufbuild/protobuf";
import {createClient} from "@connectrpc/connect";
import {createGrpcWebTransport} from "@connectrpc/connect-web";
import {EchoService, MessageSchema} from "./echo_pb.js";

// 配置 gRPC-Web 客户端
const transport = createGrpcWebTransport({
    baseUrl: "http://127.0.0.1:8080/grpc-web/default/1.0.0", useBinaryFormat: true,
});
const client = createClient(EchoService, transport);

// 构造请求并调用服务
console.log((await client.echo(create(MessageSchema, {value: "hello"}))).value);
```

</TabItem>
<TabItem value="browser" label="JavaScript">

```bash title="生成客户端代码"
npm install --save-dev @bufbuild/protoc-gen-es@2.16.0
mkdir -p generated
protoc -I . --plugin=protoc-gen-es=./node_modules/.bin/protoc-gen-es \
  --es_out=generated --es_opt=target=js echo.proto
```

```bash title="运行页面"
npm install grpc-web@2.1.1 @bufbuild/protobuf@2.16.0 esbuild@0.28.2
npx esbuild consumer.js --bundle --format=esm --outfile=consumer.bundle.js
python -m http.server 8090 --bind 127.0.0.1
```

调用代码保存为 `consumer.js`，在页面中用 `<script type="module" src="/consumer.bundle.js"></script>` 引入：

```javascript title="消费者"
import grpcWeb from "grpc-web";
import {create, fromBinary, toBinary} from "@bufbuild/protobuf";
import {MessageSchema} from "./generated/echo_pb.js";

// 定义方法和消息编码
const method = new grpcWeb.MethodDescriptor(
    "/meteor.example.EchoService/echo", grpcWeb.MethodType.UNARY, Object, Object,
    (message) => toBinary(MessageSchema, message),
    (bytes) => fromBinary(MessageSchema, bytes),
);

// 创建客户端
const client = new grpcWeb.GrpcWebClientBase({format: "binary"});

// 构造请求并处理调用结果
client.rpcCall("http://127.0.0.1:8080/grpc-web/default/1.0.0" + method.getName(),
    create(MessageSchema, {value: "hello"}), {}, method, (error, result) => {
        if (error) { console.error(error); return; }
        console.log(result.value);
    });
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.grpc-web.options.<名称>`。

- `webText`：默认 `false`，使用二进制格式；设为 `true` 使用 Text 格式。
- `corsOrigins`、`corsHeaders`：默认 `*`。
- `corsCredentials`：默认 `false`；`corsMaxAge`：默认 `600` 秒。

## 注意事项

- 普通 gRPC 客户端不能直接替代 gRPC-Web 客户端。
- 客户端格式与服务端 `webText` 配置应一致。
- 携带浏览器凭据时，必须配置明确的允许来源，不能使用 `*`；代理需正确转发 OPTIONS 预检请求。
