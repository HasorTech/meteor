/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.rpc;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.admin.*;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;

/** 通过请求携带的集群 ID 借用已保存连接，调用注册中心 API。 */
public final class RpcManagementImpl implements RpcManagement {
    @FunctionalInterface
    public interface RegistryAccess {
        <T> T withRegistry(String clusterId, Function<RegistryAdmin, T> operation);
    }

    private final RegistryAccess registries;

    public RpcManagementImpl(RegistryAccess registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
    }

    @Override
    public RegistryInfo registryInfo(RpcTarget target) {
        return cluster(target, MetClusterAdmin::info);
    }

    @Override
    public RegistryStatus registryStatus(RpcTarget target) {
        return cluster(target, MetClusterAdmin::status);
    }

    @Override
    public MetPage<MetServiceInfo> services(RpcTarget target, String filter, MetDirectoryQuery query) {
        return switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> admin.services(filter, query));
            case INSTANCE -> {
                if (StringUtils.isNotBlank(filter)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_USE_QUERY_TO_SEARCH_THE_INSTANCE);
                }
                yield instance(target, admin -> admin.getServiceList(query));
            }
            case SERVICE -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_SERVICE_DIRECTORY_DOES_NOT_ACCEPT);
        };
    }

    @Override
    public MetPage<String> applications(RpcTarget target, MetDirectoryQuery query) {
        return cluster(target, admin -> admin.applications(query));
    }

    @Override
    public MetPage<MetInstanceInfo> instances(RpcTarget target, String application, MetDirectoryQuery query) {
        return cluster(target, admin -> admin.instances(application, query));
    }

    @Override
    public String[] units(RpcTarget target, String application) {
        return switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> admin.units(application));
            case SERVICE -> service(target, MetServiceAdmin::units);
            case INSTANCE -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_UNIT_DIRECTORY_DOES_NOT_ACCEPT);
        };
    }

    @Override
    public MetInstanceInfo instanceInfo(RpcTarget target) {
        return instance(target, MetInstanceAdmin::instance);
    }

    @Override
    public MetServiceInfo serviceInfo(RpcTarget target, String serviceId) {
        return switch (scope(target)) {
            case SERVICE -> service(target, MetServiceAdmin::info);
            case INSTANCE -> instance(target, admin -> admin.getServiceById(serviceId));
            case CLUSTER -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_SERVICE_INFORMATION_REQUIRES_SERVICE_OR_INSTANCE);
        };
    }

    @Override
    public MetServiceMethodPage methods(RpcTarget target, String methodName, MetDirectoryQuery query) {
        return service(target, admin -> admin.methods(methodName, query));
    }

    @Override
    public Map<String, MetServiceDescription> descriptions(RpcTarget target) {
        return service(target, MetServiceAdmin::descriptions);
    }

    @Override
    public MetPage<MetServiceMember> members(RpcTarget target, MetServiceType role, MetEndpointFilter filter, MetDirectoryQuery query) {
        if (role == MetServiceType.Provider) {
            return service(target, admin -> admin.providers(filter, query));
        }
        if (role == MetServiceType.Consumer) {
            return service(target, admin -> admin.consumers(filter, query));
        }
        throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_MEMBER_DIRECTORY_REQUIRES_THE_PROVIDER);
    }

    @Override
    public String fetchRule(RpcTarget target, MetRuleType type) {
        return switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> admin.fetchRule(type));
            case SERVICE -> service(target, admin -> admin.fetchRule(type));
            case INSTANCE -> instance(target, admin -> admin.fetchRule(type));
        };
    }

    @Override
    public void resetRule(RpcTarget target, MetRuleType type) {
        switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> {
                admin.resetRule(type);
                return null;
            });
            case SERVICE -> service(target, admin -> {
                admin.resetRule(type);
                return null;
            });
            case INSTANCE -> instance(target, admin -> {
                admin.resetRule(type);
                return null;
            });
        }
    }

    @Override
    public MetValidationResult validateRule(RpcTarget target, MetRuleType type, String rule) {
        return switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> admin.validateRule(type, rule));
            case SERVICE -> service(target, admin -> admin.validateRule(type, rule));
            case INSTANCE -> throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_INSTANCE_SCOPE_DOES_NOT_SUPPORT_STANDALONE);
        };
    }

    @Override
    public void updateRule(RpcTarget target, MetRuleType type, String rule) {
        switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> {
                admin.updateRule(type, rule);
                return null;
            });
            case SERVICE -> service(target, admin -> {
                admin.updateRule(type, rule);
                return null;
            });
            case INSTANCE -> instance(target, admin -> {
                admin.updateRule(type, rule);
                return null;
            });
        }
    }

    @Override
    public boolean isRuleEnabled(RpcTarget target, MetRuleType type) {
        return switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> admin.isRuleEnabled(type));
            case SERVICE -> service(target, admin -> admin.isRule(type));
            case INSTANCE -> instance(target, admin -> admin.isRuleEnabled(type));
        };
    }

    @Override
    public void ruleEnabled(RpcTarget target, MetRuleType type, boolean enabled) {
        switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> {
                if (enabled) {
                    admin.enableRule(type);
                } else {
                    admin.disableRule(type);
                }
                return null;
            });
            case SERVICE -> service(target, admin -> {
                if (enabled) {
                    admin.enableRule(type);
                } else {
                    admin.disableRule(type);
                }
                return null;
            });
            case INSTANCE -> instance(target, admin -> {
                if (enabled) {
                    admin.enableRule(type);
                } else {
                    admin.disableRule(type);
                }
                return null;
            });
        }
    }

    @Override
    public boolean isServiceEnabled(RpcTarget target) {
        return service(target, MetServiceAdmin::isEnable);
    }

    @Override
    public void serviceEnabled(RpcTarget target, boolean enabled) {
        service(target, admin -> {
            if (enabled) {
                admin.enable();
            } else {
                admin.disable();
            }
            return null;
        });
    }

    @Override
    public boolean isInstanceBlocked(RpcTarget target) {
        return instance(target, MetInstanceAdmin::isBlock);
    }

    @Override
    public void instanceBlocked(RpcTarget target, boolean blocked) {
        instance(target, admin -> {
            if (blocked) {
                admin.block();
            } else {
                admin.unblock();
            }
            return null;
        });
    }

    @Override
    public Map<String, Object> fetchMeta(RpcTarget target) {
        return switch (scope(target)) {
            case CLUSTER -> cluster(target, MetClusterAdmin::fetchMeta);
            case SERVICE -> service(target, MetServiceAdmin::fetchMeta);
            case INSTANCE -> throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_INSTANCE_SCOPE_DOES_NOT_SUPPORT_METADATA);
        };
    }

    @Override
    public void putMeta(RpcTarget target, Map<String, Object> values) {
        switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> {
                admin.putMeta(values);
                return null;
            });
            case SERVICE -> service(target, admin -> {
                admin.putMeta(values);
                return null;
            });
            case INSTANCE -> throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_INSTANCE_SCOPE_DOES_NOT_SUPPORT_METADATA);
        }
    }

    @Override
    public void removeMeta(RpcTarget target, String[] keys) {
        switch (scope(target)) {
            case CLUSTER -> cluster(target, admin -> {
                admin.removeMeta(keys);
                return null;
            });
            case SERVICE -> service(target, admin -> {
                admin.removeMeta(keys);
                return null;
            });
            case INSTANCE -> throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_INSTANCE_SCOPE_DOES_NOT_SUPPORT_METADATA);
        }
    }

    private RpcTarget.Scope scope(RpcTarget target) {
        if (target == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_REGISTRY_MANAGEMENT_TARGET);
        }
        target.validate();
        return target.getScope();
    }

    private <T> T cluster(RpcTarget target, Function<MetClusterAdmin, T> operation) {
        if (scope(target) != RpcTarget.Scope.CLUSTER) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_OPERATION_REQUIRES_CLUSTER_SCOPE);
        }
        return this.registries.withRegistry(target.getClusterId(), registry -> operation.apply(registry.getClusterAdmin()));
    }

    private <T> T instance(RpcTarget target, Function<MetInstanceAdmin, T> operation) {
        if (scope(target) != RpcTarget.Scope.INSTANCE) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_OPERATION_REQUIRES_INSTANCE_SCOPE);
        }
        return this.registries.withRegistry(target.getClusterId(), registry -> operation.apply(registry.getInstanceAdmin(target.getInstanceId())));
    }

    private <T> T service(RpcTarget target, Function<MetServiceAdmin, T> operation) {
        if (scope(target) != RpcTarget.Scope.SERVICE) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THIS_OPERATION_REQUIRES_SERVICE_SCOPE);
        }
        return this.registries.withRegistry(target.getClusterId(), registry -> {
            if (StringUtils.isBlank(target.getInstanceId())) {
                return operation.apply(registry.getServiceAdmin(target.getServiceId()));
            }
            return operation.apply(registry.getServiceAdmin(target.getServiceId(), target.getInstanceId()));
        });
    }
}
