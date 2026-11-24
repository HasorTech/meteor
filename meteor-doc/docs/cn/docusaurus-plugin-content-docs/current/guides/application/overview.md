---
title: "创建应用"
description: "按项目类型选择依赖，通过 Maven 或 Gradle 引入 Meteor。"
hide_table_of_contents: true
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 创建应用

需要 JDK 17 或以上版本。选择项目类型后，按对应文章添加依赖、发布和调用服务。

## 选择项目

| 项目类型 | 依赖模块 | 使用方式 |
| --- | --- | --- |
| 普通 Java | `meteor` | [普通项目](java.md) |
| Spring | `meteor-spring` | [Spring 整合](spring.md) |
| Spring Boot | `meteor-spring-starter` | [Spring 整合](spring.md) |
| Hasor | `meteor-hasor` | [Hasor 整合](hasor.md) |
| Solon | `meteor-solon` | [Solon 整合](solon.md) |

框架整合模块已包含 `meteor` 依赖，无需重复添加。

## 引入依赖

按是否使用注册中心选择依赖。以下以普通项目的 `meteor` 为例；框架项目将它替换为表中对应的模块。

<Tabs defaultValue="standalone">
<TabItem value="standalone" label="单独使用">

通过代码指定地址或使用地址本直连，无需注册中心依赖。

```xml title="Maven"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="Gradle"
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
```

</TabItem>
<TabItem value="nacos" label="Nacos">

使用 Nacos 进行服务注册与发现。

```xml title="Maven"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-registry-nacos</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="Gradle"
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
implementation 'net.hasor:meteor-registry-nacos:@project.lastReleaseVer@'
```

</TabItem>
<TabItem value="zookeeper" label="ZooKeeper">

使用 ZooKeeper 进行服务注册与发现。

```xml title="Maven"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-registry-zookeeper</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="Gradle"
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
implementation 'net.hasor:meteor-registry-zookeeper:@project.lastReleaseVer@'
```

</TabItem>
<TabItem value="jdbc" label="JDBC">

使用关系数据库保存注册数据；应用还需提供数据源和对应的 JDBC 驱动。

```xml title="Maven"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-registry-jdbc</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="Gradle"
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
implementation 'net.hasor:meteor-registry-jdbc:@project.lastReleaseVer@'
```

</TabItem>
<TabItem value="dubbo" label="Dubbo">

连接已有 Dubbo 服务使用的注册中心，配置见[对接原生 Dubbo 注册中心](../discovery/dubbo.md)。

```xml title="Maven"
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-registry-dubbo</artifactId>
    <version>@project.lastReleaseVer@</version>
</dependency>
```

```groovy title="Gradle"
implementation 'net.hasor:meteor:@project.lastReleaseVer@'
implementation 'net.hasor:meteor-registry-dubbo:@project.lastReleaseVer@'
```

</TabItem>
</Tabs>

同一应用中的 Meteor 模块保持相同版本。其他协议依赖见[协议支持](../protocols/about.md)。
