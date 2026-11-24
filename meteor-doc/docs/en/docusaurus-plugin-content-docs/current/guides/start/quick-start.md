---
title: Quick start
description: Publish a JSON-RPC echo service and call it from Java or with curl.
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Quick start

Publish a JSON-RPC echo service, then call it from the Meteor Java client or with curl to get `Hello, Meteor!`.

## Prerequisites {#准备环境}

Use JDK 17 or later and Maven or Gradle. The provider listens on local port `8080`. The consumer uses a fixed address and runs without a registry.

This guide uses `@project.lastReleaseVer@`. If you use a development snapshot, first make sure it is available in your Maven repository. The examples support both the local Maven repository and Maven Central.

## Add dependencies {#引入依赖}

Create a plain Java project and apply the configuration for your build tool. The JSON-RPC module pulls in Meteor core as a transitive dependency.

<Tabs groupId="quick-start-build-tool">
<TabItem value="maven" label="Maven" default>

```xml title="pom.xml"
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>meteor-quick-start</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>net.hasor</groupId>
            <artifactId>meteor-protocol-jsonrpc</artifactId>
            <version>@project.lastReleaseVer@</version>
        </dependency>
    </dependencies>
</project>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy title="build.gradle"
plugins { id 'java' }
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation 'net.hasor:meteor-protocol-jsonrpc:@project.lastReleaseVer@'
}
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
```

</TabItem>
</Tabs>

## Publish the service {#发布服务}

Define the `EchoService` interface in your project.

```java title="Service interface"
public interface EchoService {
    String echo(String text);
}
```

Create `ProviderMain` to implement the interface and publish it over JSON-RPC.

```java title="Provider"
import java.util.concurrent.CountDownLatch;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ProviderMain {
    public static void main(String[] args) throws Exception {
        // Configure the protocol and listening port.
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-provider");
        config.connector("http").bind("127.0.0.1", 8080)
                .protocol("jsonrpc").contextPath("/jsonrpc");

        try (MetContext context = config.buildContext()) {
            context.start();

            // Publish the service with the JSON-RPC alias echo.
            context.publisher().rsfService(EchoService.class, text -> "Hello, " + text + "!")
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo")
                    .register();
            System.out.println("Service listening on port 8080");

            // Keep the provider process running.
            new CountDownLatch(1).await();
        }
    }
}
```

Run `ProviderMain.main()`. Once the startup message appears, leave the process running.

## Call the service {#调用服务}

<Tabs groupId="quick-start-client">
<TabItem value="meteor" label="Meteor (Java)" default>

In the same project, create `ConsumerMain`. Reuse the `EchoService` interface to make a call at a fixed address.

```java title="Consumer"
import java.util.List;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // Configure the protocol for outgoing calls.
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");
        config.connector("http").protocol("jsonrpc").contextPath("/jsonrpc");

        try (MetContext context = config.buildContext()) {
            context.start();

            // Subscribe to the service and add the provider’s fixed address.
            MetBindInfo<EchoService> binding = context.publisher().rsfService(EchoService.class)
                    .protocol("jsonrpc").aliasName("jsonrpc", "echo")
                    .register();
            context.getUpdater().appendStaticAddress(binding.getBindID(),
                    List.of(new InterAddress("jsonrpc", "127.0.0.1", 8080)));

            // Get the interface proxy and invoke the method.
            EchoService echo = context.getRsfClient().getRemote(binding);
            System.out.println(echo.echo("Meteor"));
        }
    }
}
```

Run `ConsumerMain.main()`. It prints the following line.

```text
Hello, Meteor!
```

</TabItem>
<TabItem value="curl" label="HTTP (curl)">

With the provider still running, send a JSON-RPC request from your terminal.

```bash
curl -sS http://127.0.0.1:8080/jsonrpc/default/1.0.0 \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"echo.echo","params":["Meteor"]}'
```

The response returns `Hello, Meteor!` in `result`.

```json
{"jsonrpc":"2.0","id":1,"result":"Hello, Meteor!"}
```

`echo.echo` combines the service alias `echo` with the method name `echo`. In the URL, `default` is the default service group and `1.0.0` is the default version. Clients in other languages can send the same HTTP request.

</TabItem>
</Tabs>

## Next steps {#接下来}

- [Build an application](../application/overview.md) to integrate Meteor in a plain Java project or with Spring, Hasor, or Solon.
- [Publish over multiple protocols](../services/multiple/about.md) to add endpoints to the same service.
- [Use service discovery](../discovery/about.md) to switch from a fixed address to an address book or registry.
