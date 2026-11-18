package net.hasor.meteor.console.component.rpc;
import java.nio.file.Path;
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
import net.hasor.meteor.console.component.provider.ProviderConnection;
import net.hasor.meteor.console.component.provider.ProviderFactories;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.console.utils.JsonUtils;

/** 注册中心管理 RPC 入口；持有本地或 Remote 连接及监听上下文。 */
@Singleton
public final class RpcServer implements AutoCloseable {
    @Inject
    private MeteorConfig       config;
    @Inject
    private ProviderFactories  connections;
    private ProviderConnection connection;
    private MetContext         context;
    private boolean            closed;

    @Init
    public void start() throws Exception {
        if (!this.config.isRegistryEnabled()) {
            return;
        }
        String token = this.config.getManagementToken();
        if (StringUtils.isBlank(token)) {
            throw new IllegalArgumentException("Set METEOR_REGISTRY_MANAGEMENT_TOKEN or management.token");
        }

        ClusterProfile profile = new ClusterProfile();
        profile.setName("registry-server");
        profile.setType(this.config.getStorageType());
        profile.setAddress(this.config.getStorageAddress());
        profile.setDatabase(this.config.getStorageDatabase());
        profile.setUsername(this.config.getStorageUsername());
        profile.setPassword(this.config.getStoragePassword());
        profile.setManagementToken(this.config.getStorageManagementToken());
        profile.setNamespace(this.config.getStorageNamespace());
        profile.setGroup(this.config.getStorageGroup());
        profile.setRoot(this.config.getStorageRoot());

        final String expected = token;
        ProviderConnection connection = this.connections.open(profile, () -> expected);
        MetContext context = null;
        try {
            connection.registry().getClusterAdmin().info();
            connection.registry().getClusterAdmin().status();
            MetConfiguration configuration = new MetConfiguration() //
                    .setApplicationName("meteor-registry-server") //
                    .setDataHome(Path.of(this.config.getDataDirectory(), "rpc")) //
                    .setUnitName(this.config.getRpcUnit());
            configuration.connector("tcp") //
                    .bind(this.config.getRpcHost(), this.config.getRpcPort()) //
                    .protocol("rsf");
            context = configuration.buildContext();
            JsonUtils.configureRpc(context);
            RpcManagement management = new RpcManagementImpl(connection);
            MetBindInfo<RpcManagement> binding = context.publisher().rsfService(RpcManagement.class, management)//
                    .protocol("rsf")//
                    .bindFilter(RpcManagementFilter.class.getName(), new RpcManagementFilter(() -> {
                        return expected;
                    }, profile.getPassword(), profile.getManagementToken()))//
                    .asShadow().asDistributedCall().register();
            context.getExtension(MetAccessPolicy.class).allow(binding, MetAccessEnum.values());
            context.start();
            this.connection = connection;
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
            try {
                connection.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
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
            try (ProviderConnection connection = this.connection) {
                if (this.context != null) {
                    this.context.close();
                }
            }
        }
    }
}
