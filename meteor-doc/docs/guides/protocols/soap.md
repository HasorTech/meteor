---
title: "SOAP"
description: "SOAP 使用 XML 消息调用 Web Service，通过 WSDL 描述服务接口，适合对接已有 Web Service 系统。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# SOAP

SOAP 使用 XML 消息调用 Web Service，通过 WSDL 描述服务接口，适合对接已有 Web Service 系统。

- 支持 SOAP 1.1、1.2，默认使用 1.1。
- 支持请求响应、单向操作，以及 inline、MIME、MTOM 附件。
- 可根据 Java 接口生成 WSDL，也可加载已有 WSDL；不支持 RPC 消息流。

## 引入依赖

<Tabs groupId="protocol-dependencies">
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-soap</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
implementation 'net.hasor:meteor-protocol-soap:@project.lastReleaseVer@'
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
        .protocol("soap").contextPath("/soap");
```

</TabItem>
<TabItem value="properties" label="application.properties">

```properties title="提供者"
meteor.connectors.soap.host=127.0.0.1
meteor.connectors.soap.port=8080
meteor.connectors.soap.context=/soap
```

加载配置：[Spring](../application/spring.md)、[Hasor](../application/hasor.md)、[Solon](../application/solon.md)。

</TabItem>
</Tabs>

```java title="消费者"
configuration.connector("http")
        .protocol("soap")
        .contextPath("/soap");
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
        .protocol("soap")
        .aliasName("soap", "echo")
        .register();
```

<a id="interface-file"></a>

提供者自动生成 WSDL：`http://127.0.0.1:8080/soap/default/1.0.0/echo?wsdl`。

<Tabs groupId="protocol-client-language" queryString="client">
<TabItem value="meteor" label="Meteor" default>

```java title="消费者"
import net.hasor.meteor.address.InterAddress;

// 订阅服务接口
var binding = context.publisher().rsfService(EchoService.class)
        .name("meteor.example.EchoService")
        .protocol("soap")
        .aliasName("soap", "echo")
        .register();

// 通过直连地址获取代理
EchoService client = context.getRsfClient(new InterAddress("soap", "127.0.0.1", 8080))
        .getRemote(binding);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="java-sdk" label="Java SDK">

客户端依赖：`com.sun.xml.ws:jaxws-rt:4.0.5`。

```java title="客户端接口"
import jakarta.jws.*;

@WebService(name = "echoPortType", targetNamespace = SoapEcho.NAMESPACE)
public interface SoapEcho {
    String NAMESPACE = "urn:hasor:meteor:soap:echo";

    @WebMethod(operationName = "echo", action = NAMESPACE + "/echo")
    @WebResult(name = "return", targetNamespace = "")
    String echo(@WebParam(name = "arg0", targetNamespace = "") String text);
}
```

```java title="消费者"
import java.net.URI;
import javax.xml.namespace.QName;
import jakarta.xml.ws.Service;

// 加载 WSDL 并创建服务代理
var wsdl = URI.create("http://127.0.0.1:8080/soap/default/1.0.0/echo?wsdl");
var service = Service.create(wsdl.toURL(), new QName(SoapEcho.NAMESPACE, "echo"));
var client = service.getPort(new QName(SoapEcho.NAMESPACE, "echoPort"), SoapEcho.class);

// 调用服务
System.out.println(client.echo("hello"));
```

</TabItem>
<TabItem value="python" label="Python">

```bash title="安装依赖"
python -m pip install zeep==4.3.3
```

```python title="消费者"
import requests
import zeep
from zeep.transports import Transport

# 创建 HTTP 会话，退出 with 后自动关闭
with requests.Session() as session:
    # 加载 WSDL 并创建客户端
    client = zeep.Client("http://127.0.0.1:8080/soap/default/1.0.0/echo?wsdl",
                         transport=Transport(session=session))

    # 调用服务
    print(client.service.echo(arg0="hello"))
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
    body := `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><e:echo xmlns:e="urn:hasor:meteor:soap:echo"><arg0>hello</arg0></e:echo></s:Body></s:Envelope>`
    request, err := http.NewRequest("POST", "http://127.0.0.1:8080/soap/default/1.0.0/echo", strings.NewReader(body))
    if err != nil { panic(err) }
    request.Header.Set("Content-Type", "text/xml; charset=utf-8")
    request.Header.Set("SOAPAction", "\"urn:hasor:meteor:soap:echo/echo\"")

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

```bash title="准备客户端"
npm install soap@1.14.0
```

```javascript title="消费者"
import soap from "soap";

// 加载 WSDL 并创建服务代理
const client = await soap.createClientAsync("http://127.0.0.1:8080/soap/default/1.0.0/echo?wsdl");

// 调用服务并读取结果
const [result] = await client.echoAsync({arg0: "hello"});
console.log(result.return);
```

</TabItem>
<TabItem value="browser" label="JavaScript">

通过同源反向代理访问提供者，接口路径保持如下配置。

```javascript title="消费者"
// 构造请求并发送
const response = await fetch("/soap/default/1.0.0/echo", {
    method: "POST",
    headers: {"Content-Type": "text/xml; charset=utf-8", "SOAPAction": "\"urn:hasor:meteor:soap:echo/echo\""},
    body: "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><e:echo xmlns:e=\"urn:hasor:meteor:soap:echo\"><arg0>hello</arg0></e:echo></s:Body></s:Envelope>",
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
    headers = curl_slist_append(headers, "Content-Type: text/xml; charset=utf-8");
    headers = curl_slist_append(headers, "SOAPAction: \"urn:hasor:meteor:soap:echo/echo\"");
    curl_easy_setopt(client, CURLOPT_URL, "http://127.0.0.1:8080/soap/default/1.0.0/echo");
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><e:echo xmlns:e=\"urn:hasor:meteor:soap:echo\"><arg0>hello</arg0></e:echo></s:Body></s:Envelope>");
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
    headers = curl_slist_append(headers, "Content-Type: text/xml; charset=utf-8");
    headers = curl_slist_append(headers, "SOAPAction: \"urn:hasor:meteor:soap:echo/echo\"");
    curl_easy_setopt(client, CURLOPT_URL, "http://127.0.0.1:8080/soap/default/1.0.0/echo");
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><e:echo xmlns:e=\"urn:hasor:meteor:soap:echo\"><arg0>hello</arg0></e:echo></s:Body></s:Envelope>");
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
curl --fail-with-body --silent --show-error 'http://127.0.0.1:8080/soap/default/1.0.0/echo' \
    -H 'Content-Type: text/xml; charset=utf-8' \
    -H 'SOAPAction: "urn:hasor:meteor:soap:echo/echo"' \
    --data-binary '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><e:echo xmlns:e="urn:hasor:meteor:soap:echo"><arg0>hello</arg0></e:echo></s:Body></s:Envelope>'
```

</TabItem>
</Tabs>

## 协议配置

已有 WSDL 使用 `SoapWsdlResource.load("classpath:echo.wsdl")` 加载，通过 `bindResource("soap", resource)` 绑定。

通过 `protocol.option("名称", 值)` 设置协议选项；配置文件使用 `meteor.connectors.soap.options.<名称>`。

- `soapVersion`：默认 `1.1`。
- `soapUse`：默认 `literal`；`soapStyle`、`soapParameterStyle` 设置消息形式。
- `attachmentMode`：默认 `inline`；`attachmentThreshold`：默认 `1024` 字节。
- `publicBaseUrl`：设置向客户端公开的服务地址；`soapActionRequired`：控制 SOAP Action 校验。

## XML 名称

Java 方法、参数和类型名称与 SOAP 名称不同时，用公共注解指定：

```java
import net.hasor.meteor.annotation.MetEnumValue;
import net.hasor.meteor.annotation.MetField;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetReturn;
import net.hasor.meteor.annotation.MetType;

public interface OrderService {
    @MetMethod(name = "getOrder")
    @MetReturn(name = "order")
    Order find(@MetParam(name = "orderId") String id);
}

@MetType(name = "Order")
public class Order {
    @MetField(name = "orderId")
    public String id;
    public OrderState state;
}

public enum OrderState {
    @MetEnumValue(name = "pending")
    Pending,
    @MetEnumValue(name = "paid")
    Paid
}
```

SOAP 操作为 `getOrder`，输入成员为 `orderId`，返回成员为 `order`；返回类型为 `Order`，属性 `id` 写为 `orderId`，枚举 `Paid` 写为文本 `paid`。命名空间、XML 属性和集合包装继续使用 Jackson XML 注解；已有 WSDL 的名称和结构仍需匹配。

## 注意事项

- 客户端和提供者须使用一致的命名空间、Action、元素名称和 Fault 定义。
- 附件传输不等于流式 RPC。
- XML 属性、文本和集合包装使用 Jackson XML 注解；精确的 XML 结构可由 WSDL/XSD 描述。
