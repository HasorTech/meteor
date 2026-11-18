---
title: "泛化调用"
description: "显式指定方法名、参数类型和值发起调用。"
---

# 泛化调用

## 工作方式

泛化调用通过方法名、参数类型和参数值指定要执行的方法，无需创建接口代理。Meteor 在已登记的接口中查找方法，再发送请求；示例使用 `syncInvoke` 同步取得结果。

## 适用场景

适合调试工具等需要按方法名发起调用的程序，也可以异步读取结果或通过回调接收结果。

## 示例

`client` 为已创建的客户端，`service` 为订阅时返回的 `MetBindInfo`，接口声明 `String echo(String text)`。

```java
Class<?>[] parameterTypes = { String.class };
Object[] arguments = { "hello" };
Object result = client.syncInvoke(service, "echo", parameterTypes, arguments);
```

## 补充说明

### 参数匹配

| 参数 | 含义 |
| --- | --- |
| `service` | 订阅时返回的 `MetBindInfo`。 |
| `methodName` | Java 接口方法名。 |
| `parameterTypes` | 方法声明中的参数类型，用于匹配重载。 |
| `parameterObjects` | 实际参数值，顺序与类型数组一致。 |

基本类型使用 `int.class`、`long.class` 等声明类型。仍需先订阅接口；方法名和参数类型必须与该接口一致，协议要求的接口文件也需提前配置。
