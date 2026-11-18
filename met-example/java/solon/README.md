# Solon Echo

服务端由 Solon 发布 [EchoService](src/main/java/net/hasor/example/integration/EchoService.java)，客户端注入代理，通过[地址本](src/main/resources/address-book.xml)直连本机 `28870` 端口。

1. 在 IDEA 中打开 `met-example`，运行 [ProviderMain](src/main/java/net/hasor/example/integration/ProviderMain.java)。
2. 运行 [ConsumerMain](src/main/java/net/hasor/example/integration/ConsumerMain.java)，输出 `Hello, Meteor!` 后退出。

也可从 Meteor 仓库根目录打包：

```bash
./gradlew -p met-example :java:solon:clean :java:solon:shadowJar
./gradlew -p met-example :java:solon:shadowJar -PmainClass=net.hasor.example.integration.ConsumerMain
java -jar met-example/java/solon/build/libs/solon-*-provider.jar
# 另开终端
java -jar met-example/java/solon/build/libs/solon-*-consumer.jar
```
