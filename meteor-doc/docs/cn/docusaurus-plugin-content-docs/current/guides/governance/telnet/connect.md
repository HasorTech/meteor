---
title: "接入治理 Telnet"
description: "为业务应用引入 meteor-telnet，启用独立的 Telnet 管理端口。"
---

# 接入治理 Telnet

业务应用引入 `meteor-telnet` 后，可通过 Telnet 管理当前实例。管理集群使用 Console，见[无头模式](../../console/headless.md)。

## 引入依赖

```xml title="Maven"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-telnet</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="Gradle"
implementation 'net.hasor:meteor-telnet:@project.lastReleaseVer@'
```

## 启用监听

在创建上下文前添加 `MetTelnetConfiguration`，监听随上下文启动和关闭：

```java
import java.util.concurrent.CountDownLatch;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.tconsole.MetTelnetConfiguration;

public class Application {
    public static void main(String[] args) throws Exception {
        MetConfiguration config = new MetConfiguration();

        // 添加独立的 Telnet 管理端口
        config.addExtension(new MetTelnetConfiguration()
                .setHost("127.0.0.1")
                .setPort(2182)
                .setPassword("your-password"));

        try (MetContext context = config.buildContext()) {
            context.start();
            // 在此发布或订阅业务服务
            new CountDownLatch(1).await(); // 保持示例运行
        }
    }
}
```

已有应用只需在 `buildContext()` 前添加上述扩展。


## 配置项


通过 `MetTelnetConfiguration` 的 setter 设置：

| 方法 | 默认值 | 用途 |
| --- | --- | --- |
| `setHost` | `127.0.0.1` | 管理监听地址，非回环地址必须设置密码 |
| `setPort` | `2182` | 管理端口，独立于业务端口 |
| `setPassword` | 未设置 | Telnet 登录密码 |
| `setMaxAuthenticationFailures` | `3` | 密码错误达到次数后关闭连接；`-1` 表示不限次数 |
| `setMaxSessions` | `8` | 最大连接数 |
| `setMaxInputBytes` | `65536` | 最大输入字节数，包含多行 XML |
| `setIdleTimeoutMillis` | `300000` | 空闲连接超时，单位为毫秒 |
| `setOperationTimeoutMillis` | `10000` | 注册中心写入后等待当前应用同步的超时，单位为毫秒 |

启动应用后，按[连接和认证](about.md#连接和认证)连接管理端口。
