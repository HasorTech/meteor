/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.integration;
import org.noear.solon.Solon;
import org.noear.solon.SolonApp;
import org.noear.solon.annotation.SolonMain;

@SolonMain
public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        SolonApp app = Solon.start(ConsumerMain.class, args, boot -> {
            boot.cfg().loadAdd("classpath:solon-consumer.properties");
        });

        try {
            EchoClient client = app.context().getBeanOrNew(EchoClient.class);
            client.run();
        } finally {
            Solon.stopBlock(false, 0);
        }
    }
}
