# Spring XML 显式注册 Bean

使用 JDK 17 和 Spring 6。提供者的 XML 显式声明 Echo 实现和 Meteor 上下文。消费者用 `ServiceBean` 声明 `EchoService` 接口 Bean。Spring 返回远程代理，直连本机 `29879` 端口。

在 IDEA 中先运行 [XmlProviderMain](src/main/java/net/hasor/example/spring/XmlProviderMain.java)，再运行 [XmlConsumerMain](src/main/java/net/hasor/example/spring/XmlConsumerMain.java)。也可以从 Meteor 仓库根目录在两个终端分别运行：

```bash
./gradlew -p met-example :java:spring:spring-xml1:run
./gradlew -p met-example :java:spring:spring-xml1:run -PmainClass=net.hasor.example.spring.XmlConsumerMain
```

消费者输出 `Hello, Meteor!` 后退出。

配置见[提供者 XML](src/main/resources/spring-provider.xml)和[消费者 XML](src/main/resources/spring-consumer.xml)。
