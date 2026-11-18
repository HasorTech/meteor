/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.provider.remote;
import java.net.URI;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.config.Configuration;
import net.hasor.core.Inject;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.console.component.provider.ProviderConnection;
import net.hasor.meteor.console.component.provider.ProviderFactory;
import net.hasor.meteor.console.component.rpc.RpcManagement;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.RegistryTokenProvider;

/** 创建 Console 间管理连接，借用容器管理的出站 RPC 上下文。 */
@Configuration
public final class RemoteProviderFactory implements ProviderFactory {
    @Inject
    private MetContext context;

    @Override
    public String name() {
        return "remote";
    }

    @Override
    public ProviderConnection open(ClusterProfile profile, RegistryTokenProvider credentials) throws Exception {
        profile.validate();
        if (StringUtils.isBlank(profile.getRemoteClusterId())) {
            throw new IllegalArgumentException("缺少远端集群 ID");
        }

        InterAddress address = new InterAddress(URI.create(profile.getAddress()));
        Map<String, String> options = Map.of(RpcManagement.CREDENTIAL_OPTION, profile.getManagementToken());
        var client = this.context.getRsfClient(address).withOptions(options);
        RegistryAdmin admin = new RemoteConnection(client.wrapper(RpcManagement.class), profile.getManagementToken(), profile.getRemoteClusterId());
        return ProviderConnection.registry(admin);
    }
}
