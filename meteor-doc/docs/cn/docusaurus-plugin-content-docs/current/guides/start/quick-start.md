---
title: "快速开始"
description: "发布一个 JSON-RPC Echo 服务，通过 Meteor Java 客户端或 curl 完成第一次调用。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 快速开始

发布一个 JSON-RPC Echo 服务，分别通过 Meteor Java 客户端和 curl 调用，取得 `Hello, Meteor!`。

## 准备环境

准备 JDK 17 或以上版本，以及 Maven 或 Gradle。提供者监听本机 `8080` 端口，消费者通过固定地址调用，无需注册中心。

本文使用 `@project.lastReleaseVer@`。使用开发快照时，需先确保使用的 Maven 仓库中已有该版本制品；以下配置同时支持本地 Maven 仓库和 Maven Central。

## 引入依赖

创建普通 Java 项目，选择构建工具并使用以下配置。JSON-RPC 模块会传递引入 Meteor 内核。

<Tabs groupId="quick-start-build-tool">
<TabItem value="maven" label="Maven" default>

```xml title="pom.xml"
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>meteor-quick-start</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>net.hasor</groupId>
            <artifactId>meteor-protocol-jsonrpc</artifactId>
            <version>@project.lastReleaseVer@</version>
        </dependency>
    </dependencies>
</project>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy title="build.gradle"
plugins { id 'java' }
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation 'net.hasor:meteor-protocol-jsonrpc:@project.lastReleaseVer@'
}
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
```

</TabItem>
</Tabs>

## 发布服务

在项目中定义 `EchoService` 接口：

```java title="服务接口"
public interface EchoService {
    String echo(String text);
}
```

创建 `ProviderMain`，为接口提供实现，并通过 JSON-RPC 发布：

```java title="提供者"
import java.util.concurrent.CountDownLatch;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ProviderMain {
    public static void main(String[] args) throws Exception {
        // 配置协议和监听端口
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-provider");
        config.connector("http").bind("127.0.0.1", 8080)
                .protocol("jsonrpc").contextPath("/jsonrpc");

        try (MetContext context = config.buildContext()) {
            context.start();

            // 发布服务，echo 为 JSON-RPC 服务别名
            context.publisher().rsfService(EchoService.class, text -> "Hello, " + text + "!")
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo")
                    .register();
            System.out.println("服务已启动，监听端口 8080");

            // 保持提供者进程运行
            new CountDownLatch(1).await();
        }
    }
}
```

运行 `ProviderMain.main()`，看到启动提示后保持进程运行。

## 调用服务

<Tabs groupId="quick-start-client">
<TabItem value="meteor" label="Meteor（Java）" default>

在同一项目中创建 `ConsumerMain`，复用 `EchoService` 接口，通过固定地址调用：

```java title="消费者"
import java.util.List;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置调用协议，消费者无需监听端口
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");
        config.connector("http").protocol("jsonrpc").contextPath("/jsonrpc");

        try (MetContext context = config.buildContext()) {
            context.start();

            // 订阅服务，并添加提供者的固定地址
            MetBindInfo<EchoService> binding = context.publisher().rsfService(EchoService.class)
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo")
                    .register();
            context.getUpdater().appendStaticAddress(binding.getBindID(),
                    List.of(new InterAddress("jsonrpc", "127.0.0.1", 8080)));

            // 获取接口代理并调用
            EchoService echo = context.getRsfClient().getRemote(binding);
            System.out.println(echo.echo("Meteor"));
        }
    }
}
```

运行 `ConsumerMain.main()`，输出：

```text
Hello, Meteor!
```

</TabItem>
<TabItem value="curl" label="HTTP（curl）">

提供者保持运行，在终端发送 JSON-RPC 请求：

```bash
curl -sS http://127.0.0.1:8080/jsonrpc/default/1.0.0 \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"echo.echo","params":["Meteor"]}'
```

响应中的 `result` 为 `Hello, Meteor!`：

```json
{"jsonrpc":"2.0","id":1,"result":"Hello, Meteor!"}
```

`echo.echo` 由服务别名 `echo` 和方法名 `echo` 组成。URL 中的 `default` 和 `1.0.0` 是默认服务分组和版本，其他语言也可以按相同格式发起 HTTP 请求。

</TabItem>
</Tabs>

## 接下来

- [创建应用](../application/overview.md)：接入普通项目或 Spring、Hasor、Solon。
- [多协议发布](../services/multiple/about.md)：为同一业务增加其他协议接入点。
- [服务发现](../discovery/about.md)：从固定地址切换到地址本或注册中心。
