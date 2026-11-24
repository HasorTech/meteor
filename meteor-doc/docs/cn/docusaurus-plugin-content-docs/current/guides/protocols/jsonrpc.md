---
title: "JSON-RPC"
description: "JSON-RPC 使用 JSON 表达方法名、参数和结果，可直接通过 HTTP 请求调用，适合脚本或 Web 系统接入。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# JSON-RPC

JSON-RPC 使用 JSON 表达方法名、参数和结果，可直接通过 HTTP 请求调用，适合脚本或 Web 系统接入。

- 支持 JSON-RPC 2.0 请求响应、通知和批量请求。
- 支持 HTTP 和 WebSocket。
- 不支持流式方法；WebSocket 长连接不等于流式 RPC。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-jsonrpc</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-jsonrpc:@project.lastReleaseVer@'
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
        .protocol("jsonrpc").contextPath("/jsonrpc");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.jsonrpc.host=127.0.0.1
meteor.connectors.jsonrpc.port=8080
meteor.connectors.jsonrpc.context=/jsonrpc
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("jsonrpc")
        .contextPath("/jsonrpc");
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
        .protocol("jsonrpc")
        .aliasName("jsonrpc", "echo")
        .register();
```

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("jsonrpc")
        .aliasName("jsonrpc", "echo")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("jsonrpc", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`com.github.briandilley.jsonrpc4j:jsonrpc4j:1.7`。

```java title="消费者"
import java.net.URI;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;

// 创建客户端并指定服务地址
var client = new JsonRpcHttpClient(URI.create("http://127.0.0.1:8080/jsonrpc/default/1.0.0").toURL());

// 传入方法名和参数，发起调用
System.out.println(client.invoke("echo.echo", new Object[] {"hello"}, String.class));
```

</TabItem>
<TabItem value="python" label="Python">

使用 Python 标准库：

```python title="消费者"
import json
import urllib.request

# 构造 JSON-RPC 请求
request = urllib.request.Request(
    "http://127.0.0.1:8080/jsonrpc/default/1.0.0",
    data=json.dumps({"jsonrpc": "2.0", "id": 1, "method": "echo.echo", "params": ["hello"]}).encode(),
    headers={"Content-Type": "application/json"},
)

# 发送请求并读取结果
with urllib.request.urlopen(request) as response:
    result = json.load(response)
    if "error" in result:
        raise RuntimeError(result["error"])
    print(result["result"])
```

</TabItem>
<TabItem value="go" label="Go">

使用 Go 标准库。

```go title="消费者"
package main
import (
    "fmt"
    "io"
    "net/http"
    "strings"
)

func main() {
    // 构造请求
    body := `{"jsonrpc":"2.0","id":1,"method":"echo.echo","params":["hello"]}`
    request, err := http.NewRequest("POST", "http://127.0.0.1:8080/jsonrpc/default/1.0.0", strings.NewReader(body))
    if err != nil { panic(err) }
    request.Header.Set("Content-Type", "application/json")

    // 发送请求并输出响应
    response, err := http.DefaultClient.Do(request)
    if err != nil { panic(err) }
    defer response.Body.Close()
    if response.StatusCode != http.StatusOK { panic(response.Status) }
    result, err := io.ReadAll(response.Body)
    if err != nil { panic(err) }
    fmt.Println(string(result))
}
```

</TabItem>
<TabItem value="node" label="Node.js">

使用 Node.js 22 或以上：

```javascript title="消费者"
// 构造 JSON-RPC 请求并发送
const response = await fetch("http://127.0.0.1:8080/jsonrpc/default/1.0.0", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({jsonrpc: "2.0", id: 1, method: "echo.echo", params: ["hello"]}),
});

// 读取并检查调用结果
const result = await response.json();
if (!response.ok || result.error) throw new Error(JSON.stringify(result));
console.log(result.result);
```

</TabItem>
<TabItem value="browser" label="JavaScript">

通过同源反向代理访问提供者，接口路径保持如下配置。

使用 Node.js 22 或以上：

```javascript title="消费者"
// 构造 JSON-RPC 请求并发送
const response = await fetch("/jsonrpc/default/1.0.0", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({jsonrpc: "2.0", id: 1, method: "echo.echo", params: ["hello"]}),
});

// 读取并检查调用结果
const result = await response.json();
if (!response.ok || result.error) throw new Error(JSON.stringify(result));
console.log(result.result);
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
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(client, CURLOPT_URL, "http://127.0.0.1:8080/jsonrpc/default/1.0.0");
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"echo.echo\",\"params\":[\"hello\"]}");
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
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(client, CURLOPT_URL, "http://127.0.0.1:8080/jsonrpc/default/1.0.0");
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"echo.echo\",\"params\":[\"hello\"]}");
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
curl --fail-with-body --silent --show-error 'http://127.0.0.1:8080/jsonrpc/default/1.0.0' \
    -H 'Content-Type: application/json' \
    --data-binary '{"jsonrpc":"2.0","id":1,"method":"echo.echo","params":["hello"]}'
```

</TabItem>
</Tabs>

## 协议配置

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.jsonrpc.options.<名称>`。

- `upgrade=websocket`：启用 WebSocket 接入，客户端需执行升级握手。
- HTTP 路径通过 `contextPath` 设置；服务子路径见[路径挂载](../services/multiple/http-paths.md)。

## 注意事项

- 请求方法名由服务别名和方法名组成，例如 `echo.echo`。
- 二进制数据使用 Base64；需确认数字范围、日期和空值的表示。
- 检查响应中的 `error`，不能只检查 HTTP 状态码。
