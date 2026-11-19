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
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.tconsole.TelCommand;

public final class InstanceCommand extends AbstractGovernanceCommand {
    public InstanceCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        super(governance, clusters, credentials);
    }

    @Override
    protected ConsoleMessageKey helpKey() {
        return ConsoleMessageKey.TELNET_HELP_INSTANCE;
    }

    @Override
    protected Set<String> options() {
        return Set.of("--cluster");
    }

    @Override
    protected String execute(TelCommand command, CommandOptions options, ManagementTarget target) throws Exception {
        List<String> args = options.arguments();
        requireCount(args, 2);
        target.setInstanceId(args.get(1));
        switch (args.get(0)) {
            case "info":
                return json(this.governance.instanceInfo(target));
            case "block":
                this.governance.instanceBlocked(target, true);
                break;
            case "unblock":
                this.governance.instanceBlocked(target, false);
                break;
            default:
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_INSTANCE_OPERATION);
        }
        return "SAVED  " + ConsoleI18n.getMessage(ConsoleMessageKey.TELNET_INSTANCE_SAVED);
    }
}
