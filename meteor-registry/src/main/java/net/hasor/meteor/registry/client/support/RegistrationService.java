/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.client.support;
import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.concurrent.future.Cancellable;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.shared.rpc.ConfigurationData;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import net.hasor.meteor.registry.shared.storage.domain.*;
import net.hasor.meteor.registry.shared.utils.RegistryGovernanceUtils;
import net.hasor.meteor.registry.shared.utils.RegistryJson;
import net.hasor.meteor.rpc.MetServiceResolver.EndpointFactory;

/** 客户端的存储适配：上报实际登记、读取配置和服务地址；不访问管理 API。 */
final class RegistrationService {
    private final StorageAccess storage;

    RegistrationService(StorageAccess storage) {
        this.storage = storage;
    }

    /** 全量核对仅处理本实例；单服务推送只写指定服务，不碰其他登记。 */
    void publish(RegistrationSnapshot snapshot, String serviceId) throws Exception {
        String instanceId = snapshot.getInstanceId();
        if (serviceId == null) {
            this.storage.registerInstance(snapshot.getInstance());
        }

        Set<String> current = new HashSet<>();
        for (EntServiceRuntimeData service : snapshot.getServices()) {
            this.storage.registerService(instanceId, service);
            current.add(service.getServiceId());
        }

        if (serviceId != null) {
            return;
        }

        // 从实例自己的清单找撤回项，不反向检索全库服务成员。
        DirectoryQuery query = new DirectoryQuery();
        query.setLimit(1000);
        do {
            RecordPage<EntServiceRuntimeData> page = this.storage.instanceServices(instanceId, query);
            for (EntServiceRuntimeData previous : page.getItems()) {
                if (!current.contains(previous.getServiceId())) {
                    this.storage.unregisterService(instanceId, previous.getServiceId());
                }
            }
            query.setCursor(page.getNextCursor());
        } while (query.getCursor() != null);
    }

    ConfigurationData defaults() throws Exception {
        return RegistryGovernanceUtils.read(this.storage, null, null);
    }

    ConfigurationData serviceConfiguration(String id) throws Exception {
        return RegistryGovernanceUtils.read(this.storage, null, id);
    }

    ConfigurationData instanceRules(String id) throws Exception {
        return RegistryGovernanceUtils.read(this.storage, id, null);
    }

    ConfigurationData serviceOverride(String serviceId, String instanceId) throws Exception {
        return RegistryGovernanceUtils.read(this.storage, instanceId, serviceId);
    }

    EntConfigurationData instanceConfiguration(String id) throws Exception {
        EntityRecord<EntConfigurationData> value = this.storage.getConfiguration(id, null);
        return value == null ? new EntConfigurationData() : value.getData();
    }

    Map<String, List<String>> providers(String serviceId) throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        DirectoryQuery query = new DirectoryQuery();
        query.setLimit(1000);

        do {
            RecordPage<RefServiceRelationData> page = this.storage.members(serviceId, MetServiceType.Provider, query);
            for (RefServiceRelationData member : page.getItems()) {
                String unit = Objects.requireNonNullElse(member.getInstance().getUnitName(), "");
                List<String> addresses = result.computeIfAbsent(unit, key -> new ArrayList<>());
                for (String address : member.getService().getAddresses()) {
                    if (!addresses.contains(address))
                        addresses.add(address);
                }
            }
            query.setCursor(page.getNextCursor());
        } while (query.getCursor() != null);
        return result;
    }

    <T> T resolve(String serviceId, String protocol, InterAddress target, EndpointFactory<T> factory) throws Exception {
        DirectoryQuery query = new DirectoryQuery();
        query.setLimit(100);

        do {
            RecordPage<RefServiceRelationData> page = this.storage.members(serviceId, MetServiceType.Provider, query);
            for (RefServiceRelationData member : page.getItems()) {
                EntServiceRuntimeData service = member.getService();
                if (member.getInstance().isBlock() || !service.isEnabled() || service.getDescription() == null) {
                    continue;
                }

                for (RefProtocolMountData endpoint : service.getEndpoints()) {
                    if (StringUtils.equalsIgnoreCase(protocol, endpoint.getProtocolName()) //
                            && endpoint.getDescription() != null && StringUtils.isNotBlank(endpoint.getPublishedAddress())) {
                        InterAddress address = new InterAddress(endpoint.getPublishedAddress());
                        if (target == null || target.equals(address)) {
                            ObjectNode description = RegistryJson.copy(service.getDescription(), ObjectNode.class);
                            ObjectNode contract = RegistryJson.copy(endpoint.getDescription(), ObjectNode.class);
                            return factory.create(address, service.getGroup(), service.getName(), service.getVersion(), //
                                    endpoint.getProtocolName(), endpoint.getContextPath(), description, contract);
                        }
                    }
                }
            }

            query.setCursor(page.getNextCursor());
        } while (query.getCursor() != null);
        return null;
    }

    List<Cancellable> watchInstance(String instanceId, Runnable changed) {
        List<Cancellable> result = new ArrayList<>();
        try {
            result.add(this.storage.watchConfiguration(null, null, changed));
            result.add(this.storage.watchConfiguration(instanceId, null, changed));
            return result;
        } catch (RuntimeException failure) {
            result.forEach(Cancellable::cancel);
            throw failure;
        }
    }

    List<Cancellable> watchService(String instanceId, String serviceId, Runnable changed) {
        List<Cancellable> result = new ArrayList<>();
        try {
            result.add(this.storage.watchConfiguration(null, serviceId, changed));
            result.add(this.storage.watchConfiguration(instanceId, serviceId, changed));
            result.add(this.storage.watchProviders(serviceId, changed));
            return result;
        } catch (RuntimeException failure) {
            result.forEach(Cancellable::cancel);
            throw failure;
        }
    }
}
