# Spring Boot 2 Echo

使用 JDK 17、Spring Boot 2.7.18 和 Spring Framework 5.3。Meteor Starter 自动发布服务和注入代理，消费者通过 `@MetAddress` 直连本机 `28879` 端口。

在 IDEA 中先运行 [ProviderMain](src/main/java/net/hasor/example/integration/ProviderMain.java)，再运行 [ConsumerMain](src/main/java/net/hasor/example/integration/ConsumerMain.java)。也可以从 Meteor 仓库根目录在两个终端分别运行：

```bash
./gradlew -p met-example :java:spring:spring-boot2:run
./gradlew -p met-example :java:spring:spring-boot2:run -PmainClass=net.hasor.example.integration.ConsumerMain
```

消费者输出 `Hello, Meteor!` 后退出。

该模块使用 Gradle 的 `application` 插件启动，可用 `:java:spring:spring-boot2:installDist` 打包。
