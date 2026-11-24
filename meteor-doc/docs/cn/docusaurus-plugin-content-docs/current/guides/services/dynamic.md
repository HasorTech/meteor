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
| `parameterTypes` | 方法声明中的参数类型，用于精确匹配 Java 方法。 |
| `parameterObjects` | 实际参数值，顺序与类型数组一致。 |

基本类型使用 `int.class`、`long.class` 等声明类型。仍需先订阅接口；方法名和参数类型必须与该接口一致，协议要求的接口文件也需提前配置。


## 使用发布描述调用

`syncInvokeGeneric` 使用服务 ID、协议和对外方法名发起一问一答的 RPC 调用。连接注册中心时，框架从注册中心选择提供者并取得该提供者的服务描述和协议契约。未配置注册中心时，框架从本地发布表查找服务。调用进程只需安装相应协议的客户端连接器。

```java
String serviceId = "[orders]OrderService-1.0";
Object[] arguments = { "order-42" };
Object result = context.getRsfClient()
        .syncInvokeGeneric(serviceId, "jsonrpc", "findOrder", arguments, 5000);
```

参数按方法声明的顺序传入。结构化参数可使用 `Map<String, Object>`，集合可使用 `List`，返回值可直接作为 Java 值处理。对于通过 `@MetMethod(name = "...")` 指定对外名称的方法，传入该名称。

已知提供者地址时，使用固定地址客户端。框架按服务 ID、协议和该地址查找发布契约。

```java
Object result = context.getRsfClient(providerAddress)
        .syncInvokeGeneric(serviceId, "jsonrpc", "findOrder", arguments, 5000);
```

网关或控制台若已从目录或管理接口取得发布契约，可以用 `withServiceResolver` 为当前客户端提供目标发现来源，再调用同一个 `syncInvokeGeneric`。框架负责把发现结果组装成内部调用数据。

RSF 的发布契约需使用 JSON 序列化。SOAP Encoding 暂支持标量 part。ONC RPC 的自定义 XDR 类型需要提供可解析的契约声明。
