---
title: "定义服务"
description: "声明服务接口，以分组、名称和版本确定服务 ID。"
---

# 定义服务

## 服务接口

用接口定义方法，用 `@MetService` 设置服务的分组、名称和版本：

```java
import net.hasor.meteor.annotation.MetService;

@MetService(group = "billing", name = "EchoService", version = "1.0.0")
public interface EchoService {
    String echo(String text);
}
```

接口通常放在独立模块中，供提供者和消费者共用。集合需声明元素类型。

同一服务版本中的对外方法名必须唯一。默认使用 Java 方法名，可以通过 `@MetMethod(name = "...")` 给操作指定对外名称。发布时发现同名方法会报错，即使参数数量或类型不同。已有 Java 重载可以分别命名，例如 `lookupById` 和 `lookupByName`。

## 服务坐标

分组 `group`、名称 `name` 和版本 `version` 共同标识一个服务，称为服务坐标，服务 ID 格式为：

```text
[group]name-version
```

上例为 `[billing]EchoService-1.0.0`，提供者和消费者的分组、名称、版本必须一致。

| 属性 | 用途 | 未指定时 |
| --- | --- | --- |
| `group` | 区分业务分组。 | 应用默认分组，初始为 `default`。 |
| `name` | 标识服务名称。 | 接口的完整类名。 |
| `version` | 区分同一服务的版本。 | 应用默认版本，初始为 `1.0.0`。 |

发布或订阅时，Builder 的 `group`、`name`、`version` 可覆盖注解配置。不同分组或版本可并行发布，版本按字符串精确匹配。IP、端口和协议不属于服务坐标。`@MetService.id` 是协议使用的数字编号，不能代替分组、名称和版本，用法见 [ONC RPC](../protocols/oncrpc.md)。下一步：[发布服务](publish.md)、[订阅服务](consume.md)。已有 `.proto` 等文件见[对应协议的接口文件说明](../protocols/about.md)。

## 排除方法

不向 RPC 客户端公开的方法，在服务接口上加 `@MetIgnore`：

```java
import net.hasor.meteor.annotation.MetIgnore;

public interface EchoService {
    String echo(String text);

    @MetIgnore
    String diagnostics();
}
```

按接口发布时，仅在实现类方法上加注解不会排除接口方法。子接口重新声明该方法时，也需重新加注解。
