# Spring XML 包扫描

使用 JDK 17 和 Spring 6。提供者用 `context:component-scan` 找到 Echo 实现，双方用 `ServiceScannerConfigurer` 扫描服务接口。消费者取得自动注册的代理，通过地址本直连本机 `29880` 端口。

在 IDEA 中先运行 [XmlProviderMain](src/main/java/net/hasor/example/spring/XmlProviderMain.java)，再运行 [XmlConsumerMain](src/main/java/net/hasor/example/spring/XmlConsumerMain.java)。也可以从 Meteor 仓库根目录在两个终端分别运行：

```bash
./gradlew -p met-example :java:spring:spring-xml2:run
./gradlew -p met-example :java:spring:spring-xml2:run -PmainClass=net.hasor.example.spring.XmlConsumerMain
```

消费者输出 `Hello, Meteor!` 后退出。

配置见[提供者 XML](src/main/resources/spring-provider.xml)和[消费者 XML](src/main/resources/spring-consumer.xml)。
