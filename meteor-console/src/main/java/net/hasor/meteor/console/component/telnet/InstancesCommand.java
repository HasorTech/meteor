/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.Set;
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.tconsole.TelCommand;

public final class InstancesCommand extends AbstractGovernanceCommand {
    public InstancesCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        super(governance, clusters, credentials);
    }

    @Override
    public String helpInfo() {
        return "instances [--cluster id] [--filter text] [--limit N] [--cursor value] [--application name]";
    }

    @Override
    protected Set<String> options() {
        return Set.of("--cluster", "--filter", "--limit", "--cursor", "--application");
    }

    @Override
    protected String execute(TelCommand command, CommandOptions options, ManagementTarget target) throws Exception {
        requireCount(options.arguments(), 0);
        return json(this.governance.instances(target));
    }
}
