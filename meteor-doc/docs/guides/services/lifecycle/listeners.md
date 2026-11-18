---
title: "生命周期监听"
description: "通过 MetContextListener 接收上下文状态和服务登记、移除事件。"
---

# 生命周期监听

实现 `MetContextListener`，通过 `context.subscribe()` 监听当前上下文的状态和服务变化，可用于记录运行日志或通知应用内组件。

## 注册监听器

在 `start()` 前订阅，才能收到启动事件：

```java
import net.hasor.meteor.MetContextListener;

MetContextListener listener = event -> {
    if (event.getServiceInfo() == null) {
        // 上下文事件：启动、上下线、关闭
        System.out.println("context=" + event.getType());
    } else {
        // 服务事件：登记、移除，包含提供者和消费者
        System.out.println("event=" + event.getType() + ", service=" + event.getServiceId());
    }
};

var subscription = context.subscribe(listener);
context.start();
```

## 事件类型

- `STARTED`：上下文启动完成。
- `ONLINE`、`OFFLINE`：调用 `online()`、`offline()` 后，在线状态发生变化。
- `REGISTERED`、`REMOVED`：服务登记完成或已移除，通过 `getServiceInfo()` 获取服务信息。
- `CLOSED`：上下文进入关闭状态，资源清理尚未完成。

启动时自动上线只通知 `STARTED`，不另发 `ONLINE`。事件只通知订阅后的变化，不补发历史事件。

## 取消监听

```java
subscription.cancel(); // 不再接收后续事件，不打断正在执行的回调
```

回调同步执行，应尽快返回。耗时操作由业务自行调度；启动前的资源准备见[优雅上下线](online.md)，需要参与扩展初始化时见[编写应用扩展](../../extensions/lifecycle.md)。
