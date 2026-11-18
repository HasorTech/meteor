/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.hasor.meteor.MetBindInfo;
import net.hasor.tconsole.TelCommand;

/** 查询、修改和删除本机服务元信息。 仅供管理器注册，不作为应用 API。 */
public final class MetaCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "meta list <serviceId>; meta put <serviceId> <key> <JSON>; meta remove <serviceId> <key>";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        if (args.size() < 2) {
            throw new IllegalArgumentException("meta list|put|remove <serviceId> [key] [JSON]");
        }

        MetBindInfo<?> service = this.getGovernanceManager(command).requireService(args.get(1));
        switch (args.get(0)) {
            case "list" -> {
                requireCount(args, 2);
                Map<String, Object> metadata = new TreeMap<>();
                for (String key : service.getMetaKeys()) {
                    metadata.put(key, service.getMetaData(key));
                }
                return this.getService(command).getJson().writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
            }
            case "put" -> {
                requireCount(args, 4);
                Object value = this.getService(command).getJson().readValue(args.get(3), Object.class);
                if (value == null || args.get(2).isBlank()) {
                    throw new IllegalArgumentException("Metadata key and value must be nonnull");
                }
                return this.getGovernanceManager(command).write("putMeta", args.get(1), null, null, Map.of(args.get(2), value), null);
            }
            case "remove" -> {
                requireCount(args, 3);
                if (args.get(2).isBlank()) {
                    throw new IllegalArgumentException("Metadata key must not be blank");
                }
                return this.getGovernanceManager(command).write("removeMeta", args.get(1), null, null, null, args.get(2));
            }
            default -> throw new IllegalArgumentException("Unknown metadata operation");
        }
    }
}
