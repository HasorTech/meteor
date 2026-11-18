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
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MeteorModule;

public class ProviderMain implements Module {
    public static void main(String[] args) throws Exception {
        Boot boot = new Boot().arguments(args).hconfigFile("hasor-provider.properties").sources(ProviderMain.class);
        try (BootApplication application = boot.start()) {
            MetContext meteor = application.getAppContext().getInstance(MetContext.class);
            System.out.println("Echo 服务已启动：" + meteor.getListenAddress("rsf"));
            application.join();
        }
    }

    @Override
    public void loadModule(ApiBinder binder) throws Throwable {
        binder.installModule(new MeteorModule());
        binder.bindType(EchoServiceImpl.class).asEagerSingleton();
    }
}
