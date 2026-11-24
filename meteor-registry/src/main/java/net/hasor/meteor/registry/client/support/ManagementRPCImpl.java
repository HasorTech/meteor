/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry.client.support;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.*;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.ConnectorConfig;
import net.hasor.meteor.connector.ConnectorManager;
import net.hasor.meteor.connector.transport.http.HttpServicePathUtils;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.ServiceDomain;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.meta.ServiceMeta;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;
import net.hasor.meteor.registry.RegistryTokenProvider;
import net.hasor.meteor.registry.admin.MetEndpointType;
import net.hasor.meteor.registry.shared.rpc.*;
import net.hasor.meteor.registry.shared.storage.domain.*;
import net.hasor.meteor.registry.shared.utils.RegistryGovernanceUtils;
import net.hasor.meteor.registry.shared.utils.RegistryJson;
import net.hasor.meteor.utils.ContextPathUtils;

/** 管理本地 Meteor 的运行状态、治理、快照和管理 RPC；并发控制由实际状态的持有者负责。 */
public final class ManagementRPCImpl implements ManagementRPC {
    private final MetContext                 context;
    private final ConnectorManager           connectors;
    private final String                     publishHost;
    private final MetBindInfo<ManagementRPC> managementService;
    private final MetUpdater                 updater;

    ////////////////////////////////////////////////////////////////////////////////////////
    // 本地运行服务与管理 RPC 生命周期

    /** 发布管理 RPC，登记治理白名单并保留独立鉴权。 */
    public ManagementRPCImpl(MetContext context, ConnectorManager connectors, String publishHost, //
            RegistryTokenProvider tokenProvider, List<String> managementProtocols) {
        this.context = context;
        this.connectors = connectors;
        this.publishHost = publishHost;
        this.updater = context.getUpdater();
        if (this.context.getServiceInfo(ManagementRPC.class) != null) {
            throw new IllegalStateException("Management RPC is already registered on this Meteor context");
        }

        MetPublisher publisher = this.context.publisher();
        var management = publisher.rsfService(ManagementRPC.class, this);
        for (String protocol : managementProtocols) {
            management.protocol(protocol);
        }
        this.managementService = management //
                .bindFilter(ManagementAuthentication.class.getName(), new ManagementAuthentication(tokenProvider))//
                .asDistributedCall()//
                .register();
        context.getExtension(MetAccessPolicy.class).allow(this.managementService, MetAccessEnum.values());
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // Client 协调入口：应用配置和发现结果，采集完整注册快照。
    Exception apply(String id, ConfigurationData defaults, ConfigurationData serviceConfig, ConfigurationData machine, ConfigurationData instanceConfig) {
        MetBindInfo<?> service = this.requireService(id);
        try {
            // 输入只参与这一次合并，不保存基线、覆盖层或已应用配置副本。
            this.requireValid(defaults);
            this.requireValid(serviceConfig);
            this.requireValid(machine);
            this.requireValid(instanceConfig);

            ConfigurationData config = effective(defaults, serviceConfig, machine, instanceConfig);
            this.applyCurrent(service, config);
            return null;
        } catch (Exception failure) {
            return failure;
        }
    }

    /** 获取当前已登记的业务服务。 */
    List<String> serviceIds() {
        List<MetBindInfo<?>> services = this.bindings();
        return services.stream().map(MetBindInfo::getBindID).toList();
    }

    /** 直接应用实例阻断治理，不修改应用运行选项。 */
    Exception applyInstance(EntConfigurationData configuration) {
        try {
            this.updater.setBlock(Boolean.TRUE.equals(configuration.getBlock()));
            return null;
        } catch (Exception failure) {
            return failure;
        }
    }

    /** 公共声明与实际端点契约随实例服务整体上报，运行对象和地址本不参与序列化。 */
    RegistrationSnapshot snapshot(String serviceId) throws Exception {
        RegistrationSnapshot record = new RegistrationSnapshot();
        record.setInstanceId(this.context.getInstanceID());
        if (serviceId == null) {
            InstanceData instance = this.instance(null);
            EntInstanceRuntimeData registration = RegistryJson.copy(instance, EntInstanceRuntimeData.class);
            record.setInstance(registration);
        }

        List<MetBindInfo<?>> services;
        if (serviceId == null) {
            services = this.bindings();
        } else {
            services = List.of(this.requireService(serviceId));
        }
        for (MetBindInfo<?> service : services) {
            // 同一次采集借用同一份已提交发布结果，完整描述归属实例服务。
            ServiceDomain<?> domain = MetBindInfoWrap.unwrap(service);
            Map<String, ProtocolMeta> publications = domain.getProtocolMetas();
            String id = service.getBindID();
            EntServiceRuntimeData registration = new EntServiceRuntimeData();
            registration.setServiceId(id);
            registration.setGroup(service.getBindGroup());
            registration.setName(service.getBindName());
            registration.setVersion(service.getBindVersion());
            registration.setRole(service.getServiceType());
            registration.setEnabled(this.updater.isServiceEnabled(id));
            ServiceEndpointData[] selected = this.endpoints(service, publications);
            RefProtocolMountData[] endpoints = RegistryJson.copy(selected, RefProtocolMountData[].class);
            registration.setEndpoints(endpoints);
            registration.setDescription(RegistryJson.copy(this.description(service), RefServiceDescriptionData.class));
            registration.setUsedProtocols(this.usedProtocols(service));
            boolean provider = service.getServiceType() == MetServiceType.Provider;

            // 已禁用、阻断或离线的提供者保留登记，但不向发现方提供地址。
            boolean available = this.context.isOnline() && !this.updater.isBlock() && registration.isEnabled();
            Set<String> published = new TreeSet<>();
            if (provider && available) {
                for (RefProtocolMountData endpoint : endpoints) {
                    if (endpoint.getListenAddress() != null) {
                        InterAddress listen = new InterAddress(endpoint.getListenAddress());
                        String address = this.toPublishAddress(listen).toString();
                        endpoint.setPublishedAddress(address);
                        Map<String, String> addresses = endpoint.getEndpoints().get(listen.getSchema());
                        if (addresses != null && addresses.containsKey(MetEndpointType.ENDPOINT.getKey())) {
                            addresses.put(MetEndpointType.ENDPOINT.getKey(), address);
                        }
                        published.add(address);
                    }
                }
            }

            // 竞争中的撤回或重新发布交给下一轮采集，保留存储中的上一份完整记录。
            MetBindInfo<?> current = this.context.getServiceInfo(id);
            boolean removed = current == null || MetBindInfoWrap.unwrap(current) != domain;
            if (removed || domain.getProtocolMetas() != publications) {
                throw new ConcurrentModificationException("Service publication changed during snapshot: " + id);
            }
            registration.setAddresses(published.toArray(String[]::new));
            record.getServices().add(registration);
        }

        if (record.getInstance() != null) {
            Set<MetServiceType> roles = EnumSet.noneOf(MetServiceType.class);
            for (EntServiceRuntimeData service : record.getServices()) {
                roles.add(service.getRole());
            }
            record.getInstance().setRoles(roles.toArray(MetServiceType[]::new));
        }
        return record;
    }

    /** 完整保留发现的提供者地址，去重后交给地址池计算本地可用结果。 */
    void updateAddresses(String serviceId, Map<String, List<String>> providers, boolean force) throws Exception {
        this.requireService(serviceId);
        Map<String, List<InterAddress>> discovered = new TreeMap<>();
        for (Map.Entry<String, List<String>> group : providers.entrySet()) {
            Set<InterAddress> addresses = new LinkedHashSet<>();
            for (String value : group.getValue())
                addresses.add(new InterAddress(value));
            List<InterAddress> ordered = new ArrayList<>(addresses);
            ordered.sort(Comparator.comparing(InterAddress::toString));
            discovered.put(group.getKey(), ordered);
        }

        MetUpdater updater = this.context.getUpdater();
        if (force || !discovered.equals(updater.queryAddressesByUnit(serviceId))) {
            updater.refreshAddress(serviceId, discovered);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // 直连管理：查询本地运行信息，修改只保存在当前进程。
    @Override
    public InstanceData instance(String token) {
        InstanceData result = new InstanceData();
        result.setInstanceId(this.context.getInstanceID());
        result.setIpAddress(this.context.getSettings().getBindAddress());
        result.setProcessId(ProcessHandle.current().pid());
        result.setApplicationName(this.context.getApplicationName());
        result.setUnitName(this.context.getSettings().getUnitName());
        result.setBlock(this.updater.isBlock());
        List<InstanceEndpointData> endpoints = new ArrayList<>();
        Map<String, ProtocolMeta> managementPublications = MetBindInfoWrap.unwrap(this.managementService).getProtocolMetas();
        for (ConnectorConfig connector : this.context.getSettings().getConnectorConfigs()) {
            if (!connector.bindEnabled()) {
                continue;
            }

            for (ProtocolConfig protocol : connector.protocols()) {
                InterAddress address = this.context.getListenAddress(//
                        connector.name(), protocol.name(), protocol.scheme());
                if (address == null) {
                    continue;
                }

                ProtocolMeta management = managementPublications.get(protocol.name());
                ProtocolBinding binding = management == null ? null : management.getEndpoint(connector.name());
                InterAddress managementAddress = null;
                if (binding != null) {
                    managementAddress = address;
                    if (StringUtils.isNotBlank(this.publishHost)) {
                        managementAddress = new InterAddress(//
                                address.getSchema(), this.publishHost, address.getPort(), address.getPath());
                    }
                }

                List<String> paths = ContextPathUtils.contextPaths(protocol.options());
                if (paths.isEmpty()) {
                    paths = Collections.singletonList(null);
                }
                for (String path : paths) {
                    InstanceEndpointData endpoint = new InstanceEndpointData();
                    endpoint.setProtocolName(protocol.name());
                    endpoint.setProtocolFamilies(this.connectors.getProtocolFamilies(connector.name(), protocol.name()));
                    endpoint.setDefaultProtocol(StringUtils.equalsIgnoreCase(protocol.name(), this.context.getDefaultProtocol()));
                    Map<String, String> addresses = new LinkedHashMap<>();
                    Set<String> types = this.context.getEndpointTypes(connector.name(), protocol.name());
                    for (String type : types) {
                        if (StringUtils.equals(type, MetEndpointType.MANAGEMENT.getKey())) {
                            throw new IllegalStateException("Management endpoint is defined by the registry");
                        }
                        addresses.put(type, "");
                    }
                    if (types.contains(MetEndpointType.ENDPOINT.getKey())) {
                        InterAddress mounted = new InterAddress(address.getSchema(), address.getHost(), address.getPort(), path);
                        addresses.put(MetEndpointType.ENDPOINT.getKey(), mounted.toString());
                    }
                    if (types.contains(MetEndpointType.HTTP.getKey()) && StringUtils.equals(connector.transportType(), "http")) {
                        URI httpMount = this.httpBase(connector, protocol, address).resolve(StringUtils.defaultIfBlank(path, "/"));
                        addresses.put(MetEndpointType.HTTP.getKey(), httpMount.toASCIIString());
                    }
                    endpoint.setEndpoints(addresses);

                    // 同一监听器的各上下文独立上报，管理地址只属于管理服务实际接受的挂载。
                    if (binding != null) {
                        var managementContexts = binding.getContextPath();
                        if (managementContexts == null || managementContexts.getPaths().contains(path)) {
                            addresses.put(MetEndpointType.MANAGEMENT.getKey(), new InterAddress(managementAddress.getSchema(), managementAddress.getHost(), managementAddress.getPort(), path).toString());
                        }
                    }
                    endpoints.add(endpoint);
                }
            }
        }
        result.setEndpoints(endpoints.toArray(InstanceEndpointData[]::new));
        return result;
    }

    private URI httpBase(ConnectorConfig connector, ProtocolConfig protocol, InterAddress address) {
        String scheme = protocol.option("httpScheme", connector.options().getOrDefault("httpScheme", "http"));
        if (Boolean.parseBoolean(connector.options().getOrDefault("tlsEnabled", "false"))) {
            scheme = "https";
        }
        try {
            return new URI(scheme, null, address.getHost(), address.getPort(), "/", null, null);
        } catch (java.net.URISyntaxException failure) {
            throw new IllegalStateException("Invalid HTTP endpoint", failure);
        }
    }

    /** 只读取当前服务、当前挂载已准备的接入点，不推导未知契约地址。 */
    private Map<String, Map<String, String>> serviceEndpoints(ConnectorConfig connector, ProtocolConfig protocol, InterAddress address, String path, ProtocolBinding binding) {
        Map<String, String> addresses = new LinkedHashMap<>();
        Set<String> types = this.context.getEndpointTypes(connector.name(), protocol.name());
        if (types.contains(MetEndpointType.ENDPOINT.getKey())) {
            String clientPath = HttpServicePathUtils.clientPath(binding, path);
            InterAddress mounted = new InterAddress(address.getSchema(), address.getHost(), address.getPort(), clientPath);
            addresses.put(MetEndpointType.ENDPOINT.getKey(), mounted.toString());
        }
        if (StringUtils.equals(connector.transportType(), "http")) {
            URI base = this.httpBase(connector, protocol, address);
            JsonNode description = binding.getDescription();
            JsonNode definitions = description == null ? null : description.get("endpoints");
            JsonNode routes = definitions == null || path == null ? null : definitions.get(path);
            if (routes != null && routes.isObject()) {
                routes.fields().forEachRemaining(entry -> {
                    String type = entry.getKey();
                    if (StringUtils.equals(type, MetEndpointType.ENDPOINT.getKey()) || StringUtils.equals(type, MetEndpointType.MANAGEMENT.getKey())) {
                        throw new IllegalStateException("Reserved service endpoint type: " + type);
                    }
                    if (!types.contains(type)) {
                        throw new IllegalStateException("Undeclared service endpoint type: " + type);
                    }
                    if (!entry.getValue().isTextual() || StringUtils.isBlank(entry.getValue().asText())) {
                        throw new IllegalStateException("Missing service endpoint address: " + type);
                    }
                    addresses.put(type, base.resolve(entry.getValue().asText()).toASCIIString());
                });
            }
            if (types.contains(MetEndpointType.HTTP.getKey())) {
                URI mounted = base.resolve(StringUtils.defaultIfBlank(path, "/"));
                addresses.putIfAbsent(MetEndpointType.HTTP.getKey(), mounted.toASCIIString());
            }
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put(address.getSchema(), addresses);
        return result;
    }

    @Override
    public ServiceData[] services(String token) {
        List<ServiceData> services = new ArrayList<>();
        for (MetBindInfo<?> service : this.bindings()) {
            ServiceEndpointData[] endpoints = this.endpoints(service);
            String[] usedProtocols = this.usedProtocols(service);
            services.add(this.serviceSnapshot(service, endpoints, usedProtocols));
        }
        return services.toArray(ServiceData[]::new);
    }

    @Override
    public ServiceMemberData service(String token, String serviceId) {
        // 直连查询只读取本地端点，不生成向注册中心发布的地址。
        MetBindInfo<?> service = this.requireService(serviceId);
        Map<String, ProtocolMeta> publications = MetBindInfoWrap.unwrap(service).getProtocolMetas();
        ServiceEndpointData[] endpoints = this.endpoints(service, publications);
        String[] usedProtocols = this.usedProtocols(service);

        // 返回对象和端点均由本次采集创建，描述在接入已提交协议契约时复制可变树。
        ServiceMemberData member = new ServiceMemberData();
        member.setInstance(this.instance(null));
        member.setService(this.serviceSnapshot(service, endpoints, usedProtocols));
        member.setEndpoints(endpoints);
        member.setUsedProtocols(usedProtocols);
        member.setDescription(this.description(service));
        member.setEnabled(this.updater.isServiceEnabled(serviceId));
        return member;
    }

    @Override
    public boolean isServiceEnabled(String token, String serviceId) {
        this.requireService(serviceId);
        return this.updater.isServiceEnabled(serviceId);
    }

    @Override
    public void setServiceEnabled(String token, String serviceId, boolean enabled) {
        this.requireService(serviceId);
        this.updater.setServiceEnabled(serviceId, enabled);
    }

    @Override
    public boolean isBlock(String token) {
        return this.updater.isBlock();
    }

    @Override
    public void setBlock(String token, boolean block) {
        this.updater.setBlock(block);
    }

    @Override
    public String fetchServiceRule(String token, String serviceId, RuleTypeData type) {
        this.requireService(serviceId);
        return this.readRule(serviceId, type);
    }

    @Override
    public String fetchInstanceRule(String token, RuleTypeData type) {
        // 核心没有实例规则对象；仅当现有业务服务规则一致时返回共同值。
        List<String> services = this.serviceIds();
        if (services.isEmpty()) {
            return null;
        }
        String common = this.readRule(services.get(0), type);
        for (int i = 1; i < services.size(); i++) {
            String current = this.readRule(services.get(i), type);
            if (!StringUtils.equals(common, current)) {
                throw new IllegalStateException("Instance services use different rules; query the selected service");
            }
        }
        return common;
    }

    @Override
    public ValidationData validateServiceRule(String token, String serviceId, RuleTypeData type, String value) {
        this.requireService(serviceId);

        return RegistryGovernanceUtils.validateRule(type, value);
    }

    @Override
    public void updateInstanceRule(String token, RuleTypeData type, String rule) {
        this.updateRule(null, type, rule);
    }

    @Override
    public void updateServiceRule(String token, String serviceId, RuleTypeData type, String rule) {
        this.updateRule(serviceId, type, rule);
    }

    private void updateRule(String serviceId, RuleTypeData type, String value) {
        ValidationData validation = RegistryGovernanceUtils.validateRule(type, value);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(String.join("; ", validation.getErrors()));
        }
        List<String> services = serviceId == null ? this.serviceIds() : List.of(this.requireService(serviceId).getBindID());
        for (String id : services) {
            this.applyRule(id, type, value);
        }
    }

    @Override
    public Map<String, Object> fetchServiceMeta(String token, String serviceId) {
        MetBindInfo<?> service = this.requireService(serviceId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String key : service.getMetaKeys()) {
            Object current = service.getMetaData(key);
            if (current != null) {
                metadata.put(key, current);
            }
        }
        return RegistryJson.copy(metadata, Map.class);
    }

    @Override
    public void putServiceMeta(String token, String serviceId, Map<String, Object> values) {
        if (values == null || values.size() > MetBindInfo.MAX_METADATA_SIZE) {
            throw new IllegalArgumentException("Metadata must not exceed " + MetBindInfo.MAX_METADATA_SIZE + " entries");
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                throw new IllegalArgumentException("Metadata requires nonblank keys and nonnull values");
            }
        }

        MetBindInfo<?> service = this.requireService(serviceId);
        service.setMetaData(values);
    }

    @Override
    public void removeServiceMeta(String token, String serviceId, String[] keys) {
        if (keys == null) {
            throw new IllegalArgumentException("Metadata keys must not be null");
        }

        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                throw new IllegalArgumentException("Metadata key must not be blank");
            }
        }

        MetBindInfo<?> service = this.requireService(serviceId);
        for (String key : keys) {
            service.removeMetaData(key);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // 通过现有 API 应用配置；值只存于应用，不建立本地覆盖层。
    private void applyCurrent(MetBindInfo<?> service, ConfigurationData config) {
        String id = service.getBindID();
        for (RuleTypeData type : RuleTypeData.values()) {
            this.applyRule(id, type, rule(config, type));
        }

        this.applyMetadata(service, config.getMetadata());
        this.updater.setServiceEnabled(id, Boolean.TRUE.equals(config.getEnabled()));
    }

    private String readRule(String id, RuleTypeData type) {
        return switch (type) {
            case SERVICE_ROUTE -> this.updater.serviceRoute(id);
            case METHOD_ROUTE -> this.updater.methodRoute(id);
            case ARGS_ROUTE -> this.updater.argsRoute(id);
            case FLOW_CONTROL -> this.updater.flowControl(id);
        };
    }

    private void applyRule(String id, RuleTypeData type, String value) {
        if (StringUtils.equals(StringUtils.defaultIfBlank(this.readRule(id, type), ""), StringUtils.defaultIfBlank(value, ""))) {
            return;
        }
        boolean applied = switch (type) {
            case SERVICE_ROUTE -> this.updater.updateServiceRoute(id, value);
            case METHOD_ROUTE -> this.updater.updateMethodRoute(id, value);
            case ARGS_ROUTE -> this.updater.updateArgsRoute(id, value);
            case FLOW_CONTROL -> this.updater.updateFlowControl(id, value);
        };
        if (!applied) {
            throw new IllegalArgumentException("Cannot apply " + type + " to " + id);
        }
    }

    private void requireValid(ConfigurationData value) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            errors.add("configuration is required");
        } else {
            for (RuleTypeData type : RuleTypeData.values()) {
                if (!Boolean.FALSE.equals(value.getRuleEnabled().get(type))) {
                    String script = rule(value, type);

                    ValidationData validation = RegistryGovernanceUtils.validateRule(type, script);
                    Collections.addAll(errors, validation.getErrors());
                }
            }

            if (value.getMetadata() != null && value.getMetadata().size() > MetBindInfo.MAX_METADATA_SIZE) {
                errors.add("Metadata cannot exceed " + MetBindInfo.MAX_METADATA_SIZE + " entries");
            }

            try {
                RegistryJson.write(value.getMetadata());
            } catch (Exception failure) {
                errors.add("metadata must be JSON data");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    private static String rule(ConfigurationData config, RuleTypeData type) {
        if (type == null) {
            throw new IllegalArgumentException("Rule type is required");
        }

        return switch (type) {
            case SERVICE_ROUTE -> config.getServiceRoute();
            case METHOD_ROUTE -> config.getMethodRoute();
            case ARGS_ROUTE -> config.getArgsRoute();
            case FLOW_CONTROL -> config.getFlowControl();
        };
    }

    private void applyMetadata(MetBindInfo<?> service, Map<String, Object> desired) {
        Map<String, Object> values = desired == null ? Map.of() : desired;
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : service.getMetaKeys()) {
            if (!values.containsKey(key)) {
                changes.put(key, null);
            }
        }

        changes.putAll(values);
        // 将删除与写入一次提交给应用，校验失败时不提前清空旧值。
        service.setMetaData(changes);
    }

    private static String normalize(String value) {
        return StringUtils.isBlank(value) ? "" : value;
    }

    private static ConfigurationData effective(ConfigurationData defaults, ConfigurationData service, ConfigurationData machine, ConfigurationData instance) {
        ConfigurationData result = new ConfigurationData();
        result.setEnabled(!Boolean.FALSE.equals(service.getEnabled()));
        result.setEnabled(Boolean.TRUE.equals(result.getEnabled()) && !Boolean.FALSE.equals(instance.getEnabled()));

        for (RuleTypeData type : RuleTypeData.values()) {
            ConfigurationData selected = selectRuleScope(type, instance, machine, service, defaults);
            if (selected != null) {
                boolean disabled = Boolean.FALSE.equals(selected.getRuleEnabled().get(type));
                setRule(result, type, disabled ? "" : rule(selected, type));
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();

        if (defaults.getMetadata() != null) {
            metadata.putAll(defaults.getMetadata());
        }
        if (service.getMetadata() != null) {
            metadata.putAll(service.getMetadata());
        }
        if (machine.getMetadata() != null) {
            metadata.putAll(machine.getMetadata());
        }
        if (instance.getMetadata() != null) {
            metadata.putAll(instance.getMetadata());
        }

        if (metadata.size() > MetBindInfo.MAX_METADATA_SIZE) {
            throw new IllegalArgumentException("Effective metadata cannot exceed " + MetBindInfo.MAX_METADATA_SIZE + " entries");
        } else {
            result.setMetadata(metadata.isEmpty() ? null : metadata);
            return RegistryJson.copy(result, ConfigurationData.class);
        }
    }

    private static ConfigurationData selectRuleScope(RuleTypeData type, ConfigurationData... scopes) {
        for (ConfigurationData config : scopes) {
            if (Boolean.FALSE.equals(config.getRuleEnabled().get(type))) {
                return config;
            }

            String rule = rule(config, type);
            if (rule != null) {
                return config;
            }
        }
        return null;
    }

    private static void setRule(ConfigurationData config, RuleTypeData type, String rule) {
        switch (type) {
            case SERVICE_ROUTE -> config.setServiceRoute(rule);
            case METHOD_ROUTE -> config.setMethodRoute(rule);
            case ARGS_ROUTE -> config.setArgsRoute(rule);
            case FLOW_CONTROL -> config.setFlowControl(rule);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // 本地信息采集：查询与注册上报共用，发布地址只在注册快照中生成。
    private List<MetBindInfo<?>> bindings() {
        List<MetBindInfo<?>> result = new ArrayList<>();
        for (String id : this.context.getServiceIDs()) {
            MetBindInfo<?> service = this.context.getServiceInfo(id);
            if (service != null && !service.isShadow() && service.getBindType() != ManagementRPC.class) {
                result.add(service);
            }
        }

        return result;
    }

    private MetBindInfo<?> requireService(String id) {
        MetBindInfo<?> service = this.context.getServiceInfo(id);
        if (service == null || service.isShadow() || service.getBindType() == ManagementRPC.class) {
            throw new IllegalArgumentException("Unknown business service: " + id);
        }

        return service;
    }

    private ServiceData serviceSnapshot(MetBindInfo<?> service, ServiceEndpointData[] endpoints, String[] usedProtocols) {
        ServiceData result = new ServiceData();
        result.setServiceId(service.getBindID());
        result.setGroup(service.getBindGroup());
        result.setName(service.getBindName());
        result.setVersion(service.getBindVersion());
        result.setRole(service.getServiceType());
        if (service.getServiceType() == MetServiceType.Provider) {
            result.setProviderCount(1);
        } else {
            result.setConsumerCount(1);
        }

        // 发布协议来自实际接受端点；订阅协议来自本次地址本采样。
        Set<String> protocols = new TreeSet<>();
        if (service.getServiceType() == MetServiceType.Consumer) {
            Collections.addAll(protocols, usedProtocols);
        } else {
            for (ServiceEndpointData endpoint : endpoints) {
                String name = endpoint.getProtocolName();
                if (StringUtils.isNotBlank(name)) {
                    protocols.add(name);
                }
            }
        }
        result.setProtocols(protocols.toArray(String[]::new));
        return result;
    }

    private String[] usedProtocols(MetBindInfo<?> service) {
        if (service.getServiceType() != MetServiceType.Consumer) {
            return new String[0];
        }

        Set<String> protocols = new TreeSet<>();
        for (InterAddress address : this.context.getUpdater().queryAllAddresses(service.getBindID())) {
            protocols.add(address.getSchema().toLowerCase(Locale.ROOT));
        }
        return protocols.toArray(String[]::new);
    }

    private ServiceEndpointData[] endpoints(MetBindInfo<?> service) {
        return this.endpoints(service, MetBindInfoWrap.unwrap(service).getProtocolMetas());
    }

    private ServiceEndpointData[] endpoints(MetBindInfo<?> service, Map<String, ProtocolMeta> publications) {
        List<ServiceEndpointData> result = new ArrayList<>();
        for (ConnectorConfig connector : this.context.getSettings().getConnectorConfigs()) {
            boolean provider = service.getServiceType() == MetServiceType.Provider;
            if (provider && !connector.bindEnabled()) {
                continue;
            }

            for (ProtocolConfig protocol : connector.protocols()) {
                ProtocolMeta meta = publications.get(protocol.name());
                ProtocolBinding binding = meta == null ? null : meta.getEndpoint(connector.name());
                if (binding == null) {
                    continue;
                }

                // 服务只上报实际选择的上下文；不使用上下文的协议仍保留一个端点。
                List<String> paths;
                if (binding.getContextPath() == null) {
                    paths = Collections.singletonList(null);
                } else {
                    paths = List.copyOf(binding.getContextPath().getPaths());
                }

                InterAddress listen = connector.bindEnabled() ? this.context.getListenAddress(//
                        connector.name(), protocol.name(), protocol.scheme()) : null;
                for (String path : paths) {
                    ServiceEndpointData endpoint = new ServiceEndpointData();
                    endpoint.setTransport(connector.transportType());
                    endpoint.setDescription(this.protocolDescription(connector, protocol, binding));
                    endpoint.setProtocolName(protocol.name());
                    endpoint.setDefaultProtocol(StringUtils.equalsIgnoreCase(protocol.name(), this.context.getDefaultProtocol()));
                    endpoint.setScheme(protocol.scheme());
                    endpoint.setContextPath(path);
                    if (provider && listen != null) {
                        endpoint.setEndpoints(this.serviceEndpoints(connector, protocol, listen, path, binding));
                    }
                    String clientPath = HttpServicePathUtils.clientPath(binding, path);
                    endpoint.setListenAddress(listen == null ? null : new InterAddress(listen.getSchema(), listen.getHost(), listen.getPort(), clientPath).toString());
                    result.add(endpoint);
                }
            }
        }

        return result.toArray(ServiceEndpointData[]::new);
    }

    private ObjectNode description(MetBindInfo<?> service) {
        ServiceMeta declaration = MetBindInfoWrap.unwrap(service).getServiceMeta();
        ObjectNode description = declaration.createServiceDescription();
        description.putObject("javaDeclaration")
                .put("group", declaration.getGroup())
                .put("name", declaration.getName())
                .put("version", declaration.getVersion())
                .put("serializeType", declaration.getSerializeType());
        description.put("message", service.isMessage());
        if (service.isMessage()) {
            description.withArray("methods").forEach(method -> ((ObjectNode) method).put("message", true));
        }
        return description;
    }

    private ObjectNode protocolDescription(ConnectorConfig connector, ProtocolConfig protocol, ProtocolBinding binding) {
        ObjectNode contract = binding.getDescription();
        if (contract == null) {
            return null;
        }
        ObjectNode snapshot = contract.deepCopy();
        // 默认路径推导只参与本地路由，管理侧只接收已经计算完成的真实地址。
        snapshot.remove(List.of("serviceScoped", "serviceBasePath", "servicePathName"));
        // 只上报客户端互通所需的公开连接事实，不携带容器名称、密钥或本地监听配置。
        ObjectNode connection = snapshot.putObject("connection");
        boolean tls = Boolean.parseBoolean(connector.options().getOrDefault("tlsEnabled", "false"));
        connection.put("tlsEnabled", tls);
        if (StringUtils.equals(connector.transportType(), "http")) {
            String scheme = connector.options().getOrDefault("httpScheme", "http");
            connection.put("httpScheme", protocol.option("httpScheme", scheme));
            String upgrade = protocol.option("upgrade", null);
            if (StringUtils.isNotBlank(upgrade)) {
                connection.put("upgrade", upgrade);
            }
        }
        return snapshot;
    }

    private InterAddress toPublishAddress(InterAddress address) throws Exception {
        if (StringUtils.isNotBlank(this.publishHost)) {
            return new InterAddress(address.getSchema(), this.publishHost, address.getPort(), address.getPath());
        }
        if (address.toSocketAddress().getAddress().isAnyLocalAddress()) {
            throw new IllegalArgumentException("Set registry publishHost when binding a wildcard address");
        }
        return address;
    }

}
