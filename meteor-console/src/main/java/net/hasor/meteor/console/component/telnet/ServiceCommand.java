/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.List;
import java.util.Set;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.tconsole.TelCommand;

public final class ServiceCommand extends AbstractGovernanceCommand {
    public ServiceCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        super(governance, clusters, credentials);
    }

    @Override
    public String helpInfo() {
        return "service info|enable|disable <serviceId> [--cluster id] [--instance id] [--scope service]";
    }

    @Override
    protected Set<String> options() {
        return Set.of("--cluster", "--instance", "--scope");
    }

    @Override
    protected String execute(TelCommand command, CommandOptions options, ManagementTarget target) throws Exception {
        List<String> args = options.arguments();
        requireCount(args, 2);
        target.setServiceId(args.get(1));
        String action = args.get(0);
        if (StringUtils.equals(action, "info")) {
            if (options.option("--scope") != null) {
                throw new IllegalArgumentException("info does not accept --scope");
            }
            return json(this.governance.serviceInfo(target));
        }
        if (!StringUtils.equals(action, "enable") && !StringUtils.equals(action, "disable")) {
            throw new IllegalArgumentException("Expected info|enable|disable");
        }
        // 未指定实例时，必须显式确认修改整个服务。
        if (target.getInstanceId() == null && !StringUtils.equals(target.getScope(), "service")) {
            throw new IllegalArgumentException("Specify --instance id or --scope service");
        }
        if (target.getScope() != null && !StringUtils.equals(target.getScope(), "service")) {
            throw new IllegalArgumentException("Expected --scope service");
        }
        this.governance.serviceEnabled(target, StringUtils.equals(action, "enable"));
        return "SAVED  service configuration saved; use service info to check effective state";
    }
}
