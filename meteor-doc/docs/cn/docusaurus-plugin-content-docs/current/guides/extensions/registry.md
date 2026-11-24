---
title: "扩展注册中心"
description: "通过 RegistryConfiguration 和 StorageAccess 接入新的注册存储。"
---

# 扩展注册中心

需要用新的存储保存 Meteor 的服务登记和治理配置时，实现 `StorageAccess`，再用 `RegistryConfiguration` 提供接入配置。业务应用和控制台继续使用现有发现、治理和管理 API。

## 提供配置入口

以下是配置骨架，`ExampleStorage` 需实现 `StorageAccess`，负责连接目标存储：

```java
import java.util.concurrent.Callable;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.meteor.registry.admin.RegistryInfo;
import net.hasor.meteor.registry.shared.storage.StorageAccess;

public class ExampleRegistryConfiguration extends RegistryConfiguration {
    private String address;

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String getProvider() {
        return "example-registry";
    }

    @Override
    protected Callable<StorageAccess> createStorageOpener() {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
        String target = address; // 捕获本次配置，不在这里打开连接
        return () -> new ExampleStorage(target);
    }

    @Override
    protected RegistryInfo describeStorage() {
        RegistryInfo info = super.describeStorage();
        // 可补充不含密码、token 等凭据的存储描述
        return info;
    }
}
```

每次调用返回的 opener 都应创建独立存储会话，应用登记和独立管理可能同时打开会话。

## 实现存储

`StorageAccess` 的实现包括以下部分：

- **目录查询**：应用、实例、服务、方法及成员的查询和分页。
- **运行登记**：登记实例和服务；服务撤销、会话关闭或实例失效后，不再返回可用成员。持久服务声明和治理配置不能随运行登记一起删除。
- **治理配置**：按全局、实例、服务、实例下服务保存开关、规则和元信息；条件更新使用 revision，冲突不能静默覆盖。
- **变更订阅**：`watchProviders()` 通知提供者变化，`watchConfiguration()` 通知治理变化，返回可取消的订阅句柄。
- **会话关闭**：取消当前会话的订阅，撤回当前登记，释放自己创建的资源。

首次提供者订阅需异步通知。读取失败不能当作记录删除；存储没有原生通知能力时，需要自行实现变化检测。方法的返回值、原子更新和删除要求以 `StorageAccess` 的接口注释为准。

## 接入应用

```java
MetConfiguration config = new MetConfiguration();
ExampleRegistryConfiguration registry = new ExampleRegistryConfiguration();
registry.setAddress("registry.example.com:9000");
config.addExtension(registry);

try (MetContext context = config.buildContext()) {
    // 发布或订阅服务
    ...
    context.start();
    ...
}
```

这个入口通过 `addExtension()` 安装，不通过 SPI 注册。配置管理凭据后，还可使用 `buildAdmin()` 打开独立管理会话，调用者负责关闭，见[注册中心 API](../governance/api/registry.md)。

## 接入外部目录

如果目标是读取其他框架已经保存的注册数据，不能直接按 Meteor 的存储格式读写。需要把对方服务和地址转换成 Meteor 的订阅结果，参考[对接 Dubbo 注册生态](../discovery/dubbo.md)。此类适配不代表对方支持 Meteor 的全部治理能力。
