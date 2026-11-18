/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.List;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.tconsole.TelCommand;

public final class ClustersCommand extends AbstractCommand {
    private final ClusterService clusters;

    public ClustersCommand(ClusterService clusters) {
        this.clusters = clusters;
    }

    @Override
    public String helpInfo() {
        return "clusters [use <id>|clear]: list or select saved cluster for this connection";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        if (args.isEmpty()) {
            return json(this.clusters.list());
        }
        if (args.size() == 1 && StringUtils.equals(args.get(0), "clear")) {
            command.getSession().setAttribute(CommandOptions.CLUSTER, null);
            return "OK  cluster selection cleared";
        }
        requireCount(args, 2);
        if (!StringUtils.equals(args.get(0), "use") || this.clusters.list().stream().noneMatch(c -> StringUtils.equals(c.getId(), args.get(1)))) {
            throw new IllegalArgumentException("Expected clusters use <existing-id>");
        }
        command.getSession().setAttribute(CommandOptions.CLUSTER, args.get(1));
        return "OK  selected cluster " + args.get(1);
    }
}
