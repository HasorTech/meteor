---
title: "直连管理"
description: "通过管理 RPC 直接查看和修改业务实例，无需连接注册中心。"
---

# 直连管理

已知业务实例的 RPC 地址时，可用 `TargetAdmin` 直接查询实例、启停服务、修改规则和元信息，无需连接注册中心。两端引入 `meteor-registry`，管理 token 保持一致。

## 被管理实例

在应用创建上下文前安装离线管理扩展，继续按原方式发布业务服务：

```java
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryConfiguration;

MetConfiguration config = new MetConfiguration();
config.setApplicationName("echo-provider");
config.connector("tcp").bind("0.0.0.0", 2180).protocol("rsf");

RegistryConfiguration management = new RegistryConfiguration();
management.setOnline(false); // 不连接注册中心，仍提供管理 RPC
management.setManagementToken("your-management-token");
config.addExtension(management);
```

随后用 `config.buildContext()` 创建上下文，发布服务并启动。已接入 Nacos、ZooKeeper 或 JDBC 时，直接在已有注册配置上设置管理 token，不再添加第二个扩展；配置见[管理认证](../../services/security/management.md)。

## 管理端

管理端创建出站上下文，通过 RPC 地址选择实例，按完整服务 ID 选择服务：

```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.meteor.registry.TargetAdmin;

MetConfiguration config = new MetConfiguration();
config.setApplicationName("instance-manager");
config.connector("tcp").protocol("rsf");

RegistryConfiguration management = new RegistryConfiguration();
management.setOnline(false);
management.setManagementToken("your-management-token");
config.addExtension(management);

try (MetContext context = config.buildContext()) {
    context.start();

    TargetAdmin admin = context.getExtension(TargetAdmin.class);
    InterAddress address = new InterAddress("rsf://rpc.example.com:2180");

    // 查询目标实例
    var instance = admin.getInstanceAdminByAddress(address);
    var info = instance.instance();
    System.out.println(info.getApplicationName() + " / " + info.getInstanceId());

    // 只管理目标实例中的这个服务
    var service = admin.getServiceAdminByAddress(address, "[default]EchoService-1.0.0");
    service.disable();
    System.out.println(service.isEnable());
    service.enable();
}
```

操作直接在目标进程执行，不保存到注册中心，重启后恢复启动配置；在线实例后续还可能被注册中心配置覆盖。规则通过 `updateRule`、`resetRule` 修改，不能使用依赖注册存储的 `enableRule`、`disableRule`。`TargetAdmin` 借用当前上下文，随上下文释放。
