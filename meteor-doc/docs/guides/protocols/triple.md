---
title: "Triple"
description: "Triple 是 Apache Dubbo 的 HTTP RPC 协议，支持 Java 接口调用和 Protobuf 消息，适合对接 Dubbo Triple 客户端。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Triple

Triple 是 Apache Dubbo 的 HTTP RPC 协议，支持 Java 接口调用和 Protobuf 消息，适合对接 Dubbo Triple 客户端。

- 支持普通调用和流式调用。
- 支持 Java 接口包装及 Protobuf 消息；客户端和服务端需选择相同方式。
- 流式调用需启用 HTTP/2 和 `streamBody`，可用形态取决于 HTTP 模式和消息编码。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-triple</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-triple:@project.lastReleaseVer@'
```

</TabItem>
</Tabs>

## 启用协议 {#configuration}

提供者监听端口并提供服务，消费者通过出站协议调用服务。

<Tabs groupId="protocol-provider-configuration">
<TabItem value="code" label="Java">

```java title="提供者"
configuration.connector("http")
        .bind("0.0.0.0", 8080)
        .option("httpVersion", 2)
        .protocol("triple").contextPath("/")
        .option("serializeType", "Protobuf");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.triple.host=0.0.0.0
meteor.connectors.triple.port=8080
meteor.connectors.triple.context=/
meteor.connectors.triple.options.serializeType=Protobuf
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .option("httpVersion", 2)
        .protocol("triple")
        .contextPath("/")
        .option("serializeType", "Protobuf");
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
        .protocol("triple")
        .bindResource("triple", GrpcProtoResource.load("classpath:echo.proto"))
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
        .protocol("triple")
        .bindResource("triple", GrpcProtoResource.load("classpath:echo.proto"))
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("triple", "127.0.0.1", 8080))
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

原生 Dubbo 客户端的接口名为 `meteor.example.EchoService`：

```java title="客户端接口"
package meteor.example;
import example.generated.EchoContract;

public interface EchoService {
    EchoContract.Message echo(EchoContract.Message message);
}
```

客户端依赖：`org.apache.dubbo:dubbo:3.2.20`、`com.google.protobuf:protobuf-java:3.25.9`。

```java title="消费者"
import meteor.example.EchoService;
import example.generated.EchoContract;
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
reference.setInterface(EchoService.class);
reference.setGroup("default");
reference.setVersion("1.0.0");
reference.setUrl("tri://127.0.0.1:8080/meteor.example.EchoService");
try {
    // 启动客户端并获取代理
    bootstrap.reference(reference)
            .start();
    EchoService client = reference.get();

    // 构造请求并调用服务
    var request = EchoContract.Message.newBuilder()
            .setValue("hello")
            .build();
    System.out.println(client.echo(request).getValue());
} finally {
    // 关闭客户端
    bootstrap.destroy();
}
```

</TabItem>
<TabItem value="python" label="Python">

Triple 使用 Protobuf 编码时，可通过 gRPC 客户端调用。

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
        "/meteor.example.EchoService/echo",
        request_serializer=echo_pb2.Message.SerializeToString,
        response_deserializer=echo_pb2.Message.FromString,
    )

    # 构造请求并调用服务
    print(echo(echo_pb2.Message(value="hello")).value)
```

</TabItem>
<TabItem value="go" label="Go">

Triple 使用 Protobuf 编码时，可通过 gRPC 客户端调用。

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
    err = connection.Invoke(context.Background(), "/meteor.example.EchoService/echo",
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
        "/meteor.example.EchoService/echo",
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
    auto call = client.PrepareUnaryCall(&context, "/meteor.example.EchoService/echo", request, &queue);
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

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.triple.options.<名称>`。

- `serializeType`：默认 `Hessian2`；本文的原生示例显式选择 `Protobuf`。
- `httpMode`：默认 `grpc`。
- `compression`：默认 `identity`。
- `maxMessageSize`、`streamWorkers`：控制消息大小和流式处理线程。

## 注意事项

- Triple 与 Dubbo 二进制协议是不同协议，接入点不能互换。
- 服务名、分组、版本及消息编码须与 Dubbo 客户端一致。
- 本文使用根路径 `/`，与原生 Dubbo Triple 客户端的请求路径保持一致。

原生 Dubbo SDK 可能将回环地址替换为本机 IP，因此示例提供者监听 `0.0.0.0`；跨机器调用时使用提供者实际可达的 IP。
