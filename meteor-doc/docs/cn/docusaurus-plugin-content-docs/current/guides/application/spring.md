---
title: "Spring 整合"
sidebar_label: "Spring 整合"
description: "在 Spring 或 Spring Boot 中引入 Meteor，发布服务，通过注入的接口调用服务。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Spring 整合

适用于 JDK 17 或以上版本、Spring 6 / Spring Boot 3 项目。提供者与消费者使用各自的启动类和配置文件。

## 引入依赖

<Tabs groupId="spring-project" defaultValue="boot">
<TabItem value="boot" label="Spring Boot 项目">

```xml title="pom.xml 中的 dependencies"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-spring-starter</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="build.gradle 中的 dependencies"
implementation 'net.hasor:meteor-spring-starter:@project.lastReleaseVer@'
```

已有 Boot 项目保留当前使用的 Spring Boot Starter 版本。

</TabItem>
<TabItem value="spring" label="Spring 项目">

```xml title="pom.xml 中的 dependencies"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-spring</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="build.gradle 中的 dependencies"
implementation 'net.hasor:meteor-spring:@project.lastReleaseVer@'
```

</TabItem>
</Tabs>

## 配置方式

普通 Spring 选择以下一种方式；Spring Boot 由 Starter 自动配置。

<Tabs groupId="spring-configuration" defaultValue="properties">
<TabItem value="properties" label="基于 application.properties 配置文件">

```properties title="application.properties"
# 服务接口扫描包
meteor.scan-packages=com.example.rpc
meteor.application=spring-echo-provider
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=28880
```

```java
import net.hasor.meteor.EnableMeteor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan("com.example.provider")
@EnableMeteor(basePackages = "com.example.rpc")
@PropertySource("classpath:application.properties")
public class MeteorConfig {
}
```

</TabItem>
<TabItem value="annotation" label="基于注解配置">

```java
import net.hasor.meteor.EnableMeteor;
import net.hasor.meteor.bootstrap.MetConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.example.provider")
@EnableMeteor(basePackages = "com.example.rpc")
public class MeteorConfig {
    @Bean
    public MetConfiguration meteorConfiguration() {
        // 配置应用
        MetConfiguration configuration = new MetConfiguration();
        configuration.setApplicationName("spring-echo-provider");

        // 配置协议
        MetConfiguration.ConnectorSettings connector = configuration.connector("tcp");
        connector.bind("127.0.0.1", 28880);
        connector.protocol("rsf");
        return configuration;
    }
}
```

</TabItem>
<TabItem value="xml" label="基于 XML 配置">

```properties title="application.properties"
# 服务接口扫描包
meteor.scan-packages=com.example.rpc
meteor.application=spring-echo-provider
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=28880
```

```xml title="applicationContext.xml"
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
                           http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
    <context:component-scan base-package="com.example.provider"/>
    <bean class="net.hasor.meteor.ServiceScannerConfigurer">
        <property name="basePackage" value="com.example.rpc"/>
        <property name="configLocation" value="classpath:application.properties"/>
    </bean>
</beans>
```

</TabItem>
</Tabs>

## 在 XML 中显式声明消费接口

在 @project.developmentVersion@ 中，可以通过 `ServiceBean` 将消费接口声明为 Spring Bean。先配置由 Spring 管理启动和关闭的 `meteorContext`，再声明接口 Bean：

```xml
<bean id="echoService" class="net.hasor.meteor.ServiceBean">
    <property name="context" ref="meteorContext"/>
    <property name="serviceInterface" value="com.example.rpc.EchoService"/>
    <property name="address" value="rsf://127.0.0.1:18880"/>
</bean>
```

其他 Bean 可以按 `EchoService` 类型注入或通过 `ref="echoService"` 引用。指定 `address` 时，代理直接调用该端点。省略此属性时，沿用上下文的默认调用方式，远程地址可由地址本或注册发现提供。接口尚未登记时，`ServiceBean` 会在该上下文注册消费者。

完整配置见 [XML 显式注册示例](../../../../met-example/java/spring/spring-xml1/README.md)。

## 服务定义

定义服务接口，并提供实现类。

```java
import net.hasor.meteor.annotation.MetService;
import org.springframework.stereotype.Service;

@MetService(name = "EchoService", group = "default", version = "1.0.0")
public interface EchoService {
    String echo(String text);
}

@Service
public class EchoServiceImpl implements EchoService {
    @Override
    public String echo(String text) {
        return "Hello, " + text;
    }
}
```

## 服务发布

```properties title="meteor-provider.properties"
# 服务接口扫描包
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=spring-echo-provider

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=28880
```

`@Service` 实现类由 Spring 扫描。Spring Boot 的 Starter 自动扫描服务接口并发布服务；普通 Spring 通过 `@EnableMeteor` 启用。

<Tabs groupId="spring-project" defaultValue="boot">
<TabItem value="boot" label="Spring Boot 项目">

```java
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:meteor-provider.properties")
public class ProviderMain {
    public static void main(String[] args) throws InterruptedException {
        // 启动 Spring Boot，自动初始化并启动 Meteor
        try (ConfigurableApplicationContext app = SpringApplication.run(ProviderMain.class, args)) {
            System.out.println("服务已启动");

            // 保持提供者运行
            CountDownLatch latch = new CountDownLatch(1);
            latch.await();
        }
    }
}
```

</TabItem>
<TabItem value="spring" label="Spring 项目">

```java
import java.util.concurrent.CountDownLatch;
import net.hasor.meteor.EnableMeteor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan
@EnableMeteor(basePackages = "com.example.rpc")
@PropertySource("classpath:meteor-provider.properties")
public class ProviderMain {
    public static void main(String[] args) throws InterruptedException {
        // 启动 Spring 容器，自动初始化并启动 Meteor
        try (AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext(ProviderMain.class)) {
            app.registerShutdownHook();
            System.out.println("服务已启动");

            // 保持提供者运行
            CountDownLatch latch = new CountDownLatch(1);
            latch.await();
        }
    }
}
```

</TabItem>
</Tabs>

## 服务调用

直连时用 `@MetAddress` 指定地址；使用地址本或注册中心时，通过 Spring 注入服务接口。

<Tabs groupId="consumer-connection" defaultValue="direct-code">
<TabItem value="direct-code" label="直连（代码）">

用 `@MetAddress` 指定提供者地址 `rsf://127.0.0.1:28880`，直接调用注入的服务接口。

`@MetAddress` 用于服务接口字段，无需叠加其他注入注解。字段不能是 `static` 或 `final`，构造方法内尚未完成注入。

```properties title="meteor-consumer.properties"
# 服务接口扫描包
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=spring-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0
```

```java
import com.example.rpc.EchoService;
import net.hasor.meteor.annotation.MetAddress;
import org.springframework.stereotype.Component;

@Component
public class EchoClient {
    @MetAddress("rsf://127.0.0.1:28880")
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
# 服务接口扫描包
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=spring-echo-consumer

# 配置协议
meteor.connectors.rsf.host=127.0.0.1
meteor.connectors.rsf.port=0

# 直连：从地址本读取提供者地址
meteor.address-book=classpath:address-book.xml
```

```xml title="/address-book.xml"
<address-book xmlns="https://www.hasor.net/schema/meteor-address-book">
    <service group="default" name="EchoService" version="1.0.0">
        <address>rsf://127.0.0.1:28880</address>
    </service>
</address-book>
```

```java
import com.example.rpc.EchoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EchoClient {
    @Autowired
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
# 服务接口扫描包
meteor.scan-packages=com.example.rpc
# 配置应用
meteor.application=spring-echo-consumer

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EchoClient {
    @Autowired
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

Spring Boot 使用 `application.properties`；普通 Spring 使用已通过 `@PropertySource` 或 XML `configLocation` 加载的属性文件。

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

将下面的类放在 Spring 扫描的包中，启动时注册[日志拦截器](../services/filters.md#write-filter)：

```java
import net.hasor.meteor.MetContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RpcFilters implements InitializingBean {
    @Autowired
    private MetContext meteor;

    @Override
    public void afterPropertiesSet() {
        this.meteor.publisher().bindFilter("rpc-log", new RpcLogFilter());
    }
}
```

仅声明 `MetFilter` Bean 不会自动生效，需要调用 `bindFilter`。业务方法可通过 `MetRequestLocal` [读取隐式参数](../services/options.md#read-options)，无需额外注册拦截器。Spring 会创建、启动和关闭 `MetContext`。

### application.properties

| 配置项 | 说明 |
| --- | --- |
| `meteor.enabled` | 仅用于 Boot 自动配置，默认 `true`；设为 `false` 关闭。 |
| `meteor.scan-packages` | 服务接口所在的包。Boot 默认使用启动类所在包及子包；普通 Spring 默认使用 `@EnableMeteor` 所在配置类的包。 |

连接、并发等配置见[框架配置](../services/configuration/about.md)，注册中心接入见[服务发现](../discovery/about.md)。

### 扫描设置

| 配置项 | 说明 |
| --- | --- |
| `@EnableMeteor.basePackages` | 服务接口所在的包，优先于 `meteor.scan-packages`。 |
| `ServiceScannerConfigurer.basePackage` | XML 中指定扫描包，支持逗号、分号或空白分隔；优先于 `meteor.scan-packages`。 |
| `ServiceScannerConfigurer.configLocation` | 加载属性文件，已有 Spring 环境配置优先。 |
| `MetConfiguration` Bean | 使用此 Bean，不再根据属性文件创建配置对象。 |
