---
title: "回调调用"
description: "调用完成或失败时，通过回调处理结果。"
---

# 回调调用

## 工作方式

回调调用提交请求时同时注册结果处理逻辑，业务无需主动读取 Future。调用成功时执行 `completed`，失败时执行 `failed`。

## 适用场景

适合收到结果后再触发后续步骤，例如报价完成后更新查询状态。后续逻辑放在回调中，发起调用的方法返回时，远程业务可能尚未完成。

## 示例

`service` 为订阅时返回的 `MetBindInfo`，接口声明 `String echo(String text)`。

```java
import net.hasor.cobble.concurrent.future.FutureCallback;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;

public class CallbackEchoCall {
    public void call(MetClient client, MetBindInfo<?> service) {
        FutureCallback<Object> callback = new FutureCallback<Object>() {
            @Override
            public void completed(Object result) {
                System.out.println("调用完成：" + result);
            }

            @Override
            public void failed(Throwable failure) {
                failure.printStackTrace();
            }
        };
        Class<?>[] parameterTypes = { String.class };
        Object[] arguments = { "hello" };
        client.callbackInvoke(service, "echo", parameterTypes, arguments, callback);
    }
}
```

## 补充说明

### 选择回调内容

| API | 回调类型 |
| --- | --- |
| `callbackInvoke` | `FutureCallback<Object>`，返回业务结果。 |
| `callbackRequest` | `FutureCallback<MetResponse>`，返回完整响应。 |

回调中的耗时操作应交给其他线程处理。回调调用改变结果接收方式，不会把普通方法变成[流式调用](streaming.md)。回调完成前不要关闭 `MetContext`。
