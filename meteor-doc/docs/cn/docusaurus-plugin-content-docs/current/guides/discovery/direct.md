---
title: "使用固定地址"
description: "在消费者代码中指定提供者地址，无需接入注册中心。"
mdx:
  format: mdx
---

# 使用固定地址

在消费者代码中指定提供者地址，无需接入注册中心。

## 消费者调用

假设目标服务已发布，消费者订阅已有的 `EchoService` 接口，通过 `appendStaticAddress` 添加固定地址，再用默认客户端调用。

```java
import java.util.List;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.address.InterAddress;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用和接入协议
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");
        config.connector("tcp").protocol("rsf");

        try (MetContext context = config.buildContext()) {
            // 启动并订阅服务
            context.start();
            MetBindInfo<EchoService> binding = context.publisher().rsfService(EchoService.class)
                    .register();

            // 添加固定地址，多个目标可继续加入 List
            context.getUpdater().appendStaticAddress(binding.getBindID(), List.of(
                    new InterAddress("rsf", "127.0.0.1", 2180)));

            // 从可用地址中选择提供者并调用
            EchoService echo = context.getRsfClient().getRemote(binding);
            System.out.println(echo.echo("hello"));
        }
    }
}
```

## 临时指定地址

需要临时调用某个实例时，可在已订阅服务后使用带地址的客户端。此地址仅用于该客户端，不加入服务的静态地址列表。

```java
InterAddress target = new InterAddress("rsf", "10.0.0.12", 2180);
EchoService echo = context.getRsfClient(target).getRemote(binding);
System.out.println(echo.echo("hello"));
```

## 注意事项

- 示例使用 RSF；跨主机调用时，将 `127.0.0.1` 改为提供者地址，并确保端口可访问。
- `appendStaticAddress` 支持一个或多个地址，默认客户端从可用目标中选址；地址变化需要主动更新。
- 通过配置文件维护地址见[使用地址本](address-book.md)。
