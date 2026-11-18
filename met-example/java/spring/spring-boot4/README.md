# Spring Boot 4 Echo

使用 JDK 17、Spring Boot 4.0.9 和 Spring Framework 7.0。Meteor Starter 自动发布服务和注入代理，消费者通过 `@MetAddress` 直连本机 `28881` 端口。

在 IDEA 中先运行 [ProviderMain](src/main/java/net/hasor/example/integration/ProviderMain.java)，再运行 [ConsumerMain](src/main/java/net/hasor/example/integration/ConsumerMain.java)。也可以从 Meteor 仓库根目录在两个终端分别运行：

```bash
./gradlew -p met-example :java:spring:spring-boot4:bootRun
./gradlew -p met-example :java:spring:spring-boot4:bootRun -PmainClass=net.hasor.example.integration.ConsumerMain
```

消费者输出 `Hello, Meteor!` 后退出。

如需可执行 JAR，先构建提供者，再构建消费者：

```bash
./gradlew -p met-example :java:spring:spring-boot4:bootJar
./gradlew -p met-example :java:spring:spring-boot4:bootJar -PmainClass=net.hasor.example.integration.ConsumerMain
```

在 `met-example/java/spring/spring-boot4/build/libs/` 中分别选择以 `-provider.jar` 和 `-consumer.jar` 结尾的 JAR，用 `java -jar` 启动。
