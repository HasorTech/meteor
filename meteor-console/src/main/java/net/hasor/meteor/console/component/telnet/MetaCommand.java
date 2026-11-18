/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.tconsole.TelCommand;

public final class MetaCommand extends AbstractGovernanceCommand {
    public MetaCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        super(governance, clusters, credentials);
    }

    @Override
    public String helpInfo() {
        return "meta list|put|remove [serviceId] [key] [JSON] --scope cluster|service [--cluster id] [--instance id]";
    }

    @Override
    protected Set<String> options() {
        return Set.of("--cluster", "--instance", "--scope");
    }

    @Override
    protected String execute(TelCommand command, CommandOptions options, ManagementTarget target) throws Exception {
        List<String> args = new ArrayList<>(options.arguments());
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Expected list|put|remove");
        }
        String action = args.remove(0);
        if (StringUtils.equals(target.getScope(), "service")) {
            if (args.isEmpty()) {
                throw new IllegalArgumentException("serviceId is required");
            }
            target.setServiceId(args.remove(0));
        }
        CommandOptions.requireScope(target);
        switch (action) {
            case "list":
                requireCount(args, 0);
                return json(this.governance.fetchMeta(target));
            case "put":
                requireCount(args, 2);
                if (args.get(0).isBlank()) {
                    throw new IllegalArgumentException("Metadata key is required");
                }
                Object value = JsonUtils.readCompatibleValue(args.get(1).getBytes(StandardCharsets.UTF_8), Object.class);
                if (value == null) {
                    throw new IllegalArgumentException("Metadata must not be null");
                }
                this.governance.putMeta(target, Map.of(args.get(0), value));
                break;
            case "remove":
                requireCount(args, 1);
                if (args.get(0).isBlank()) {
                    throw new IllegalArgumentException("Metadata key is required");
                }
                this.governance.removeMeta(target, new String[] { args.get(0) });
                break;
            default:
                throw new IllegalArgumentException("Expected list|put|remove");
        }
        return "SAVED  metadata saved; use meta list to read current values";
    }
}
