/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.util.ArrayList;
import java.util.List;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.tconsole.TelCommand;

/** 按角色和文本分页查询本机服务。 仅供管理器注册，不作为应用 API。 */
public final class ServicesCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "services [--role provider|consumer] [--filter text] [--page N] [--limit N]";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        int page = 1;
        int limit = 20;
        String filter = "";
        MetServiceType role = null;
        if (args.size() % 2 != 0) {
            throw new IllegalArgumentException("Search options require values");
        }

        for (int i = 0; i < args.size(); i += 2) {
            String value = args.get(i + 1);
            switch (args.get(i)) {
                case "--page" -> page = Integer.parseInt(value);
                case "--limit" -> limit = Integer.parseInt(value);
                case "--filter" -> filter = value;
                case "--role" -> {
                    if (StringUtils.equalsIgnoreCase(value, "provider")) {
                        role = MetServiceType.Provider;
                    } else if (StringUtils.equalsIgnoreCase(value, "consumer")) {
                        role = MetServiceType.Consumer;
                    } else {
                        throw new IllegalArgumentException("Role must be provider or consumer");
                    }
                }
                default -> throw new IllegalArgumentException("Unknown search option");
            }
        }

        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("page >= 1, limit 1..100");
        }

        List<MetBindInfo<?>> all = new ArrayList<>();
        for (MetBindInfo<?> service : this.getGovernanceManager(command).bindings()) {
            if ((role == null || service.getServiceType() == role) && service.getBindID().contains(filter)) {
                all.add(service);
            }
        }

        long offset = (long) (page - 1) * limit;
        StringBuilder result = new StringBuilder("SERVICE ID\tROLE\tENABLED\n");
        for (long i = offset; i < Math.min(offset + limit, all.size()); i++) {
            MetBindInfo<?> service = all.get((int) i);
            result.append(service.getBindID()).append('\t') //
                    .append(service.getServiceType()).append('\t') //
                    .append(this.getService(command).getContext().getUpdater().isServiceEnabled(service.getBindID())).append('\n');
        }

        return result.append("Page ").append(page).append(", total ").append(all.size()).toString();
    }
}
