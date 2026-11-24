---
title: "Hasor 整合"
sidebar_label: "Hasor 整合"
description: "在 Hasor 或 Hasor Boot 中引入 Meteor，发布服务，通过注入的接口调用服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Hasor 整合

适用于 JDK 17 或以上版本的 Hasor / Hasor Boot 项目。

## 引入依赖

<Tabs groupId="hasor-project" defaultValue="boot">
<TabItem value="boot" label="Hasor Boot">

```xml title="pom.xml 中的 dependencies"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-hasor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>hasor-boot</artifactId>
    <version>@project.hasorVersion@</version>
</dependency>
```

```groovy title="build.gradle 中的 dependencies"
implementation 'net.hasor:meteor-hasor:@project.lastReleaseVer@'
implementation 'net.hasor:hasor-boot:@project.hasorVersion@'
```

</TabItem>
<TabItem value="hasor" label="Hasor">

```xml title="pom.xml 中的 dependencies"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-hasor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>hasor-config</artifactId>
    <version>@project.hasorVersion@</version>
</dependency>
```

```groovy title="build.gradle 中的 dependencies"
implementation 'net.hasor:meteor-hasor:@project.lastReleaseVer@'
implementation 'net.hasor:hasor-config:@project.hasorVersion@'
```

</TabItem>
</Tabs>

## 服务定义

定义服务接口，并提供实现类。

```java
import net.hasor.config.Bean;
import net.hasor.meteor.annotation.MetService;

@MetService(name = "EchoService", group = "default", version = "1.0.0")
public interface EchoService {
    String echo(String text);
}

@Bean
public class EchoServiceImpl implements EchoService {
    @Override
    public String echo(String text) {
        return "Hello, " + text;
    }
}
```

## 服务发布

```properties title="meteor-provider.properties"
hasor.loadPackages=com.example.rpc,com.example.provider

# 配置应用
meteor.application=hasor-echo-provider

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=28860
```

`@Bean` 业务类由 Hasor 扫描注册，`MeteorModule` 扫描服务接口并发布服务。

<Tabs groupId="hasor-project" defaultValue="boot">
<TabItem value="boot" label="Hasor Boot">

```java
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.core.ApiBinder;
import net.hasor.core.Module;
import net.hasor.meteor.MeteorModule;

public class ProviderMain implements Module {
    public static void main(String[] args) throws Exception {
        // 加载应用与协议配置
        Boot boot = new Boot()
                .arguments(args)
                .sources(ProviderMain.class)
                .hconfigFile("meteor-provider.properties");

        // 启动 Hasor Boot 和 Meteor
        try (BootApplication app = boot.start()) {
            System.out.println("服务已启动");

            // 保持提供者运行
            app.join();
        }
    }

    @Override
    public void loadModule(ApiBinder binder) throws Throwable {
        binder.installModule(new MeteorModule());
    }
}
```

</TabItem>
<TabItem value="hasor" label="Hasor">

```java
import net.hasor.core.AppContext;
import net.hasor.core.Hasor;
import net.hasor.meteor.MeteorModule;

public class ProviderMain {
    public static void main(String[] args) throws Throwable {
        // 加载应用与协议配置
        Hasor builder = Hasor.create();
        builder.mainSettingWith("meteor-provider.properties");

        // 创建容器并启动 Meteor
        try (AppContext app = builder.build(new MeteorModule())) {
            System.out.println("服务已启动");

            // 保持提供者运行
            app.join();
        }
    }
}
```

</TabItem>
</Tabs>

## 服务调用

直连时用 `@MetAddress` 指定地址；使用地址本或注册中心时，通过 Hasor 注入服务接口。

<Tabs groupId="consumer-connection" defaultValue="direct-code">
<TabItem value="direct-code" label="直连（代码）">

用 `@MetAddress` 指定提供者地址 `rsf://127.0.0.1:28860`，直接调用注入的服务接口。

`@MetAddress` 用于服务接口字段，无需叠加其他注入注解。字段不能是 `static` 或 `final`，构造方法内尚未完成注入。

```properties title="meteor-consumer.properties"
hasor.loadPackages=com.example.rpc,com.example.consumer

# 配置应用
meteor.application=hasor-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0
```

```java
import com.example.rpc.EchoService;
import net.hasor.meteor.annotation.MetAddress;
import net.hasor.config.Bean;

@Bean
public class EchoClient {
    @MetAddress("rsf://127.0.0.1:28860")
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
hasor.loadPackages=com.example.rpc,com.example.consumer

# 配置应用
meteor.application=hasor-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0

# 直连：从地址本读取提供者地址
meteor.address-book=classpath:address-book.xml
```

```xml title="/address-book.xml"
<address-book xmlns="https://www.hasor.net/schema/meteor-address-book">
    <service group="default" name="EchoService" version="1.0.0">
        <address>rsf://127.0.0.1:28860</address>
    </service>
</address-book>
```

```java
import com.example.rpc.EchoService;
import net.hasor.core.Inject;
import net.hasor.config.Bean;

@Bean
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
hasor.loadPackages=com.example.rpc,com.example.consumer

# 配置应用
meteor.application=hasor-echo-consumer

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
import net.hasor.core.Inject;
import net.hasor.config.Bean;

@Bean
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

在启动时传给 `hconfigFile` 或 `mainSettingWith` 的属性文件中设置。

```properties
meteor.request-timeout=2000
meteor.connect-timeout=1000
```

| 配置 | 说明 |
| --- | --- |
| `meteor.request-timeout` | 请求等待上限，示例为 2000 毫秒。 |
| `meteor.connect-timeout` | 建立连接的等待上限，示例为 1000 毫秒。 |
| `meteor.default-timeout` | 请求超时未设为正数时的默认 RPC 超时。 |

若已声明 `MetConfiguration` Bean，通过它的 `setRequestTimeout(2000)`、`setConnectTimeout(1000)` 设置，属性文件不会覆盖该 Bean。单个服务可在接口的 `@MetService` 上设置 `clientTimeout = 1000`，或在手工发布、订阅时调用 Builder 的 `.timeout(1000)`。具体效果见[超时和异常](../services/timeouts.md)。

### 注册拦截器 {#register-filter}

将下面的类放在 Hasor 扫描的包中，启动时注册[日志拦截器](../services/filters.md#write-filter)：

```java
import net.hasor.config.Bean;
import net.hasor.core.Inject;
import net.hasor.meteor.MetContext;

@Bean(initMethod = "register")
public class RpcFilters {
    @Inject
    private MetContext meteor;

    public void register() {
        this.meteor.publisher().bindFilter("rpc-log", new RpcLogFilter());
    }
}
```

仅声明 `MetFilter` Bean 不会自动生效，需要调用 `bindFilter`。业务方法可通过 `MetRequestLocal` [读取隐式参数](../services/options.md#read-options)，无需额外注册拦截器。Hasor 会创建、启动和关闭 `MetContext`。

### 属性配置

| 配置项 | 说明 |
| --- | --- |
| `hasor.loadPackages` | 服务接口和业务 Bean 所在的包，多个包用逗号分隔。 |
| `meteor.address-book` | 地址本位置，如 `classpath:address-book.xml`；服务发现时无需配置。 |

连接、并发等配置见[框架配置](../services/configuration/about.md)，注册中心接入见[服务发现](../discovery/about.md)。

### 容器配置

| 配置项 | 说明 |
| --- | --- |
| `MeteorModule` | 启用 Meteor 整合，在应用模块中安装一次。 |
| `@Bean` | 通过 `hasor-config` 扫描注册业务类；Hasor Boot 默认启用扫描。 |
| `MetConfiguration` Bean | 使用此 Bean，不再根据属性文件创建配置对象。 |
