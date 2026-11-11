/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.CollectionUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.logging.Logger;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.connector.transport.http.HttpProtocolProvider;
import net.hasor.meteor.domain.*;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;

/** Prepares endpoint protocol mounts, then maintains routes from service publication events. */
final class RouteServiceRegistry {
    private static final Logger                                         logger       = Logger.getLogger(RouteServiceRegistry.class);
    private final        MetContext                                     context;
    private final        Map<String, RouteEndpoint>                     endpoints    = new LinkedHashMap<>();
    private final        Map<MetBindInfo<?>, Map<String, ProtocolMeta>> publications = new IdentityHashMap<>();

    RouteServiceRegistry(MetContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Prepare every configured endpoint before runtime endpoint construction. */
    synchronized void prepare(Collection<PlanEndpoint<?>> plans) {
        Map<String, RouteEndpoint> prepared = new LinkedHashMap<>();
        for (PlanEndpoint<?> plan : plans) {
            RouteEndpoint endpoint = new RouteEndpoint(plan);
            prepared.put(plan.config().name(), endpoint);
        }

        this.endpoints.clear();
        this.endpoints.putAll(prepared);
    }

    /** Bind once to a prepared endpoint; route lookups read its current service paths directly. */
    synchronized Function<String, RouteSelection> resolver(String endpointName) {
        RouteEndpoint endpoint = this.endpoints.get(endpointName);
        return endpoint::selectService;
    }

    /** Validate every endpoint first and publish all service routes as one change. */
    synchronized void registerService(MetBindInfo<?> service) {
        // 并发移除可能先于登记回调到达，不能重新加入已回收服务的地址。
        if (this.context.getServiceInfo(service.getBindID()) != service) {
            return;
        }

        // 第一阶段为每个端点完成协议校验、冲突检查，并生成下一版只读快照。
        ServiceDomain<?> domain = MetBindInfoWrap.unwrap(service);
        this.validateContexts(service, domain);
        Map<String, ProtocolMeta> metas = new LinkedHashMap<>();
        for (Map.Entry<String, ProtocolMeta> entry : domain.getProtocolMetas().entrySet()) {
            ProtocolMeta previous = entry.getValue();
            ContextPath declared = previous.getContextPath();
            if (declared != null || previous.getBindResource() != null) {
                Object resource = previous.getBindResource();
                ProtocolMeta meta = new ProtocolMeta(service, declared, resource, new LinkedHashMap<>());
                metas.put(entry.getKey(), meta);
            }
        }

        // 各端点直接向本轮候选元信息收集绑定，准备期间不修改已发布对象。
        boolean provider = !service.isShadow() && service.getServiceType() == MetServiceType.Provider;
        for (RouteEndpoint endpoint : this.endpoints.values()) {
            endpoint.prepare(service, this.context, metas);
        }

        // 所有端点的协议准备完成后，再统一构建候选索引并检查冲突。
        Map<String, ProtocolMeta> inbound = provider ? metas : Map.of();
        for (RouteEndpoint endpoint : this.endpoints.values()) {
            endpoint.validateServiceKeys(service, inbound, this.publications);
        }
        Map<RouteEndpoint, Map<String, RouteSelection>> candidates = this.prepareServiceIndexes(service, inbound);

        // 文件请求仍走正常路径选路；发布前检查文件入口不能被其他挂载或精确服务路径遮蔽。
        for (RouteEndpoint endpoint : this.endpoints.values()) {
            Map<String, RouteSelection> serviceRoutes = candidates.getOrDefault(endpoint, endpoint.serviceRoutes);
            endpoint.validateResourceRoutes(service, inbound, this.publications, serviceRoutes);
        }

        // 第二阶段统一提交本轮完整元信息和服务路由快照。
        domain.replaceProtocolPublication(metas);
        this.publications.put(service, provider ? domain.getProtocolMetas() : Map.of());
        this.applyServiceIndexes(candidates);
    }

    synchronized void removeService(MetBindInfo<?> service) {
        // 先按已提交快照准备撤销，其他服务共享的路径仍参与索引重建。
        Map<RouteEndpoint, Map<String, RouteSelection>> candidates = this.prepareServiceIndexes(service, Map.of());

        this.publications.remove(service);
        MetBindInfoWrap.unwrap(service).replaceProtocolPublication(Map.of());
        this.applyServiceIndexes(candidates);
    }

    synchronized void clear() {
        for (MetBindInfo<?> service : this.publications.keySet()) {
            MetBindInfoWrap.unwrap(service).replaceProtocolPublication(Map.of());
        }

        this.publications.clear();
        for (RouteEndpoint endpoint : this.endpoints.values()) {
            endpoint.serviceRoutes = Collections.emptyMap();
        }
        this.endpoints.clear();
    }

    /** 收集各端点的候选索引，只跳过 null，空 Map 仍需提交。 */
    private Map<RouteEndpoint, Map<String, RouteSelection>> prepareServiceIndexes(MetBindInfo<?> replaced, //
            Map<String, ProtocolMeta> replacement) {
        Map<RouteEndpoint, Map<String, RouteSelection>> candidates = new LinkedHashMap<>();
        for (RouteEndpoint endpoint : this.endpoints.values()) {
            Map<String, RouteSelection> serviceRoutes = this.prepareServiceIndex(endpoint, replaced, replacement);
            if (serviceRoutes != null) {
                candidates.put(endpoint, serviceRoutes);
            }
        }
        return candidates;
    }

    /** 准备全部成功后替换索引，服务元信息和发布快照由对应生命周期入口提交。 */
    private void applyServiceIndexes(Map<RouteEndpoint, Map<String, RouteSelection>> candidates) {
        for (Map.Entry<RouteEndpoint, Map<String, RouteSelection>> entry : candidates.entrySet()) {
            entry.getKey().serviceRoutes = entry.getValue();
        }
    }

    /** null 表示无需更新，空 Map 表示清空旧索引。 */
    private Map<String, RouteSelection> prepareServiceIndex(RouteEndpoint endpoint, MetBindInfo<?> replaced, //
            Map<String, ProtocolMeta> replacement) {
        if (!StringUtils.equalsIgnoreCase(endpoint.config.transportType(), "http")) {
            return null;
        }

        // 本轮候选只属于当前端点；没有新增或旧服务路由时保持现有索引。
        Map<String, RouteSelection> selected = new LinkedHashMap<>();
        endpoint.addServiceRoutes(replacement, selected);
        Map<String, ProtocolMeta> previous = this.publications.getOrDefault(replaced, Map.of());
        if (selected.isEmpty() && !endpoint.hasServiceRoutes(previous)) {
            return null;
        }

        // 其他服务只读取上次提交的协议结果，不读取正在变更的服务声明。
        for (Map.Entry<MetBindInfo<?>, Map<String, ProtocolMeta>> entry : this.publications.entrySet()) {
            if (entry.getKey() != replaced) {
                endpoint.addServiceRoutes(entry.getValue(), selected);
            }
        }
        return Collections.unmodifiableMap(selected);
    }

    private void validateContexts(MetBindInfo<?> service, ServiceDomain<?> domain) {
        for (Map.Entry<String, ProtocolMeta> entry : domain.getProtocolMetas().entrySet()) {
            ContextPath selected = entry.getValue().getContextPath();
            if (selected == null) {
                continue;
            }

            String name = entry.getKey();
            List<ProtocolMountBinding<?>> configured = this.selectProtocols(name);
            if (configured.isEmpty()) {
                throw new IllegalArgumentException("Context selected for unknown protocol: " + name);
            }

            for (ProtocolMountBinding<?> protocol : configured) {
                this.validateContext(service, name, selected, protocol);
            }
        }
    }

    private void validateContext(MetBindInfo<?> service, String name, ContextPath selected, //
            ProtocolMountBinding<?> protocol) {
        RouteMountIndex routes = protocol.routes();
        if (!routes.accepts(service.getBindProtocols())) {
            throw new IllegalArgumentException("Context selected for unbound protocol: " + name);
        }

        // 此时发布结果尚未提交，只检查原始路径声明，不读取上一次端点发布结果。
        String transport = protocol.provider().transportType();
        if (!StringUtils.equalsIgnoreCase(transport, "http") || routes.contextPath() == null) {
            throw new IllegalArgumentException("Service paths require an HTTP protocol mount: " + name);
        }

        // 服务路径只能位于协议已声明的挂载点内，精确匹配也不能绕过该边界。
        for (String path : selected.resolve(routes.contextPath()).getPaths()) {
            if (routes.match(path) == null) {
                String message = "Context is not configured for protocol " + name + ": " + path;
                throw new IllegalArgumentException(message);
            }
        }
    }

    private List<ProtocolMountBinding<?>> selectProtocols(String selector) {
        List<ProtocolMountBinding<?>> selected = new ArrayList<>();
        for (RouteEndpoint endpoint : this.endpoints.values()) {
            for (ProtocolMountBinding<?> protocol : endpoint.protocols) {
                if (StringUtils.equalsIgnoreCase(protocol.config().name(), selector)) {
                    selected.add(protocol);
                }
            }
        }
        return selected;
    }

    private static void addRoute(Map<String, RouteSelection> selected, RouteSelection route) {
        RouteSelection previous = selected.putIfAbsent(route.mount().path(), route);
        if (previous != null && (!StringUtils.equals(previous.protocolName(), route.protocolName()) || previous.mount().exact() != route.mount().exact())) {
            throw new IllegalArgumentException("Conflicting service path: " + route.mount().path());
        }
    }

    private static final class RouteEndpoint {
        private final    ConnectorConfig                               config;
        private final    Collection<? extends ProtocolMountBinding<?>> protocols;
        private volatile Map<String, RouteSelection>                   serviceRoutes = Collections.emptyMap();

        private RouteEndpoint(PlanEndpoint<?> plan) {
            this.config = plan.config();
            // 启动计划已经固定协议名与挂载关系，直接借用其只读集合。
            this.protocols = plan.protocolBindings().values();
        }

        private void prepare(MetBindInfo<?> service, MetContext context, Map<String, ProtocolMeta> metas) {
            for (ProtocolMountBinding<?> protocol : this.protocols) {
                if (!protocol.routes().accepts(service.getBindProtocols())) {
                    continue;
                }

                // 一个协议只创建一份本轮元信息，各端点共用原始声明。
                ProtocolConfig config = protocol.config();
                String name = config.name();
                ProtocolMeta meta = metas.get(name);
                if (meta == null) {
                    meta = new ProtocolMeta(service, null, null, new LinkedHashMap<>());
                    metas.put(name, meta);
                }

                // 协议一次准备完整定义、描述和路径，不修改已提交绑定或共享声明。
                ProtocolBinding prepared = protocol.provider().prepareBinding(config, context, meta);
                if (prepared == null) {
                    String message = "Protocol " + name + " rejected service " + service.getBindID();
                    logger.warn(message + " at endpoint " + this.config.name());
                    continue;
                }

                // 绑定按连接器名称加入本轮候选，所有端点准备成功后再统一发布。
                meta.getEndpoints().put(this.config.name(), prepared);
            }
        }

        private boolean hasServiceRoutes(Map<String, ProtocolMeta> metas) {
            for (ProtocolMeta meta : metas.values()) {
                if (this.servicePaths(meta) != null) {
                    return true;
                }
            }
            return false;
        }

        private void validateServiceKeys(MetBindInfo<?> service, Map<String, ProtocolMeta> metas, Map<MetBindInfo<?>, Map<String, ProtocolMeta>> publications) {
            for (ProtocolMountBinding<?> protocol : this.protocols) {
                String name = protocol.config().name();
                ProtocolMeta meta = metas.get(name);
                ProtocolBinding binding = meta == null ? null : meta.getEndpoint(this.config.name());
                if (binding == null) {
                    continue;
                }
                List<List<String>> keys = new ArrayList<>(binding.getRouteKeys());
                if (protocol.provider() instanceof HttpProtocolProvider http) {
                    List<String> key = http.serviceKey(service, binding);
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }

                // 只比较其他服务已经提交的同端点绑定，本轮失败不改动旧发布状态。
                for (Map.Entry<MetBindInfo<?>, Map<String, ProtocolMeta>> entry : publications.entrySet()) {
                    if (entry.getKey() == service) {
                        continue;
                    }
                    ProtocolMeta previous = entry.getValue().get(name);
                    ProtocolBinding existing = previous == null ? null : previous.getEndpoint(this.config.name());
                    if (existing == null) {
                        continue;
                    }
                    if (protocol.provider() instanceof HttpProtocolProvider && !this.overlappingPaths(binding, existing)) {
                        continue;
                    }
                    List<List<String>> previousKeys = new ArrayList<>(existing.getRouteKeys());
                    if (protocol.provider() instanceof HttpProtocolProvider http) {
                        List<String> key = http.serviceKey(entry.getKey(), existing);
                        if (!key.isEmpty()) {
                            previousKeys.add(key);
                        }
                    }
                    if (CollectionUtils.containsAny(keys, previousKeys)) {
                        String message = "Conflicting service route for protocol " + name + " at endpoint ";
                        throw new IllegalArgumentException(message + this.config.name() + ": " + keys //
                                + "; services " + entry.getKey().getBindID() + " and " + service.getBindID());
                    }
                }
            }
        }

        private void validateResourceRoutes(MetBindInfo<?> replaced, Map<String, ProtocolMeta> replacement, //
                Map<MetBindInfo<?>, Map<String, ProtocolMeta>> publications, Map<String, RouteSelection> serviceRoutes) {
            if (!StringUtils.equalsIgnoreCase(this.config.transportType(), "http")) {
                return;
            }
            Map<String, String> owners = new HashMap<>();
            this.validateServiceResourceRoutes(replaced, replacement, serviceRoutes, owners);
            for (Map.Entry<MetBindInfo<?>, Map<String, ProtocolMeta>> entry : publications.entrySet()) {
                if (entry.getKey() != replaced) {
                    this.validateServiceResourceRoutes(entry.getKey(), entry.getValue(), serviceRoutes, owners);
                }
            }
        }

        private void validateServiceResourceRoutes(MetBindInfo<?> service, Map<String, ProtocolMeta> metas, //
                Map<String, RouteSelection> serviceRoutes, Map<String, String> owners) {
            for (ProtocolMountBinding<?> protocol : this.protocols) {
                ProtocolMeta meta = metas.get(protocol.config().name());
                ProtocolBinding binding = meta == null ? null : meta.getEndpoint(this.config.name());
                if (binding == null || binding.getDescription() == null) {
                    continue;
                }
                JsonNode definitions = binding.getDescription().path("endpoints");
                for (JsonNode addresses : definitions) {
                    Iterator<Map.Entry<String, JsonNode>> fields = addresses.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        if (!field.getValue().isTextual()) {
                            continue;
                        }
                        URI uri = URI.create(field.getValue().asText());
                        // 相对文件入口由当前监听器负责；绝对公开地址的转发由应用的代理配置负责。
                        boolean resource = StringUtils.equals(field.getKey(), "schema");
                        if (uri.isAbsolute() || !resource) {
                            continue;
                        }
                        String path = uri.getRawPath();
                        RouteSelection selected = this.selectService(path, serviceRoutes);
                        if (selected == null || !selected.mount().exact()) {
                            for (ProtocolMountBinding<?> candidate : this.protocols) {
                                RouteMount mount = candidate.routes().match(path);
                                if (mount == null) {
                                    continue;
                                }
                                if (selected == null || mount.path().length() > selected.mount().path().length()) {
                                    selected = new RouteSelection(candidate.config().name(), mount, candidate.config().options());
                                }
                            }
                        }
                        ContextPath contexts = binding.getContextPath();
                        boolean owned = selected != null && StringUtils.equals(selected.protocolName(), protocol.config().name());
                        owned = owned && contexts != null && contexts.isExact() == selected.mount().exact() //
                                && contexts.getPaths().contains(selected.mount().path());
                        if (!owned) {
                            throw new IllegalArgumentException("Schema endpoint is shadowed by another route: " + uri);
                        }
                        String previous = owners.putIfAbsent(uri.toString(), service.getBindID());
                        if (previous != null && !StringUtils.equals(previous, service.getBindID())) {
                            throw new IllegalArgumentException("Conflicting schema endpoint: " + uri);
                        }
                    }
                }
            }
        }

        private boolean overlappingPaths(ProtocolBinding first, ProtocolBinding second) {
            ContextPath left = first.getContextPath();
            ContextPath right = second.getContextPath();
            return left != null && right != null && left.isExact() == right.isExact() //
                    && CollectionUtils.containsAny(left.getPaths(), right.getPaths());
        }

        private void addServiceRoutes(Map<String, ProtocolMeta> metas, Map<String, RouteSelection> selected) {
            for (ProtocolMountBinding<?> protocol : this.protocols) {
                ProtocolConfig config = protocol.config();
                ProtocolMeta meta = metas.get(config.name());
                if (meta == null) {
                    continue;
                }

                ContextPath paths = this.servicePaths(meta);
                if (paths == null) {
                    continue;
                }

                for (String path : paths.getPaths()) {
                    // 端点配置在启动时固定，同一路径和协议可复用现有选择对象。
                    RouteSelection route = this.serviceRoutes.get(path);
                    if (route == null || !StringUtils.equals(route.protocolName(), config.name()) //
                            || route.mount().exact() != paths.isExact()) {
                        route = new RouteSelection(config.name(), new RouteMount(path, paths.isExact()), config.options());
                    }
                    addRoute(selected, route);
                }
            }
        }

        private ContextPath servicePaths(ProtocolMeta meta) {
            ProtocolBinding binding = meta.getEndpoint(this.config.name());
            if (binding == null) {
                return null;
            }

            ContextPath paths = binding.getContextPath();
            boolean scoped = binding.getDescription() != null && binding.getDescription().path("serviceScoped").asBoolean();
            return paths != null && (meta.getContextPath() != null || paths.isExact() || scoped) ? paths : null;
        }

        private RouteSelection selectService(String uri) {
            Map<String, RouteSelection> routes = this.serviceRoutes;
            if (routes.isEmpty()) {
                return null;
            }

            int query = uri.indexOf('?');
            String path = query < 0 ? uri : uri.substring(0, query);
            return this.selectService(path, routes);
        }

        private RouteSelection selectService(String path, Map<String, RouteSelection> routes) {
            RouteSelection direct = routes.get(path);
            if (direct != null) {
                return direct;
            }
            int end = path.length();
            while ((end = path.lastIndexOf('/', end - 1)) >= 0) {
                RouteSelection prefix = routes.get(path.substring(0, end));
                if (prefix != null && !prefix.mount().exact()) {
                    return prefix;
                }
            }
            return null;
        }
    }
}
