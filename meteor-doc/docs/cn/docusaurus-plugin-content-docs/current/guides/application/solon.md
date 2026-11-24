---
title: "Solon 整合"
sidebar_label: "Solon 整合"
description: "在 Solon 中引入 Meteor，发布服务，通过注入的接口调用服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Solon 整合

适用于 JDK 17 或以上版本的 Solon 项目。

## 引入依赖

```xml title="pom.xml 中的 dependencies"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-solon</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="build.gradle 中的 dependencies"
implementation 'net.hasor:meteor-solon:@project.lastReleaseVer@'
```

## 服务定义

定义服务接口，并提供实现类。

```java
import net.hasor.meteor.annotation.MetService;

@MetService(name = "EchoService", group = "default", version = "1.0.0")
public interface EchoService {
    String echo(String text);
}

public class EchoServiceImpl implements EchoService {
    @Override
    public String echo(String text) {
        return "Hello, " + text;
    }
}
```

## 服务发布

```properties title="meteor-provider.properties"
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=solon-echo-provider

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=28870
```

Meteor 插件随 Solon 自动加载，启动时注册实现类：

```java
import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Props;
import org.noear.solon.annotation.SolonMain;

@SolonMain
public class ProviderMain {
    public static void main(String[] args) {
        // 启动 Solon，自动加载 Meteor 插件
        Solon.start(ProviderMain.class, args, app -> {
            // 加载应用与协议配置
            Props properties = app.cfg();
            properties.loadAdd("classpath:meteor-provider.properties");

            // 注册服务实现
            AppContext context = app.context();
            context.beanMake(EchoServiceImpl.class);
        });
        System.out.println("服务已启动");
    }
}
```

## 服务调用

直连时用 `@MetAddress` 指定地址；使用地址本或注册中心时，通过 Solon 注入服务接口。

<Tabs groupId="consumer-connection" defaultValue="direct-code">
<TabItem value="direct-code" label="直连（代码）">

用 `@MetAddress` 指定提供者地址 `rsf://127.0.0.1:28870`，直接调用注入的服务接口。

`@MetAddress` 用于服务接口字段，无需叠加其他注入注解。字段不能是 `static` 或 `final`，构造方法内尚未完成注入。

```properties title="meteor-consumer.properties"
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=solon-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0
```

```java
import com.example.rpc.EchoService;
import net.hasor.meteor.annotation.MetAddress;
import org.noear.solon.annotation.Component;

@Component
public class EchoClient {
    @MetAddress("rsf://127.0.0.1:28870")
    private EchoService echo;

    public String call() {
        return this.echo.echo("Meteor!");
    }
}
```

</TabItem>
<TabItem value="address-book" label="直连（地址本）">

从 `address-book.xml` 加载提供者地址，再注入服务接口发起调用。

```properties title="meteor-consumer.properties"
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=solon-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0

# 直连：从地址本读取提供者地址
meteor.address-book=classpath:address-book.xml
```

```xml title="/address-book.xml"
<address-book xmlns="https://www.hasor.net/schema/meteor-address-book">
    <service group="default" name="EchoService" version="1.0.0">
        <address>rsf://127.0.0.1:28870</address>
    </service>
</address-book>
```

```java
import com.example.rpc.EchoService;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Component;

@Component
public class EchoClient {
    @Inject
    private EchoService echo;

    public String call() {
        return this.echo.echo("Meteor!");
    }
}
```

</TabItem>
<TabItem value="discovery" label="服务发现">

以 ZooKeeper 为例，添加[对应依赖](overview.md#引入依赖)，提供者和消费者连接同一注册中心。

```properties title="meteor-consumer.properties"
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=solon-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0

# 配置注册中心：提供者使用相同配置
meteor.registry.provider=zookeeper
meteor.registry.address=127.0.0.1:2181
# 将 address 替换为实际的 ZooKeeper 集群地址
meteor.registry.root=/meteor-echo
meteor.registry.publish-host=127.0.0.1
```

```java
import com.example.rpc.EchoService;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Component;

@Component
public class EchoClient {
    @Inject
    private EchoService echo;

    public String call() {
        return this.echo.echo("Meteor!");
    }
}
```

</TabItem>
</Tabs>

更多用法见[发布服务](../services/publish.md)、[订阅服务](../services/consume.md)和[服务发现](../discovery/zookeeper.md)。

## 配置项说明

### 调用超时 {#call-timeout}

在 `app.properties` 或通过 `@Import` 加载的属性文件中设置：

```properties
meteor.request-timeout=2000
meteor.connect-timeout=1000
```

两个值均以毫秒为单位。请求超时未设为正数时使用 `meteor.default-timeout`。若已声明 `MetConfiguration` Bean，改用它的 `setRequestTimeout(2000)`、`setConnectTimeout(1000)` 设置。单个服务可通过接口注解 `@MetService.clientTimeout` 或服务 Builder 的 `timeout` 设置，具体效果见[超时和异常](../services/timeouts.md)。

### 属性配置

| 配置项 | 说明 |
| --- | --- |
| `meteor.scan-packages` | 服务接口所在的包，支持逗号分隔或列表；默认使用 Solon 启动类所在包。 |
| `meteor.address-book` | 地址本位置，如 `classpath:address-book.xml`；服务发现时无需配置。 |

连接、并发等配置见[框架配置](../services/configuration/about.md)，注册中心接入见[服务发现](../discovery/about.md)。

### 容器配置

| 配置项 | 说明 |
| --- | --- |
| `MeteorPlugin` | 随 Solon 自动加载，无需手动注册。 |
| `MetConfiguration` Bean | 使用此 Bean，不再根据属性文件创建配置对象。 |
