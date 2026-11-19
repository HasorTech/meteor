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
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.tconsole.TelCommand;

public final class ServiceCommand extends AbstractGovernanceCommand {
    public ServiceCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        super(governance, clusters, credentials);
    }

    @Override
    protected ConsoleMessageKey helpKey() {
        return ConsoleMessageKey.TELNET_HELP_SERVICE;
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
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INFO_SCOPE);
            }
            return json(this.governance.serviceInfo(target));
        }
        if (!StringUtils.equals(action, "enable") && !StringUtils.equals(action, "disable")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_SERVICE_OPERATION);
        }
        // 未指定实例时，必须显式确认修改整个服务。
        if (target.getInstanceId() == null && !StringUtils.equals(target.getScope(), "service")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_SERVICE_SCOPE_REQUIRED);
        }
        if (target.getScope() != null && !StringUtils.equals(target.getScope(), "service")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_SERVICE_SCOPE);
        }
        this.governance.serviceEnabled(target, StringUtils.equals(action, "enable"));
        return "SAVED  " + ConsoleI18n.getMessage(ConsoleMessageKey.TELNET_SERVICE_SAVED);
    }
}
