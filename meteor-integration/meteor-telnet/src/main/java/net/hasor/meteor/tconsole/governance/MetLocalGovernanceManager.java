/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.governance;
import java.util.LinkedHashMap;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetUpdater;

/** 仅修改当前进程，不依赖注册中心模块。 */
public final class MetLocalGovernanceManager extends AbstractGovernanceManager {
    public MetLocalGovernanceManager(MetContext context) {
        super(context);
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("Registry", "NONE");
        result.put("Connection", "NONE");
        result.put("Governance", "LOCAL");
        return result;
    }

    @Override
    protected boolean isManagementService(MetBindInfo<?> service) {
        return false;
    }

    private void applyRule(String id, String rule, String body) {
        String current = StringUtils.defaultIfBlank(this.readRule(id, rule), "");
        String desired = StringUtils.defaultIfBlank(body, "");
        if (StringUtils.equals(current, desired)) {
            return;
        }

        MetUpdater updater = this.getContext().getUpdater();
        boolean applied = switch (rule) {
            case "service" -> updater.updateServiceRoute(id, body);
            case "method" -> updater.updateMethodRoute(id, body);
            case "args" -> updater.updateArgsRoute(id, body);
            case "flow" -> updater.updateFlowControl(id, body);
            default -> throw new IllegalArgumentException("Unknown rule type");
        };
        if (!applied) {
            throw new IllegalArgumentException("Cannot apply " + rule + " to " + id);
        }
    }

    @Override
    protected String apply(String action, String id, String rule, String body, Map<String, Object> metadata, String key) {
        MetUpdater updater = this.getContext().getUpdater();
        switch (action) {
            case "enable" -> updater.setServiceEnabled(id, true);
            case "disable" -> updater.setServiceEnabled(id, false);
            case "block" -> updater.setBlock(true);
            case "unblock" -> updater.setBlock(false);
            case "rule" -> this.applyRule(id, rule, body);
            case "putMeta" -> this.requireService(id).setMetaData(metadata);
            case "removeMeta" -> this.requireService(id).removeMetaData(key);
            default -> throw new IllegalArgumentException("Unsupported governance operation");
        }
        return "OK  applied to current process; restored from startup configuration after restart";
    }
}