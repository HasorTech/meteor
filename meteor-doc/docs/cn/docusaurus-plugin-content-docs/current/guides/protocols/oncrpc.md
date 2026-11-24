---
title: "ONC RPC"
description: "ONC RPC 使用程序号、程序版本和过程号定位调用，以 XDR 编码参数，常用于对接采用 Sun RPC 的系统。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# ONC RPC

ONC RPC 使用程序号、程序版本和过程号定位调用，以 XDR 编码参数，常用于对接采用 Sun RPC 的系统。

- 支持 TCP 和 UDP，协议名分别为 `oncrpc`、`oncrpc-udp`。
- 支持普通请求响应，可加载 IDL 或在 Java 中声明编号和编码。
- 不支持 Meteor 单向消息或流式调用。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-oncrpc</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-oncrpc:@project.lastReleaseVer@'
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
        .protocol("oncrpc");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.oncrpc.host=127.0.0.1
meteor.connectors.oncrpc.port=8080
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("tcp")
        .protocol("oncrpc");
```

## 发起 RPC 调用

在已启动的 `MetContext` 中发布 `EchoService`，客户端调用 `echo`，输出 `hello`。

```java title="服务定义"
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.annotation.MetMethod;

@MetService(id = 0x31234569L)
public interface EchoService {
    @MetMethod(id = 1)
    String echo(String text);
}

public class EchoServiceImpl implements EchoService {
    public String echo(String text) { return text; }
}
```

```java title="提供者"
import net.hasor.meteor.protocol.oncrpc.binding.OncRpcIdlResource;

OncRpcIdlResource resource = OncRpcIdlResource.load("classpath:echo.x");
resource.selectProgram("ECHO_PROGRAM", 1);
context.publisher().rsfService(EchoService.class, new EchoServiceImpl())
        .name("meteor.example.EchoService")
        .protocol("oncrpc")
        .bindResource("oncrpc", resource)
        .register();
```

<a id="interface-file"></a>

将 `echo.x` 放入提供者的 classpath 根目录：

```c title="echo.x"
program ECHO_PROGRAM {
    version ECHO_VERSION {
        string ECHO(string) = 1;
    } = 1;
} = 0x31234569;
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.protocol.oncrpc.binding.OncRpcIdlResource;

// 订阅服务接口
OncRpcIdlResource resource = OncRpcIdlResource.load("classpath:echo.x");
resource.selectProgram("ECHO_PROGRAM", 1);
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("oncrpc")
        .bindResource("oncrpc", resource)
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("oncrpc", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`org.acplt.remotetea:remotetea-oncrpc:1.1.6`。

```java title="消费者"
import java.net.InetAddress;
import org.acplt.oncrpc.OncRpcTcpClient;
import org.acplt.oncrpc.XdrString;

// 指定程序号、版本和直连地址
var client = new OncRpcTcpClient(InetAddress.getByName("127.0.0.1"), 0x31234569, 1, 8080);
try {
    // 调用过程并读取返回值
    XdrString result = new XdrString();
    client.call(1, new XdrString("hello"), result);
    System.out.println(result.stringValue());
} finally {
    // 关闭连接
    client.close();
}
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="准备客户端"
python -m pip install sunrpc==1.1.0 standard-xdrlib termcolor
```

```python title="消费者"
from sunrpc.client import TCPClient, rpc_client_obtain, rpc_client_send
from sunrpc.types import RpcStr

# 声明过程号和 XDR 字符串类型
class EchoClient(TCPClient):
    @rpc_client_send(1, RpcStr)
    @rpc_client_obtain(RpcStr)
    def echo(self, result):
        return result

# 通过程序号、版本和端口直连，不依赖 rpcbind
client = EchoClient("127.0.0.1", 8080, 0x31234569, 1)
client.connect()
try:
    # 调用过程并读取返回值
    print(client.echo("hello"))
finally:
    client.close()
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="准备客户端"
go mod init example.com/oncrpc-client
go get github.com/prashanthpai/sunrpc@v0.0.0-20210303180433-689a3880d90a
```

```go title="消费者"
package main
import (
    "fmt"
    "net"
    "github.com/prashanthpai/sunrpc"
)

func main() {
    // 将本地方法名映射到 IDL 中的程序号、版本和过程号
    procedure := sunrpc.Procedure{
        ID: sunrpc.ProcedureID{ProgramNumber: 0x31234569, ProgramVersion: 1, ProcedureNumber: 1},
        Name: "Echo.Echo",
    }
    if err := sunrpc.RegisterProcedure(procedure, true); err != nil { panic(err) }

    // 创建 TCP 客户端，退出时关闭连接
    connection, err := net.Dial("tcp", "127.0.0.1:8080")
    if err != nil { panic(err) }
    client := sunrpc.NewClient(connection)
    defer client.Close()

    // 调用过程并读取返回值
    var result string
    if err := client.Call("Echo.Echo", "hello", &result); err != nil { panic(err) }
    fmt.Println(result)
}
```

</TabItem>
<TabItem value="c" label="C">

Linux 安装 libtirpc 开发库和 `rpcgen`。调用代码保存为 `consumer.c`，在 `echo.x` 所在目录生成并编译：

```bash title="安装依赖"
rpcgen -h -o echo.h echo.x
rpcgen -c -o echo_xdr.c echo.x
rpcgen -l -o echo_clnt.c echo.x
cc -I/usr/include/tirpc consumer.c echo_xdr.c echo_clnt.c -ltirpc -o consumer
./consumer
```

```c title="消费者"
#include "echo.h"
#include <arpa/inet.h>
#include <stdio.h>

int main(void) {
    // 指定提供者地址
    struct sockaddr_in address = {0};
    address.sin_family = AF_INET;
    address.sin_port = htons(8080);
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

    // 通过程序号和版本创建客户端
    int socket_fd = RPC_ANYSOCK;
    CLIENT *client = clnttcp_create(&address, ECHO_PROGRAM, ECHO_VERSION, &socket_fd, 0, 0);
    if (!client) { clnt_pcreateerror("connect"); return 1; }

    // 构造请求并调用过程
    char text[] = "hello";
    char *request = text;
    char **result = echo_1(&request, client);
    if (!result) { clnt_perror(client, "echo"); clnt_destroy(client); return 1; }
    puts(*result);

    // 释放返回值并关闭客户端
    xdr_free((xdrproc_t)xdr_wrapstring, (char *)result);
    clnt_destroy(client);
    return 0;
}
```

</TabItem>
<TabItem value="cpp" label="C++">

Linux 安装 libtirpc 开发库和 `rpcgen`。调用代码保存为 `consumer.cpp`，在 `echo.x` 所在目录生成并编译：

```bash title="安装依赖"
rpcgen -h -o echo.h echo.x
rpcgen -c -o echo_xdr.c echo.x
rpcgen -l -o echo_clnt.c echo.x
cc -I/usr/include/tirpc -c echo_xdr.c echo_clnt.c
c++ -I/usr/include/tirpc consumer.cpp echo_xdr.o echo_clnt.o -ltirpc -o consumer
./consumer
```

```cpp title="消费者"
#include "echo.h"
#include <arpa/inet.h>
#include <stdio.h>

int main(void) {
    // 指定提供者地址
    struct sockaddr_in address = {0};
    address.sin_family = AF_INET;
    address.sin_port = htons(8080);
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

    // 通过程序号和版本创建客户端
    int socket_fd = RPC_ANYSOCK;
    CLIENT *client = clnttcp_create(&address, ECHO_PROGRAM, ECHO_VERSION, &socket_fd, 0, 0);
    if (!client) { clnt_pcreateerror("connect"); return 1; }

    // 构造请求并调用过程
    char text[] = "hello";
    char *request = text;
    char **result = echo_1(&request, client);
    if (!result) { clnt_perror(client, "echo"); clnt_destroy(client); return 1; }
    puts(*result);

    // 释放返回值并关闭客户端
    xdr_free((xdrproc_t)xdr_wrapstring, (char *)result);
    clnt_destroy(client);
    return 0;
}
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.oncrpc.options.<名称>`。

- TCP：`recordFragmentSize`、`maxRecordFragments`、`maxMessageSize` 控制分片和消息上限。
- UDP：`retryAttempts`、`retryInitialDelay`、`retryMaxDelay` 控制重传。
- UDP：`duplicateCacheSize`、`duplicateCacheTtl` 控制重复请求缓存。

## 枚举映射

Java 枚举通过 `@MetEnumValue` 对应 XDR 中的符号和数字，不使用 Java 枚举的声明顺序作为编号：

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

```c
enum OrderState {
    UNKNOWN = 0,
    PAID = 1
};
```

绑定已有 `.x` 文件时，Java 声明的名称和编号必须与文件一致，不一致会在发布时报告。

## 注意事项

- 程序号、数值程序版本、过程号和 XDR 数据结构必须匹配。
- ONC RPC 的数值程序版本不是 Meteor 服务坐标中的字符串版本。
- 示例直接连接固定端口，不依赖 rpcbind。UDP 重传可能造成重复请求，写操作需处理幂等性。

使用 UDP 时，将连接器改为 `udp`、协议名改为 `oncrpc-udp`；配置文件的协议属性前缀也相应改为 `meteor.connectors.oncrpc-udp`。
