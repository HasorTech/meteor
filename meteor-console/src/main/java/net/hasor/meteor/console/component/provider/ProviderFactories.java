/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.provider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.core.AppContext;
import net.hasor.core.Init;
import net.hasor.core.Inject;
import net.hasor.core.Singleton;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.registry.RegistryTokenProvider;

/** 按名称选择容器中的工厂；新建连接的所有权交给调用方。 */
@Singleton
public final class ProviderFactories {
    @Inject
    private       AppContext                   appContext;
    private final Map<String, ProviderFactory> factories = new LinkedHashMap<>();

    @Init
    public void init() {
        // 接口和具体类都可能作为 Bean 的注册类型，按容器类型元信息查找全部工厂。
        for (String id : this.appContext.getBindIDs()) {
            Class<?> type = this.appContext.getBeanType(id);
            if (type == null || !ProviderFactory.class.isAssignableFrom(type)) {
                continue;
            }

            ProviderFactory factory = this.appContext.getInstance(id);
            String name = factory.name();
            if (StringUtils.isBlank(name)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_REGISTRY_FACTORY_NAME_MUST_NOT_BE);
            }
            if (this.factories.putIfAbsent(name, factory) != null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_DUPLICATE_REGISTRY_FACTORY_NAME, name);
            }
        }
    }

    public ProviderConnection open(ClusterProfile profile, RegistryTokenProvider credentials) throws Exception {
        ProviderFactory factory = this.factories.get(profile.getType());
        if (factory == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_UNSUPPORTED_REGISTRY_TYPE);
        }
        return factory.open(profile, credentials);
    }

    public List<String> names() {
        return this.factories.keySet().stream().sorted().toList();
    }
}
