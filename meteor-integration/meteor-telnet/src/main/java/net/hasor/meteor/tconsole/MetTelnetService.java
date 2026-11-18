/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.hasor.cobble.ClassUtils;
import net.hasor.cobble.reflect.MethodUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.tconsole.governance.MetLocalGovernanceManager;
import net.hasor.neta.channel.NetConfig;
import net.hasor.tconsole.TelConfig;
import net.hasor.tconsole.launcher.telnet.SocketTelService;

/** Telnet 管理服务；复用 tconsole 的监听、会话和命令派发，只借用业务上下文。 */
public final class MetTelnetService extends SocketTelService {
    private final MetContext           context;
    private final ObjectMapper         json = new ObjectMapper();
    private       MetGovernanceManager governanceManager;
    private final long                 timeoutMillis;

    public MetTelnetService(NetConfig network, TelConfig tel, MetContext context, long timeoutMillis) {
        super(network, tel);
        this.context = context;
        this.timeoutMillis = timeoutMillis;
    }

    /** 在所有扩展创建完毕后选择实现，不取决于扩展的声明顺序。 */
    public void beforeStart() throws Exception {
        this.governanceManager = this.createRegistryGovernance();
        if (this.governanceManager == null) {
            this.governanceManager = new MetLocalGovernanceManager(this.context);
        }
    }

    private MetGovernanceManager createRegistryGovernance() throws Exception {
        try {
            ClassUtils.getClass(this.context.getClassLoader(), "net.hasor.meteor.registry.RegistryAdmin", false);
        } catch (ClassNotFoundException absent) {
            return null;
        }

        // 使用名称延迟加载，避免字节码中的实现类型引用触发 JVM 提前解析可选依赖。
        Class<?> factory = ClassUtils.getClass(getClass().getClassLoader(), "net.hasor.meteor.tconsole.governance.MetRegistryGovernanceManager");
        Object[] arguments = { this.context, this.timeoutMillis };
        Class<?>[] parameterTypes = { MetContext.class, long.class };
        return (MetGovernanceManager) MethodUtils.invokeStaticMethod(factory, "createIfConfigured", arguments, parameterTypes);
    }

    @Override
    public String getPrompt() {
        return "meteor>";
    }

    public MetContext getContext() {
        return this.context;
    }

    public ObjectMapper getJson() {
        return this.json;
    }

    public MetGovernanceManager getGovernanceManager() {
        return this.governanceManager;
    }
}
