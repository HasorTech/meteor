/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.governance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetUpdater;
import net.hasor.meteor.address.dsl.FlowRuleParser;
import net.hasor.meteor.address.dsl.XmlRuleParser;
import net.hasor.meteor.tconsole.MetGovernanceManager;

/** 治理命令的公共入口，负责查询和校验；具体实现负责执行写入。 */
public abstract class AbstractGovernanceManager implements MetGovernanceManager {
    private final MetContext context;

    protected AbstractGovernanceManager(MetContext context) {
        this.context = context;
    }

    protected final MetContext getContext() {
        return this.context;
    }

    @Override
    public abstract Map<String, Object> status();

    protected abstract boolean isManagementService(MetBindInfo<?> service);

    @Override
    public final List<MetBindInfo<?>> bindings() {
        List<MetBindInfo<?>> result = new ArrayList<>();
        for (String id : this.context.getServiceIDs()) {
            MetBindInfo<?> service = this.context.getServiceInfo(id);
            if (service != null && !service.isShadow() && !this.isManagementService(service)) {
                result.add(service);
            }
        }

        result.sort(Comparator.comparing(MetBindInfo::getBindID));
        return result;
    }

    @Override
    public final MetBindInfo<?> requireService(String id) {
        MetBindInfo<?> service = this.context.getServiceInfo(id);
        if (service == null || service.isShadow() || this.isManagementService(service)) {
            throw new IllegalArgumentException("Business service is not registered: " + id);
        }
        return service;
    }

    @Override
    public final String readRule(String id, String rule) {
        this.requireService(id);
        MetUpdater updater = this.context.getUpdater();
        return switch (rule) {
            case "service" -> updater.serviceRoute(id);
            case "method" -> updater.methodRoute(id);
            case "args" -> updater.argsRoute(id);
            case "flow" -> updater.flowControl(id);
            default -> throw new IllegalArgumentException("Unknown rule type");
        };
    }

    @Override
    public final void validateRule(String rule, String body) {
        boolean flow = switch (rule) {
            case "flow" -> true;
            case "service", "method", "args" -> false;
            default -> throw new IllegalArgumentException("Unknown rule type");
        };
        if (StringUtils.isNotBlank(body)) {
            if (flow) {
                new FlowRuleParser().parse(body);
            } else {
                new XmlRuleParser().parse(body);
            }
        }
    }

    @Override
    public final String write(String action, String id, String rule, String body, Map<String, Object> metadata, String key) throws Exception {
        if (id != null) {
            this.requireService(id);
        }
        if (StringUtils.equals(action, "rule")) {
            this.validateRule(rule, body);
        }
        return this.apply(action, id, rule, body, metadata, key);
    }

    protected abstract String apply(String action, String id, String rule, String body, Map<String, Object> metadata, String key) throws Exception;
}
