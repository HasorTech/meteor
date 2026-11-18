/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.tconsole.TelCommand;

public final class StatusCommand extends AbstractCommand {
    private final MeteorConfig config;

    public StatusCommand(MeteorConfig config) {
        this.config = config;
    }

    @Override
    public String helpInfo() {
        return "status: Console runtime and selected cluster";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        requireCount(args, 0);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("application", "meteor-console");
        state.put("web", this.config.isWebEnabled());
        state.put("telnet", this.config.isTelnetEnabled());
        state.put("cluster", command.getSession().getAttribute(CommandOptions.CLUSTER));
        return json(state);
    }
}
