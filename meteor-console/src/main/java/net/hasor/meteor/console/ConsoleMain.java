/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.logging.LoggerFactory;
import net.hasor.cobble.setting.Settings;
import net.hasor.config.Bean;
import net.hasor.config.Configuration;
import net.hasor.core.ApiBinder;
import net.hasor.core.Hasor;
import net.hasor.core.Module;
import net.hasor.meteor.MetAccessEnum;
import net.hasor.meteor.MetAccessPolicy;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.component.commands.CommandDispatcher;
import net.hasor.meteor.console.component.rpc.RpcManagement;
import net.hasor.meteor.console.component.rpc.RpcServer;
import net.hasor.meteor.console.component.telnet.ConsoleTelnetService;
import net.hasor.meteor.console.dao.ClusterDao;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.auth.ConsoleCookies;
import net.hasor.meteor.console.global.auth.SessionService;
import net.hasor.meteor.console.global.http.ConsoleApiFilter;
import net.hasor.meteor.console.global.http.ConsoleJsonRender;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.web.WebApiBinder;

/** 统一启动入口；HTTP 监听由 Hasor Boot 原生配置控制。 */
@Configuration
public class ConsoleMain implements Module {
    /** 内置默认配置先加载，外部 meteor.properties 覆盖同名项。 */
    public static void main(String[] arguments) throws Exception {
        LoggerFactory.useJdkLogger();
        try (BootApplication application = start(arguments)) {
            application.join();
        }
    }

    /** Web 和无头模式使用同一配置和公共治理装配。 */
    public static BootApplication start(String... arguments) throws Exception {
        String config = configuration(arguments).toUri().toString();
        Hasor hasor = Hasor.create().mainSettingWith(config).bindArguments(arguments);
        Settings settings = hasor.buildSettings();
        if (settings.getBoolean("console.web.enabled", true)) {
            return new Boot().arguments(arguments).sources(ConsoleMain.class).hconfigFile(config).start();
        }

        if (!settings.getBoolean("telnet.enabled", false) && StringUtils.isBlank(settings.getString("storage.address"))) {
            throw new IllegalArgumentException("Headless Console requires Telnet or a registry management listener");
        }

        return new Boot().arguments(arguments).sources(ConsoleMain.class).hconfigFile(config).property("hasor.boot.web.enabled", false).start();
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    public MetContext meteorContext(MeteorConfig config) throws IOException {
        // 仅为直连管理提供 RPC 能力，不连接注册中心。
        MetConfiguration meteor = new MetConfiguration() //
                .setApplicationName("meteor-console") //
                .setDataHome(Path.of(config.getDataDirectory(), "meteor")) //
                .setUnitName(config.getUnit()) //
                .setDefaultTimeout(5000) //
                .setConnectTimeout(3000);
        meteor.connector("tcp") //
                .protocol("rsf");
        meteor.addExtension(new RegistryConfiguration().setOnline(false));
        MetContext context = meteor.buildContext();
        try {
            JsonUtils.configureRpc(context);
            MetBindInfo<RpcManagement> binding = context.publisher().rsfService(RpcManagement.class) //
                    .asShadow().asDistributedCall().register();
            context.getExtension(MetAccessPolicy.class).allow(binding, MetAccessEnum.values());
            return context;
        } catch (RuntimeException | Error failure) {
            try {
                context.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    @Override
    public void loadModule(ApiBinder binder) {
        binder.bindType(MeteorConfig.class).asEagerSingleton();
        binder.bindType(ClusterDao.class).asEagerSingleton();
        binder.bindType(CommandDispatcher.class).asEagerSingleton();
        binder.bindType(RpcServer.class).asEagerSingleton();
        binder.bindType(ConsoleTelnetService.class).asEagerSingleton();

        WebApiBinder web = binder.tryCast(WebApiBinder.class);
        if (web != null) {
            binder.bindType(SessionService.class).asEagerSingleton();
            binder.bindType(ConsoleCookies.class).asEagerSingleton();
            web.setEncodingCharacter("UTF-8", "UTF-8");
            web.addRender("json").to(ConsoleJsonRender.class);
            Supplier<SessionService> sessions = binder.getProvider(SessionService.class);
            Supplier<ConsoleCookies> cookies = binder.getProvider(ConsoleCookies.class);
            binder.bindType(ConsoleApiFilter.class).toProvider(() -> new ConsoleApiFilter(sessions.get(), cookies.get())).asEagerSingleton();
            web.filter("/api/*").through(binder.getBindInfo(ConsoleApiFilter.class));
        }
    }

    private static Path configuration(String[] arguments) throws NoSuchFileException {
        String configured = System.getProperty("config");
        for (String argument : arguments) {
            if (argument.startsWith("--config=")) {
                configured = argument.substring("--config=".length());
                if (StringUtils.isBlank(configured)) {
                    throw new IllegalArgumentException("--config 必须指定配置文件");
                }
            }
        }
        if (StringUtils.isBlank(configured)) {
            return Path.of(System.getProperty("user.home"), ".meteor", "meteor.properties").toAbsolutePath().normalize();
        }

        Path configuration = Path.of(configured);
        if (!configuration.isAbsolute()) {
            URL resource = ConsoleMain.class.getResource("ConsoleMain.class");
            if (resource != null && StringUtils.equals(resource.getProtocol(), "jar")) {
                // Hasor Boot 的嵌套资源保留外层 JAR 地址，不能依赖应用类的 CodeSource。
                String archive = resource.getFile();
                Path jar = Path.of(URI.create(archive.substring(0, archive.indexOf("!/"))));
                configuration = jar.getParent().resolve(configuration);
            }
        }

        configuration = configuration.toAbsolutePath().normalize();
        if (!Files.isRegularFile(configuration)) {
            throw new NoSuchFileException(configuration.toString());
        } else {
            return configuration;
        }
    }
}
