package net.hasor.meteor.console.component.rpc;
import java.nio.file.Path;
import java.util.function.Function;
import net.hasor.cobble.ExceptionUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.core.Destroy;
import net.hasor.core.Init;
import net.hasor.core.Inject;
import net.hasor.core.Singleton;
import net.hasor.meteor.MetAccessEnum;
import net.hasor.meteor.MetAccessPolicy;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.component.commands.CommandDispatcher;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.RegistryAdmin;

/** 注册中心管理 RPC 入口，持有监听上下文。 */
@Singleton
public final class RpcServer implements AutoCloseable {
    @Inject
    private MeteorConfig       config;
    @Inject
    private CommandDispatcher  dispatcher;
    @Inject
    private CommandCredentials credentials;
    private MetContext         context;
    private boolean            closed;

    @Init
    public void start() throws Exception {
        if (!this.config.isRpcEnabled()) {
            return;
        }
        String token = this.config.getManagementToken();
        if (StringUtils.isBlank(token)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_RPC_TOKEN_REQUIRED);
        }

        MetContext context = null;
        try {
            MetConfiguration configuration = new MetConfiguration() //
                    .setApplicationName("meteor-registry-server") //
                    .setDataHome(Path.of(this.config.getDataDirectory(), "rpc")) //
                    .setUnitName(this.config.getRpcUnit());
            configuration.connector("tcp") //
                    .bind(this.config.getRpcHost(), this.config.getRpcPort()) //
                    .protocol("rsf");
            context = configuration.buildContext();
            JsonUtils.configureRpc(context);
            RpcManagement management = new RpcManagementImpl(this::withRegistry);
            MetBindInfo<RpcManagement> binding = context.publisher().rsfService(RpcManagement.class, management)//
                    .protocol("rsf")//
                    .bindFilter(RpcManagementFilter.class.getName(), new RpcManagementFilter(() -> {
                        return token;
                    }))//
                    .asShadow().asDistributedCall().register();
            context.getExtension(MetAccessPolicy.class).allow(binding, MetAccessEnum.values());
            context.start();
            this.context = context;
            System.out.println("Meteor Remote registry management: " + this.getAddress());
        } catch (Exception | Error failure) {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }

    private <T> T withRegistry(String clusterId, Function<RegistryAdmin, T> operation) {
        try {
            return this.credentials.withToken(this.config.getManagementToken(), () -> this.dispatcher.withRegistry(clusterId, operation));
        } catch (Exception failure) {
            throw ExceptionUtils.toRuntime(failure);
        }
    }

    public String getAddress() {
        return this.context.getListenAddress("rsf").toString();
    }

    @Override
    @Destroy
    public synchronized void close() throws Exception {
        if (!this.closed) {
            this.closed = true;
            if (this.context != null) {
                this.context.close();
            }
        }
    }
}
