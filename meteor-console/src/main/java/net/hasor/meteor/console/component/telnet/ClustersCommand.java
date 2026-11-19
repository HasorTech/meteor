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
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.tconsole.TelCommand;

public final class ClustersCommand extends AbstractCommand {
    private final ClusterService clusters;

    public ClustersCommand(ClusterService clusters) {
        this.clusters = clusters;
    }

    @Override
    protected ConsoleMessageKey helpKey() {
        return ConsoleMessageKey.TELNET_HELP_CLUSTERS;
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        if (args.isEmpty()) {
            return json(this.clusters.list());
        }
        if (args.size() == 1 && StringUtils.equals(args.get(0), "clear")) {
            command.getSession().setAttribute(CommandOptions.CLUSTER, null);
            return "OK  " + ConsoleI18n.getMessage(ConsoleMessageKey.TELNET_CLUSTER_CLEARED);
        }
        requireCount(args, 2);
        if (!StringUtils.equals(args.get(0), "use") || this.clusters.list().stream().noneMatch(c -> StringUtils.equals(c.getId(), args.get(1)))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_CLUSTER_USE);
        }
        command.getSession().setAttribute(CommandOptions.CLUSTER, args.get(1));
        return "OK  " + ConsoleI18n.getMessage(ConsoleMessageKey.TELNET_CLUSTER_SELECTED, args.get(1));
    }
}
