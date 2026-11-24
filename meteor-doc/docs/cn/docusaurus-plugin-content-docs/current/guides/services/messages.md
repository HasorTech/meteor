---
title: "单向消息"
description: "调用不依赖业务返回值的通知方法。"
---

# 单向消息

## 工作方式

单向消息只发送业务请求，不接收业务返回值。提供者执行通知方法，消费者不等待远端业务结果，但选择地址、建立连接或发送请求时仍可能失败。

## 适用场景

适合允许不确认执行结果的状态通知。需要确认处理成功、读取返回值或保证可靠投递时，应选择有响应的调用或消息队列。

## 示例

用 `@MetMessage` 将通知方法声明为消息模式：

```java
import net.hasor.meteor.annotation.MetMessage;

public interface NotificationService {
    @MetMessage
    void notify(String message);
}
```

提供者注册实现对象，消费者订阅同一接口后发送通知：

```java
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;

public class NotificationCall {
    public void send(MetClient client, MetBindInfo<NotificationService> service) {
        NotificationService notifications = client.getRemote(service);
        notifications.notify("order-created");
    }
}
```

## 补充说明

### 配置范围

| 方式 | 范围 |
| --- | --- |
| 方法上的 `@MetMessage` | 当前方法。 |
| 接口上的 `@MetMessage` | 整个服务。 |
| Builder 的 `asMessage()` | 当前发布或订阅的服务。 |

消息模式不使用业务返回值，发送成功不能证明远端业务已完成。它不会保存消息供离线服务稍后处理。使用其他 SDK 时，还需按该协议配置 oneway（无业务响应）方法，见[协议支持](../protocols/about.md)。
