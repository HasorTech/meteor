/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hasor.tconsole.TelCommand;

/** 显示当前实例、管理监听和注册中心同步状态。 仅供管理器注册，不作为应用 API。 */
public final class StatusCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "status: instance, registry connection, latest sync and governance mode";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("Application", this.getService(command).getContext().getApplicationName());
        state.put("Instance", this.getService(command).getContext().getInstanceID());
        state.put("Runtime", this.getService(command).getContext().isOnline() ? "ONLINE" : "OFFLINE");
        state.put("Blocked", this.getService(command).getContext().getUpdater().isBlock());
        state.put("Management", this.getService(command).getListenAddress());
        state.put("Protocols", this.getService(command).getContext().runProtocols());
        state.putAll(this.getGovernanceManager(command).status());
        StringBuilder result = new StringBuilder();
        state.forEach((key, value) -> {
            result.append(key).append(" : ").append(value).append('\n');
        });
        return result.toString();
    }
}
