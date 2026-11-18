---
title: "流式调用"
description: "在一次调用中逐条发送或接收消息，持续处理业务结果。"
---

# 流式调用

## 工作方式

服务端通过方法的参数和返回类型声明消息流：返回 `Flow.Publisher<T>` 表示逐条返回结果，参数为 `Flow.Publisher<T>` 表示逐条接收请求。客户端仍通过服务代理发起调用，再订阅返回的流或传入请求流，直到正常结束、失败或取消。

| 形式 | 请求与响应 |
| --- | --- |
| 服务端流 | 发送一个请求，逐条接收多个响应。 |
| 客户端流 | 逐条发送多个请求，结束后接收一个响应。 |
| 双向流 | 请求和响应都是消息流，两端可持续发送和接收。 |

异步和回调调用通常处理一次调用的单个结果；流式调用处理同一次调用中的多条消息。异步调用不会自动变成流式调用。

## 适用场景

适合订单进度推送、分批返回查询结果、持续上传采集数据等场景。仅返回一条结果时，普通调用更直接；需要持久化、离线积压或可靠投递时，应使用相应的消息系统。

## 示例

以下以“客户端查询订单进度，服务端逐条返回状态”为例。两端先配置支持服务端流的协议及相同的消息编码，具体配置见下方协议说明。`provider`、`consumer` 分别是服务端和客户端应用的 `MetContext`，创建方式见[创建应用](../application/overview.md)。

### 服务端

接口返回 `Flow.Publisher<String>`，表示一次订单查询会返回多条状态。实现类逐条返回状态：

```java
import java.util.List;
import java.util.concurrent.Flow;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.rpc.MetCaller;

@MetService(name = "OrderProgressService")
public interface OrderProgressService {
    Flow.Publisher<String> watch(String orderId);
}

public class OrderProgressServiceImpl implements OrderProgressService {
    @Override
    public Flow.Publisher<String> watch(String orderId) {
        // 提供消息源，客户端按需求逐条接收
        return MetCaller.fromIterable(List.of(
                orderId + ":created",
                orderId + ":paid",
                orderId + ":shipped"));
    }
}
```

在服务端注册实现，使用应用已配置的协议：

```java
provider.publisher().rsfService(OrderProgressService.class, new OrderProgressServiceImpl())
        .register();
```

应用启动后即可远程调用。示例返回三条状态，实际业务可在订单状态变化时返回新消息；也可通过 Spring、Hasor 等容器自动发布服务。

### 客户端

客户端先订阅相同接口，再取得服务代理。提供者地址由已配置的注册中心或地址本提供：

```java
import java.util.concurrent.Flow;
import net.hasor.cobble.concurrent.future.Future;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;
import net.hasor.meteor.rpc.MetCaller;

// 1. 订阅服务，两端使用相同的协议和编码
MetBindInfo<OrderProgressService> binding = consumer.publisher()
        .rsfService(OrderProgressService.class)
        .register();

// 2. 取得远程服务代理
MetClient client = consumer.getRsfClient();
OrderProgressService orders = client.getRemote(binding);

// 3. 发起一次查询，取得本次调用的结果流
Flow.Publisher<String> progress = orders.watch("ORDER-1001");

// 4. 订阅结果流，每收到一条状态就处理一条
Future<Void> completion = MetCaller.consume(progress, System.out::println);
completion.onFailed(done -> done.getCause().printStackTrace());
```

客户端依次输出：

```text
ORDER-1001:created
ORDER-1001:paid
ORDER-1001:shipped
```

`watch` 发起一次调用，三条状态属于这一次调用的响应，不是客户端反复查询。`consume` 每处理完一条再申请下一条，正常结束时完成 `completion`，中途失败时触发失败处理。客户端应用应保持运行直到消费完成；页面关闭或不再需要结果时，可调用 `completion.cancel()` 取消订阅。

## 补充说明

### 协议限制

流式调用使用统一的 `Flow.Publisher<T>` 接口，支持范围取决于底层协议：

| 协议 | 流式支持与配置入口 |
| --- | --- |
| [gRPC](../protocols/grpc.md) | 服务端流、客户端流和双向流。 |
| [Connect](../protocols/connect.md) | 服务端流、客户端流和双向流；双向流要求 HTTP/2。 |
| [Triple](../protocols/triple.md) | 支持流式调用，可用形式取决于 HTTP 模式和消息编码方式。 |
| [gRPC-Web](../protocols/grpc-web.md) | 服务端流，不支持客户端流和双向流。 |

选择协议后，按对应文档配置 HTTP 版本、`streamBody` 和消息编码；RSF/1 不支持流式调用，普通 HTTP 方法也不能直接作为消息流使用。

`Flow.Publisher<T>` 必须保留明确的消息类型，不能省略泛型或使用 `Publisher<Object>`。使用其他 SDK 时，双方 proto 中的 `stream` 声明、消息类型和编码也须一致。

### 处理消息

| API | 用途 |
| --- | --- |
| `MetCaller.fromIterable` | 按客户端请求的数量，逐条返回集合中的元素。 |
| `MetCaller.fromIterator` | 客户端请求消息时，创建迭代器并逐条返回元素。 |
| `MetCaller.consume` | 按顺序消费，正常结束或失败时完成返回的 Future。 |
| `completion.cancel()` | 取消订阅，不再请求后续消息。 |

消息处理不宜长时间阻塞。取消订阅不会撤销已执行的操作；中途失败时，应记录已处理进度，避免重试后重复处理。
