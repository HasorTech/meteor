---
title: "使用 API"
description: "选择只修改当前应用，或通过注册中心保存治理配置。"
---

# 使用 API

管理 API 包括以下接口。

## 本地管理 API

- `MetContext`：当前应用的上下文，查询已登记的服务，控制业务监听上线、下线。
- `MetUpdater`：通过 `context.getUpdater()` 获取，修改路由、限流、服务启停和实例阻断状态，查询或更新提供者地址。
- `MetBindInfo`：发布、订阅服务时返回，也可通过 `context.getServiceInfo(...)` 获取，用于读取服务信息和维护元信息。

## 注册中心管理 API

- `RegistryAdmin`：通过注册中心配置的 `buildAdmin()` 创建，是以下管理接口的获取入口，使用完需关闭。
- `MetClusterAdmin`：通过 `getClusterAdmin()` 获取，查询应用、实例和服务目录，维护集群默认规则和元信息。
- `MetInstanceAdmin`：通过 `getInstanceAdmin(instanceId)` 获取，查看实例及其服务，维护实例规则，阻断或恢复实例。
- `MetServiceAdmin`：通过 `getServiceAdmin(serviceId)` 获取，查询提供者、消费者和方法，维护服务规则、启停状态和元信息；传入 `instanceId` 时只管理该实例下的服务。

## 直连管理 API

`TargetAdmin` 通过 RPC 地址取得目标实例或服务的管理对象，直接修改对端进程，不读写注册存储。使用方式见[直连管理](api/direct.md)。

## 使用指引

- [本地 API](api/local.md)，通过消费者上下文设置路由和限流，无需注册中心。
- [注册中心API](api/registry.md)，校验、保存、启用和恢复规则。
- [直连管理](api/direct.md)，通过 RPC 管理指定业务实例，无需注册中心。
- [服务状态 API](api/state.md)，启用或停用服务，阻断或恢复实例。
- [元信息 API](api/metadata.md)，按键查询、修改和删除属性。

脚本写法见[治理能力](capabilities/about.md)。
