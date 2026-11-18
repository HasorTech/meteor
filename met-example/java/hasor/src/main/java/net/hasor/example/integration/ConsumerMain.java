/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.integration;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.core.ApiBinder;
import net.hasor.core.Module;
import net.hasor.meteor.MeteorModule;

public class ConsumerMain implements Module {
    public static void main(String[] args) throws Exception {
        Boot boot = new Boot().arguments(args).hconfigFile("hasor-consumer.properties").sources(ConsumerMain.class);
        try (BootApplication application = boot.start()) {
            EchoClient client = application.getAppContext().getInstance(EchoClient.class);
            client.run();
        }
    }

    @Override
    public void loadModule(ApiBinder binder) throws Throwable {
        binder.installModule(new MeteorModule());
        binder.bindType(EchoClient.class).asEagerSingleton();
    }
}
