/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.tconsole.TelSession;

/** 一次命令的显式目标；仅集群选择保存在会话中。 */
public final class CommandOptions {
    static final  String              CLUSTER   = CommandOptions.class.getName() + ".cluster";
    private final List<String>        arguments = new ArrayList<>();
    private final Map<String, String> options   = new LinkedHashMap<>();

    public CommandOptions(List<String> input, Set<String> accepted) {
        for (int i = 0; i < input.size(); i++) {
            String argument = input.get(i);
            if (!argument.startsWith("--")) {
                this.arguments.add(argument);
                continue;
            }
            if (!accepted.contains(argument) || this.options.containsKey(argument)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_UNKNOWN_OPTION, argument);
            }
            if (StringUtils.equals(argument, "--multiline")) {
                this.options.put(argument, "true");
            } else {
                if (++i >= input.size() || input.get(i).startsWith("--") || input.get(i).isBlank()) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_MISSING_OPTION_VALUE, argument);
                }
                this.options.put(argument, input.get(i));
            }
        }
    }

    public List<String> arguments() {
        return this.arguments;
    }

    public String option(String name) {
        return this.options.get(name);
    }

    public ManagementTarget target(TelSession session, ClusterService clusters) {
        String cluster = this.options.get("--cluster");
        if (cluster == null) {
            cluster = (String) session.getAttribute(CLUSTER);
        }
        final String selected = cluster;
        if (StringUtils.isBlank(selected) || clusters.list().stream().noneMatch(c -> StringUtils.equals(c.getId(), selected))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_SELECT_CLUSTER);
        }
        ManagementTarget target = new ManagementTarget();
        target.setClusterId(cluster);
        target.setInstanceId(this.options.get("--instance"));
        target.setScope(this.options.get("--scope"));
        target.setFilter(this.options.get("--filter"));
        target.setApplicationName(this.options.get("--application"));
        target.setCursor(this.options.get("--cursor"));
        if (this.options.containsKey("--limit")) {
            try {
                target.setLimit(Integer.parseInt(this.options.get("--limit")));
            } catch (NumberFormatException invalid) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_LIMIT);
            }
        }
        target.directoryQuery(); // 在访问后端之前校验分页参数。
        return target;
    }

    public static void requireScope(ManagementTarget target) {
        switch (target.requireScope()) {
            case "cluster" -> {
                if (StringUtils.isNotBlank(target.getInstanceId()) || StringUtils.isNotBlank(target.getServiceId())) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_CLUSTER_SCOPE);
                }
            }
            case "instance" -> {
                target.requireInstanceId();
                if (StringUtils.isNotBlank(target.getServiceId())) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INSTANCE_SCOPE);
                }
            }
            case "service" -> target.requireServiceId();
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_SCOPE);
        }
    }
}
