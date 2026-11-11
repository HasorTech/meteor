/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.direct;
import java.io.IOException;
import java.util.Arrays;
import net.hasor.example.OrderDemoService;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;

/** 一个 HTTP 挂载点下的默认、指定和多个 SOAP 服务子路径；其他协议继续复用同一业务服务。 */
public final class ContextMountExample {
    private ContextMountExample() {
    }

    public static MetConfiguration server(String host, int tcpPort, int httpPort) {
        return DirectRpcExample.server(host, tcpPort, httpPort);
    }

    public static MetConfiguration client() {
        return DirectRpcExample.client();
    }

    public static MetBindInfo<OrderDemoService> subscribe(MetContext context, String childPath) throws IOException {
        return DirectRpcExample.subscription(context).contextPathByPrefix("soap", childPath).register();
    }

    /** 不传子路径时直接使用 /soap；指定子路径后在 /soap 下发布 SOAP 服务。 */
    public static MetBindInfo<OrderDemoService> publish(MetContext context, OrderDemoService implementation, //
            String... soapContextPaths) throws IOException {
        var publication = DirectRpcExample.publication(context, implementation);
        if (soapContextPaths.length == 0) {
            publication.contextPathByPrefix("soap", "/");
        } else {
            String primary = soapContextPaths[0];
            String[] additional = Arrays.copyOfRange(soapContextPaths, 1, soapContextPaths.length);
            publication.contextPathByPrefix("soap", primary, additional);
        }
        return publication.register();
    }
}
