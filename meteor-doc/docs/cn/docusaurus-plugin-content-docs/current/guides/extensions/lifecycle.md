---
title: "编写应用扩展"
description: "创建上下文独享的扩展，安装启动回调并提供扩展服务。"
---

# 编写应用扩展

需要随应用启动和关闭的资源，可以封装为 `MetExtension`，通过 `MetExtensionFactory` 为每个上下文创建独立实例。

## 实现扩展

下面的扩展提供一个应用独享的调度器，业务可通过扩展服务取得它：

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.hasor.meteor.MetExtension;

public class SchedulerExtension implements MetExtension {
    private ScheduledExecutorService scheduler;

    @Override
    public void beforeStart() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void afterStart() {
        // 需要等待启动阶段完成的操作放在这里
    }

    @Override
    public <T> T getService(Class<T> type) {
        return scheduler != null && type.isInstance(scheduler)
                ? type.cast(scheduler) : null;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
```

## 安装使用

在构建上下文前添加工厂，启动后取得调度器：

```java
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

MetConfiguration config = new MetConfiguration();
config.addExtension(context -> new SchedulerExtension());

try (MetContext context = config.buildContext()) {
    context.start();
    ScheduledExecutorService scheduler = context.getExtension(ScheduledExecutorService.class);
    scheduler.schedule(() -> System.out.println("扩展任务已执行"), 0, TimeUnit.SECONDS).get();
}
```

## 注意事项

- 工厂可复用，每次 `create(context)` 都应创建当前上下文独享的扩展。工厂返回前失败时，自行释放尚未交给上下文的资源。
- `beforeStart()` 或 `afterStart()` 失败会终止启动。`close()` 需支持部分初始化和重复清理，不应调用 `context.close()`。
- `getExtension(类型)` 找不到时返回 `null`，多个扩展提供同一类型时抛出异常。`getService()` 只查询，不在查询时创建资源。
- 只需接收事件时使用[生命周期监听](../services/lifecycle/listeners.md)，无需编写扩展。
