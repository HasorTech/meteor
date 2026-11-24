---
title: "隐式传参"
description: "不修改方法参数，随请求传递请求编号等信息。"
---

# 隐式传参

隐式传参通过字符串键值传递请求编号、调用来源等信息，无需修改方法参数。以下将订单请求编号传到报价服务，用于关联两端日志。

## 发送参数

`context` 为应用的 `MetContext` 对象，`PriceService` 已订阅，地址由注册中心或地址本提供。

```java
import java.util.Map;
import net.hasor.meteor.MetClient;

String requestId = "order-001";
MetClient client = context.getRsfClient()
        .withOptions(Map.of("request-id", requestId));
PriceService prices = client.getRemote(context.getServiceInfo(PriceService.class));

System.out.println("订单服务 request-id=" + requestId);
String price = prices.quote("SKU-1001");
```

`withOptions` 返回独立客户端，其代理调用均携带这些值，不影响原客户端。请求编号变化时，重新创建对应客户端。

## 读取参数 {#read-options}

业务方法直接使用 Local 工具，无需拦截器：

```java
import net.hasor.meteor.rpc.MetRequestLocal;
import net.hasor.meteor.rpc.MetResponseLocal;

public class PriceServiceImpl implements PriceService {
    @Override
    public String quote(String sku) {
        String requestId = new MetRequestLocal().getOption("request-id");
        System.out.println("报价服务 request-id=" + requestId + ", sku=" + sku);
        new MetResponseLocal().addOption("processed-by", "price-service");
        return "100.00";
    }
}
```

两端日志包含相同编号：

```text
订单服务 request-id=order-001
报价服务 request-id=order-001, sku=SKU-1001
```

## 返回参数

上例用 `MetResponseLocal` 写入 `processed-by`，消费者通过完整响应读取：

```java
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.MetFuture;
import net.hasor.meteor.MetResponse;

MetFuture future = client.asyncInvoke(context.getServiceInfo(PriceService.class), "quote",
        new Class<?>[] { String.class }, new Object[] { "SKU-1001" });
MetResponse response = future.get(3, TimeUnit.SECONDS);
System.out.println("processed-by=" + response.getOption("processed-by"));
System.out.println("报价=" + response.getData());
```

普通接口代理只返回业务结果，响应附加信息需通过 `MetResponse` 读取。

Local 工具仅在当前调用线程内有效，不向异步线程或下游调用自动传播。发送新请求仍使用 `withOptions`；`setAttribute` 仅保存本地对象，不传到远端。

其他协议的传递方式见[协议说明](../protocols/about.md)。
