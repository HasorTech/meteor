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
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.RegistryStatus;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.meteor.registry.admin.MetServiceAdmin;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.shared.rpc.ManagementRPC;
import net.hasor.meteor.tconsole.MetGovernanceManager;

/** 通过注册中心保存配置并同步，不在注册中心故障时回退到本地写入。 */
public final class MetRegistryGovernanceManager extends AbstractGovernanceManager {
    private final long timeoutMillis;

    public MetRegistryGovernanceManager(MetContext context, long timeoutMillis) {
        super(context);
        this.timeoutMillis = timeoutMillis;
    }

    public static MetGovernanceManager createIfConfigured(MetContext context, long timeoutMillis) {
        if (context.getExtension(RegistryClient.class) == null) {
            return null;
        } else {
            return new MetRegistryGovernanceManager(context, timeoutMillis);
        }
    }

    @Override
    public Map<String, Object> status() {
        RegistryStatus status = this.getContext().getExtension(RegistryStatus.class);
        if (status == null) {
            return Map.of("Registry", "UNKNOWN", "Connection", "UNKNOWN", "Governance", "REGISTRY");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("Registry", status.getProvider());
        result.put("Connection", status.getConnection());
        result.put("Last sync", status.getLastSuccess() == null ? "NEVER" : status.getLastSuccess().toString());
        result.put("Last attempt", status.getLastAttempt() == null ? "NEVER" : status.getLastAttempt().toString());
        result.put("Last result", status.getError() == null ? status.getConnection() : status.getError());
        result.put("Governance", "REGISTRY");
        return result;
    }

    @Override
    protected boolean isManagementService(MetBindInfo<?> service) {
        return service.getBindType() == ManagementRPC.class;
    }

    @Override
    protected String apply(String action, String serviceId, String rule, String body, Map<String, Object> metadata, String key) throws Exception {
        RegistryStatus status = this.getContext().getExtension(RegistryStatus.class);
        if (status == null || !status.isConnected()) {
            return "ERROR REGISTRY_UNAVAILABLE  registry is configured but latest synchronization failed; local fallback is disabled";
        }

        RegistryAdmin admin = this.getContext().getExtension(RegistryAdmin.class);
        String instance = this.getContext().getInstanceID();
        if (serviceId == null) {
            if (StringUtils.equals(action, "block")) {
                admin.getInstanceAdmin(instance).block();
            } else if (StringUtils.equals(action, "unblock")) {
                admin.getInstanceAdmin(instance).unblock();
            } else {
                throw new IllegalArgumentException("Unsupported instance operation");
            }
        } else {
            MetServiceAdmin service = admin.getServiceAdmin(serviceId, instance);
            switch (action) {
                case "enable" -> service.enable();
                case "disable" -> service.disable();
                case "rule" -> service.updateRule(switch (rule) {
                    case "service" -> MetRuleType.SERVICE_ROUTE;
                    case "method" -> MetRuleType.METHOD_ROUTE;
                    case "args" -> MetRuleType.ARGS_ROUTE;
                    case "flow" -> MetRuleType.FLOW_CONTROL;
                    default -> throw new IllegalArgumentException("Unknown rule type");
                }, body);
                case "putMeta" -> service.putMeta(metadata);
                case "removeMeta" -> service.removeMeta(key);
                default -> throw new IllegalArgumentException("Unsupported governance operation: " + action);
            }
        }

        try {
            this.getContext().getExtension(RegistryClient.class).pullSync().get(this.timeoutMillis, TimeUnit.MILLISECONDS);
            if (serviceId != null && (StringUtils.equals(action, "enable") || StringUtils.equals(action, "disable"))) {
                boolean desired = StringUtils.equals(action, "enable");
                if (this.getContext().getUpdater().isServiceEnabled(serviceId) != desired) {
                    return "SAVED  registry configuration saved; effective state constrained by inherited policy";
                }
            }
            return "OK  registry configuration saved; synchronized to current process";
        } catch (Exception failure) {
            return "SAVED  registry configuration saved; local synchronization pending (" + failure.getClass().getSimpleName() + ")";
        }
    }
}
