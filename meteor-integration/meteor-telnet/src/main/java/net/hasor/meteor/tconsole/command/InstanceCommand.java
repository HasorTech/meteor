/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.util.List;
import net.hasor.tconsole.TelCommand;

/** 控制当前实例的阻断和业务监听状态。 仅供管理器注册，不作为应用 API。 */
public final class InstanceCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "instance block|unblock|online|offline";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        requireCount(args, 1);
        switch (args.get(0)) {
            case "online" -> this.getService(command).getContext().online();
            case "offline" -> this.getService(command).getContext().offline();
            case "block", "unblock" -> {
                return this.getGovernanceManager(command).write(args.get(0), null, null, null, null, null);
            }
            default -> throw new IllegalArgumentException("Expected block, unblock, online or offline");
        }
        return "OK  business listener state updated; management listener remains available";
    }
}