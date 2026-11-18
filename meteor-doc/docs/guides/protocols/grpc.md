---
title: "gRPC"
description: "gRPC 使用 HTTP/2 和 Protobuf 描述消息，适合跨语言调用和持续传输消息的服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# gRPC

gRPC 使用 HTTP/2 和 Protobuf 描述消息，适合跨语言调用和持续传输消息的服务。

- 支持普通请求响应、服务端流、客户端流和双向流。
- 支持绑定 `.proto` 文件，使用生成的消息类型调用。
- 流式调用使用 `Flow.Publisher<T>`，两端需启用 `streamBody`。

## 引入依赖

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
        .option("httpVersion", 2)
        .protocol("grpc").contextPath("/grpc");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.grpc.host=127.0.0.1
meteor.connectors.grpc.port=8080
meteor.connectors.grpc.context=/grpc
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .option("httpVersion", 2)
        .protocol("grpc")
        .contextPath("/grpc");
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
        .protocol("grpc")
        .bindResource("grpc", GrpcProtoResource.load("classpath:echo.proto"))
        .register();
```

<a id="contract"></a>

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
        .protocol("grpc")
        .bindResource("grpc", GrpcProtoResource.load("classpath:echo.proto"))
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("grpc", "127.0.0.1", 8080))
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

客户端依赖：`io.grpc:grpc-netty-shaded:1.84.1`、`io.grpc:grpc-stub:1.84.1`、`io.grpc:grpc-protobuf:1.84.1`、`com.google.protobuf:protobuf-java:3.25.9`。

```java title="消费者"
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import example.generated.EchoContract;

// 定义方法和消息编码
var method = MethodDescriptor.<EchoContract.Message, EchoContract.Message>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("grpc/default/1.0.0/meteor.example.EchoService/echo")
        .setRequestMarshaller(ProtoUtils.marshaller(EchoContract.Message.getDefaultInstance()))
        .setResponseMarshaller(ProtoUtils.marshaller(EchoContract.Message.getDefaultInstance()))
        .build();

// 连接提供者
var channel = NettyChannelBuilder.forAddress("127.0.0.1", 8080)
        .usePlaintext()
        .build();
try {
    // 构造请求
    var request = EchoContract.Message.newBuilder()
            .setValue("hello")
            .build();

    // 发起调用并读取结果
    var result = ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, request);
    System.out.println(result.getValue());
} finally {
    // 关闭连接
    channel.shutdownNow();
}
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="生成客户端代码"
python -m pip install grpcio==1.84.0 grpcio-tools==1.84.0 protobuf==7.36.2
python -m grpc_tools.protoc -I . --python_out=. echo.proto
```

```python title="消费者"
import grpc
import echo_pb2

# 连接提供者，退出 with 后自动关闭
with grpc.insecure_channel("127.0.0.1:8080") as channel:
    # 定义方法路径和消息编码
    echo = channel.unary_unary(
        "/grpc/default/1.0.0/meteor.example.EchoService/echo",
        request_serializer=echo_pb2.Message.SerializeToString,
        response_deserializer=echo_pb2.Message.FromString,
    )

    # 构造请求并调用服务
    print(echo(echo_pb2.Message(value="hello")).value)
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="生成客户端代码"
go mod init example.com/echo
go get google.golang.org/grpc@v1.84.0 google.golang.org/protobuf@v1.36.12
go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.12
protoc -I . --go_out=. --go_opt=paths=source_relative echo.proto
```

生成工具需加入 `PATH`。调用代码保存到 `cmd/consumer/main.go`，运行 `go run ./cmd/consumer`。

```go title="消费者"
package main
import (
    "context"
    "fmt"
    "log"
    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
    echo "example.com/echo"
)

func main() {
    // 连接提供者，使用 defer 在结束时关闭
    connection, err := grpc.NewClient("127.0.0.1:8080", grpc.WithTransportCredentials(insecure.NewCredentials()))
    if err != nil { log.Fatal(err) }
    defer connection.Close()

    // 指定方法路径和消息，发起调用
    var result echo.Message
    err = connection.Invoke(context.Background(), "/grpc/default/1.0.0/meteor.example.EchoService/echo",
        &echo.Message{Value: "hello"}, &result)
    if err != nil { log.Fatal(err) }
    fmt.Println(result.Value)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="准备客户端"
npm install @grpc/grpc-js protobufjs
```

```javascript title="消费者"
import grpc from "@grpc/grpc-js";
import protobuf from "protobufjs";

// 加载消息定义并连接提供者
const root = await protobuf.load("echo.proto");
const Message = root.lookupType("meteor.example.Message");
const client = new grpc.Client("127.0.0.1:8080", grpc.credentials.createInsecure());
try {
    // 指定完整方法路径和消息编码，发起调用
    const result = await new Promise((resolve, reject) => client.makeUnaryRequest(
        "/grpc/default/1.0.0/meteor.example.EchoService/echo",
        value => Buffer.from(Message.encode(value).finish()),
        bytes => Message.decode(bytes),
        {value: "hello"},
        (error, value) => error ? reject(error) : resolve(value),
    ));
    console.log(result.value);
} finally {
    // 关闭连接
    client.close();
}
```

</TabItem>
<TabItem value="cpp" label="C++">

安装 gRPC C++ 和 Protobuf 开发包，使用本文的 `echo.proto`：

```bash title="准备客户端"
protoc --cpp_out=. echo.proto
c++ consumer.cpp echo.pb.cc $(pkg-config --cflags --libs grpc++ protobuf) -o consumer
./consumer
```

```cpp title="消费者"
#include "echo.pb.h"
#include <grpcpp/grpcpp.h>
#include <grpcpp/generic/generic_stub.h>
#include <grpcpp/impl/codegen/proto_utils.h>
#include <iostream>
#include <stdexcept>

int main() {
    // 连接提供者，构造请求
    auto channel = grpc::CreateChannel("127.0.0.1:8080", grpc::InsecureChannelCredentials());
    grpc::TemplatedGenericStub<meteor::example::Message, meteor::example::Message> client(channel);
    meteor::example::Message request, result;
    request.set_value("hello");

    // 指定完整方法路径，发起调用并等待结果
    grpc::ClientContext context;
    grpc::CompletionQueue queue;
    grpc::Status status;
    auto call = client.PrepareUnaryCall(&context, "/grpc/default/1.0.0/meteor.example.EchoService/echo", request, &queue);
    call->StartCall();
    call->Finish(&result, &status, reinterpret_cast<void *>(1));
    void *tag;
    bool completed;
    bool received = queue.Next(&tag, &completed);
    queue.Shutdown();
    if (!received || !completed || !status.ok()) throw std::runtime_error(status.error_message());
    std::cout << result.value() << std::endl;
}
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.grpc.options.<名称>`。

- `serializeType`：默认 `Protobuf`。
- `compression`：默认 `identity`，不压缩。
- `streamBody`：默认 `false`；流式调用设为 `true`，用法见[流式调用](../services/streaming.md)。

## 枚举映射

Java 枚举通过 `@MetEnumValue` 对应 `.proto` 中的名称和编号，不使用 Java 枚举的声明顺序作为编号：

```java
import net.hasor.meteor.annotation.MetEnumValue;
import net.hasor.meteor.annotation.MetType;

@MetType(name = "OrderState")
public enum OrderState {
    @MetEnumValue(name = "UNKNOWN", id = 0)
    Unknown,
    @MetEnumValue(name = "PAID", id = 1)
    Paid
}
```

对应的接口文件定义：

```protobuf
enum OrderState {
    UNKNOWN = 0;
    PAID = 1;
}
```

每个枚举值都需声明编号；Proto3 必须包含一个编号为 `0` 的值。

## 注意事项

- 服务名、方法名和字段编号须与 `.proto` 一致。
- 不能用裸 `Object`、`InputStream` 或 `Reader` 代替明确的消息类型。
- 服务地址包含协议前缀、分组和版本；原生 SDK 需使用完整方法路径。
