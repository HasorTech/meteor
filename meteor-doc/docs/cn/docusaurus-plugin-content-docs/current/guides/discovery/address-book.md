---
title: "使用地址本"
description: "通过 XML 文件维护提供者地址，消费者启动时加载，无需接入注册中心。"
mdx:
  format: mdx
---

# 使用地址本

通过 XML 文件维护提供者地址，消费者启动时加载，无需接入注册中心。

## 编写地址本

在消费者的 `src/main/resources` 下创建 `address-book.xml`，分组、名称和版本与服务接口一致。

```xml title="/address-book.xml"
<address-book xmlns="https://www.hasor.net/schema/meteor-address-book">
    <service group="default" name="EchoService" version="1.0.0">
        <address>rsf://127.0.0.1:2180</address>
    </service>
</address-book>
```

## 消费者调用

假设目标服务已发布，消费者使用已有的 `EchoService` 接口发起调用。


```java
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用和接入协议
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");
        config.setAddressBook("classpath:address-book.xml");
        config.connector("tcp").protocol("rsf");

        try (MetContext context = config.buildContext()) {
            // 启动前订阅服务，地址本只为已登记的服务加载地址
            MetBindInfo<EchoService> binding = context.publisher().rsfService(EchoService.class)
                    .register();

            // 启动时加载地址本
            context.start();

            // 调用服务
            EchoService echo = context.getRsfClient().getRemote(binding);
            System.out.println(echo.echo("hello"));
        }
    }
}
```


## 手动加载

若需在启动后加载，在服务引用登记完成后调用：

```java
context.getUpdater().loadAddressBook(EchoService.class.getClassLoader(), "classpath:address-book.xml");
```

## 标记机房

`address` 的 `unit` 标记该提供者所在机房，供[同机房优先](../governance/units.md)选址使用：

```xml
<address-book xmlns="https://www.hasor.net/schema/meteor-address-book">
    <service group="default" name="EchoService" version="1.0.0">
        <address unit="shanghai">rsf://10.0.0.10:2180</address>
        <address unit="beijing">rsf://10.1.0.10:2180</address>
    </service>
</address-book>
```

XML 格式可用 [Schema](/schema/meteor-address-book.xsd) 校验。

## 注意事项

- 一个 `service` 可包含多个 `address`，默认客户端从可用地址中选择提供者；跨主机部署时填写实际提供者地址。
- 地址本不会订阅服务或持续监视文件变化，地址变更需要主动更新，移除已退役目标。
