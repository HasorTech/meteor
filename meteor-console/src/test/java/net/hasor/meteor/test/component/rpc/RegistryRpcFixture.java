/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.test.component.rpc;
import java.util.NoSuchElementException;
import java.util.function.Function;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.provider.ProviderConnection;
import net.hasor.meteor.console.component.rpc.RpcManagementImpl;
import net.hasor.meteor.registry.RegistryAdmin;

/** 为协议边界测试提供固定 ID 的借用连接。 */
public final class RegistryRpcFixture {
    private RegistryRpcFixture() {
    }

    public static RpcManagementImpl management(ProviderConnection connection) {
        return new RpcManagementImpl(new RpcManagementImpl.RegistryAccess() {
            @Override
            public <T> T withRegistry(String clusterId, Function<RegistryAdmin, T> operation) {
                if (!StringUtils.equals("saved-registry", clusterId)) {
                    throw new NoSuchElementException("集群连接不存在或已被移除");
                }
                return operation.apply(connection.registry());
            }
        });
    }
}
