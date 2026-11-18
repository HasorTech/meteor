/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.util.List;
import net.hasor.meteor.MetUpdater;
import net.hasor.tconsole.TelCommand;

/** 查询本机服务的全部、可用、失效或同机房地址。 仅供管理器注册，不作为应用 API。 */
public final class AddressesCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "addresses <serviceId> [--all|--available|--invalid|--local-unit]";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        if (args.isEmpty() || args.size() > 2) {
            throw new IllegalArgumentException("addresses <serviceId> [--all|--available|--invalid|--local-unit]");
        }

        String id = args.get(0);
        this.getGovernanceManager(command).requireService(id);
        String kind = args.size() == 1 ? "--all" : args.get(1);
        MetUpdater updater = this.getService(command).getContext().getUpdater();
        var addresses = switch (kind) {
            case "--all" -> updater.queryAllAddresses(id);
            case "--available" -> updater.queryAvailableAddresses(id);
            case "--invalid" -> updater.queryInvalidAddresses(id);
            case "--local-unit" -> updater.queryLocalUnitAddresses(id);
            default -> throw new IllegalArgumentException("Unknown address selection");
        };
        return this.getService(command).getJson().writeValueAsString(addresses.stream().map(Object::toString).toList());
    }
}
