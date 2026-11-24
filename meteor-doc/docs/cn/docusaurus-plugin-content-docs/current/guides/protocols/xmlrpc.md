---
title: "XML-RPC"
description: "XML-RPC 使用 HTTP 和 XML 调用远程方法，适合对接已有 XML-RPC 客户端或服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# XML-RPC

XML-RPC 使用 HTTP 和 XML 调用远程方法，适合对接已有 XML-RPC 客户端或服务。

- 支持普通请求响应。
- 支持 XML-RPC 的结构体、数组、日期和 Base64 等值类型。
- 不支持单向消息或流式调用。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-xmlrpc</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-xmlrpc:@project.lastReleaseVer@'
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
        .protocol("xmlrpc").contextPath("/xmlrpc");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.xmlrpc.host=127.0.0.1
meteor.connectors.xmlrpc.port=8080
meteor.connectors.xmlrpc.context=/xmlrpc
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("xmlrpc")
        .contextPath("/xmlrpc");
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
        .protocol("xmlrpc")
        .aliasName("xmlrpc", "echo")
        .register();
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("xmlrpc")
        .aliasName("xmlrpc", "echo")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("xmlrpc", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`org.apache.xmlrpc:xmlrpc-client:3.1.3`、`org.apache.xmlrpc:xmlrpc-server:3.1.3`。

```java title="消费者"
import java.net.URI;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

// 配置服务地址并创建客户端
var config = new XmlRpcClientConfigImpl();
config.setServerURL(URI.create("http://127.0.0.1:8080/xmlrpc/default/1.0.0").toURL());
var client = new XmlRpcClient();
client.setConfig(config);

// 传入方法名和参数，发起调用
System.out.println(client.execute("echo.echo", new Object[] {"hello"}));
```

</TabItem>
<TabItem value="python" label="Python">

```python title="消费者"
from xmlrpc.client import ServerProxy

# 创建客户端，退出 with 后自动关闭
with ServerProxy("http://127.0.0.1:8080/xmlrpc/default/1.0.0") as client:
    # 调用服务
    print(client.echo.echo("hello"))
```

</TabItem>
<TabItem value="go" label="Go">

```bash title="准备客户端"
go mod init example.com/xmlrpc-client
go get github.com/kolo/xmlrpc@v0.0.0-20220921171641-a4b6fa1dd06b
```

```go title="消费者"
package main
import (
    "fmt"
    "github.com/kolo/xmlrpc"
)

func main() {
    // 创建客户端，退出时关闭连接
    client, err := xmlrpc.NewClient("http://127.0.0.1:8080/xmlrpc/default/1.0.0", nil)
    if err != nil { panic(err) }
    defer client.Close()

    // 传入方法名和参数，读取返回值
    var result string
    if err := client.Call("echo.echo", []interface{}{"hello"}, &result); err != nil { panic(err) }
    fmt.Println(result)
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```bash title="准备客户端"
npm install xmlrpc@1.3.2
```

```javascript title="消费者"
import xmlrpc from "xmlrpc";

// 创建客户端
const client = xmlrpc.createClient({url: "http://127.0.0.1:8080/xmlrpc/default/1.0.0"});

// 发起调用并读取结果
client.methodCall("echo.echo", ["hello"], (error, result) => {
    if (error) throw error;
    console.log(result);
});
```

</TabItem>
<TabItem value="browser" label="JavaScript">

通过同源反向代理访问提供者，接口路径保持如下配置。

```javascript title="消费者"
// 构造请求并发送
const response = await fetch("/xmlrpc/default/1.0.0", {
    method: "POST",
    headers: {"Content-Type": "text/xml"},
    body: "<methodCall><methodName>echo.echo</methodName><params><param><value><string>hello</string></value></param></params></methodCall>",
});

// 检查状态并读取响应
if (!response.ok) throw new Error(`HTTP ${response.status}`);
console.log(await response.text());
```

</TabItem>
<TabItem value="c" label="C">

安装 libcurl 开发包，保存为 `consumer.c` 后编译运行：

```bash title="准备客户端"
cc consumer.c -lcurl -o consumer
./consumer
```

```c title="消费者"
#include <curl/curl.h>
#include <stdio.h>

int main(void) {
    // 配置地址、请求头和请求内容
    curl_global_init(CURL_GLOBAL_DEFAULT);
    CURL *client = curl_easy_init();
    if (!client) return 1;
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: text/xml");
    curl_easy_setopt(client, CURLOPT_URL, "http://127.0.0.1:8080/xmlrpc/default/1.0.0");
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, "<methodCall><methodName>echo.echo</methodName><params><param><value><string>hello</string></value></param></params></methodCall>");
    curl_easy_setopt(client, CURLOPT_FAILONERROR, 1L);

    // 发送请求，默认将响应输出到标准输出
    CURLcode result = curl_easy_perform(client);
    if (result != CURLE_OK) fprintf(stderr, "%s\n", curl_easy_strerror(result));

    // 释放资源
    curl_slist_free_all(headers);
    curl_easy_cleanup(client);
    curl_global_cleanup();
    return result == CURLE_OK ? 0 : 1;
}
```

</TabItem>
<TabItem value="cpp" label="C++">

安装 libcurl 开发包，保存为 `consumer.cpp` 后编译运行：

```bash title="准备客户端"
c++ consumer.cpp -lcurl -o consumer
./consumer
```

```cpp title="消费者"
#include <curl/curl.h>
#include <stdio.h>

int main(void) {
    // 配置地址、请求头和请求内容
    curl_global_init(CURL_GLOBAL_DEFAULT);
    CURL *client = curl_easy_init();
    if (!client) return 1;
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: text/xml");
    curl_easy_setopt(client, CURLOPT_URL, "http://127.0.0.1:8080/xmlrpc/default/1.0.0");
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, "<methodCall><methodName>echo.echo</methodName><params><param><value><string>hello</string></value></param></params></methodCall>");
    curl_easy_setopt(client, CURLOPT_FAILONERROR, 1L);

    // 发送请求，默认将响应输出到标准输出
    CURLcode result = curl_easy_perform(client);
    if (result != CURLE_OK) fprintf(stderr, "%s\n", curl_easy_strerror(result));

    // 释放资源
    curl_slist_free_all(headers);
    curl_easy_cleanup(client);
    curl_global_cleanup();
    return result == CURLE_OK ? 0 : 1;
}
```

</TabItem>
<TabItem value="shell" label="Shell">

```bash title="消费者"
# 构造请求并发送，响应中包含 hello
curl --fail-with-body --silent --show-error 'http://127.0.0.1:8080/xmlrpc/default/1.0.0' \
    -H 'Content-Type: text/xml' \
    --data-binary '<methodCall><methodName>echo.echo</methodName><params><param><value><string>hello</string></value></param></params></methodCall>'
```

</TabItem>
</Tabs>

## 协议配置

- HTTP 路径通过 `contextPath` 设置。
- 服务方法通过协议别名和方法名定位，例如 `echo.echo`。

## 注意事项

- 需核对整数范围、空值扩展、日期时区及 Base64 表示。
- 服务失败通过 XML-RPC fault 返回，客户端应读取 fault 内容。
