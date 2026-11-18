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
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.tconsole.TelCommand;

/** 将认证连接的命令转入既有治理服务；不持有具体注册中心连接。 */
public abstract class AbstractGovernanceCommand extends AbstractCommand {
    protected final GovernanceService  governance;
    private final   ClusterService     clusters;
    private final   CommandCredentials credentials;

    protected AbstractGovernanceCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        this.governance = governance;
        this.clusters = clusters;
        this.credentials = credentials;
    }

    protected abstract Set<String> options();

    protected abstract String execute(TelCommand command, CommandOptions options, ManagementTarget target) throws Exception;

    @Override
    protected final String execute(TelCommand command, List<String> args) throws Exception {
        CommandOptions options = new CommandOptions(args, this.options());
        ManagementTarget target = options.target(command.getSession(), this.clusters);
        return this.credentials.withToken(ConsoleTelnetService.token(command.getSession()), () -> this.execute(command, options, target));
    }
}
