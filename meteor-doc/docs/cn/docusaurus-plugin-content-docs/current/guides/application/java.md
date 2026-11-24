---
title: "普通项目"
sidebar_label: "普通项目"
description: "引入 Meteor，在普通 Java 工程中启动提供者与消费者，完成一次调用。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 普通项目

在 JDK 17 或以上版本的 Java 工程中，编写服务接口、提供者和消费者的启动类，完成一次 RSF 调用。

## 引入依赖

```xml title="pom.xml 中的 dependencies"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="build.gradle 中的 dependencies"
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
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

配置 RSF 监听地址，调用 `rsfService(...)` 和 `register()` 注册服务，再调用 `context.start()` 启动。

```java
import java.util.concurrent.CountDownLatch;
import com.example.rpc.EchoService;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetPublisher;
import net.hasor.meteor.bootstrap.MetConfiguration.ConnectorSettings;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ProviderMain {
    public static void main(String[] args) throws Exception {
        // 配置应用
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("java-echo-provider");

        // 配置协议
        ConnectorSettings connector = config.connector("tcp");
        connector.bind("127.0.0.1", 2180); // 提供者监听地址
        connector.protocol("rsf");

        // 创建 MetContext，退出时自动关闭
        try (MetContext context = config.buildContext()) {
            // 注册服务
            MetPublisher publisher = context.publisher();
            MetPublisher.ConfigurationBuilder<EchoService> service =
                    publisher.rsfService(EchoService.class, new EchoServiceImpl());
            service.register();

            // 启动服务
            context.start();
            System.out.println("服务已启动：" + context.getListenAddress("rsf"));

            // 保持提供者运行
            CountDownLatch latch = new CountDownLatch(1);
            latch.await();
        }
    }
}
```

## 服务调用

<Tabs groupId="consumer-connection" defaultValue="direct-code">
<TabItem value="direct-code" label="直连（代码）">

在代码中指定 `rsf://127.0.0.1:2180`，直接调用该提供者。

```java
import com.example.rpc.EchoService;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetPublisher;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration.ConnectorSettings;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("java-echo-consumer");

        // 配置协议
        ConnectorSettings connector = config.connector("tcp");
        connector.protocol("rsf"); // 配置调用远程服务使用的协议

        // 创建 MetContext，退出时自动关闭
        try (MetContext context = config.buildContext()) {
            // 声明服务引用
            MetPublisher publisher = context.publisher();
            MetPublisher.ConfigurationBuilder<EchoService> service = publisher.rsfService(EchoService.class);
            MetBindInfo<EchoService> binding = service.register();

            // 启动应用
            context.start();

            // 调用服务
            InterAddress address = new InterAddress("rsf", "127.0.0.1", 2180);
            MetClient client = context.getRsfClient(address); // 直连：指定提供者地址
            EchoService echo = client.getRemote(binding);
            System.out.println(echo.echo("Meteor!"));
        }
    }
}
```

</TabItem>
<TabItem value="address-book" label="直连（地址本）">

将提供者地址写入 `address-book.xml`，消费者启动时加载。

```xml title="/address-book.xml"
<address-book xmlns="https://www.hasor.net/schema/meteor-address-book">
    <service group="default" name="EchoService" version="1.0.0">
        <address>rsf://127.0.0.1:2180</address>
    </service>
</address-book>
```

```java
import com.example.rpc.EchoService;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetPublisher;
import net.hasor.meteor.bootstrap.MetConfiguration.ConnectorSettings;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("java-echo-consumer");
        config.setAddressBook("classpath:address-book.xml"); // 直连：从地址本读取提供者地址

        // 配置协议
        ConnectorSettings connector = config.connector("tcp");
        connector.protocol("rsf"); // 配置调用远程服务使用的协议

        // 创建 MetContext，退出时自动关闭
        try (MetContext context = config.buildContext()) {
            // 声明服务引用
            MetPublisher publisher = context.publisher();
            MetPublisher.ConfigurationBuilder<EchoService> service = publisher.rsfService(EchoService.class);
            MetBindInfo<EchoService> binding = service.register();

            // 启动应用
            context.start();

            // 调用服务
            MetClient client = context.getRsfClient();
            EchoService echo = client.getRemote(binding);
            System.out.println(echo.echo("Meteor!"));
        }
    }
}
```

</TabItem>
<TabItem value="discovery" label="服务发现">

以 ZooKeeper 为例，添加[对应依赖](overview.md#引入依赖)，提供者和消费者连接同一注册中心。

```java
import com.example.rpc.EchoService;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetPublisher;
import net.hasor.meteor.bootstrap.MetConfiguration.ConnectorSettings;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.zookeeper.ZookeeperRegistryConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("java-echo-consumer");

        // 配置协议
        ConnectorSettings connector = config.connector("tcp");
        connector.protocol("rsf"); // 配置调用远程服务使用的协议

        ZookeeperRegistryConfiguration registry = new ZookeeperRegistryConfiguration();
        registry.setAddress("127.0.0.1:2181"); // 替换为实际的 ZooKeeper 集群地址
        registry.setRoot("/meteor-echo"); // 与提供者使用相同的注册路径
        registry.setPublishHost("127.0.0.1");
        config.addExtension(registry);

        // 创建 MetContext，退出时自动关闭
        try (MetContext context = config.buildContext()) {
            // 声明服务引用
            MetPublisher publisher = context.publisher();
            MetPublisher.ConfigurationBuilder<EchoService> service = publisher.rsfService(EchoService.class);
            MetBindInfo<EchoService> binding = service.register();

            // 启动应用
            context.start();

            // 调用服务
            MetClient client = context.getRsfClient(); // 服务发现：从注册中心获取提供者地址
            EchoService echo = client.getRemote(binding);
            System.out.println(echo.echo("Meteor!"));
        }
    }
}
```

</TabItem>
</Tabs>

## 运行验证

按所选调用方式保存文件，在 IDE 中以两个独立进程运行：

1. 运行 `com.example.provider.ProviderMain`，等待输出“服务已启动”。
2. 运行 `com.example.consumer.ConsumerMain`，输出应包含：

```text
Hello, Meteor!
```

提供者监听本机 `2180` 端口；更换调用方式后重新启动两个进程。

更多用法见[发布服务](../services/publish.md)、[订阅服务](../services/consume.md)和[服务发现](../discovery/zookeeper.md)。
