/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.commands.action;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.CommandContext;
import net.hasor.meteor.console.component.commands.RegistryCommand;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTargetsRequest;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTargetsResult;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.admin.*;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;

/** 从当前提供者登记和端点契约收集示例目标，不猜测协议名称或业务地址。 */
public final class ExampleTargetsRegistryCmd implements RegistryCommand<ExampleTargetsRequest, ExampleTargetsResult> {
    @Override
    public ExampleTargetsResult execute(CommandContext context, ExampleTargetsRequest request) {
        ManagementTarget target = request.getTarget();
        if (StringUtils.isBlank(request.getProtocol()) && StringUtils.isBlank(target.getInstanceId()) && !target.isDirect()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_AN_INSTANCE_IS_REQUIRED_TO_LIST);
        }

        boolean scoped = StringUtils.isNotBlank(target.getServiceId()) || StringUtils.isNotBlank(target.getInstanceId());
        if (!target.isDirect() && !scoped) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_SERVICE_OR_INSTANCE_IS_REQUIRED);
        }

        ExampleTargetsResult result = new ExampleTargetsResult();
        if (!target.isDirect()) {
            result.setRegistry(context.cluster().info());
        }

        Set<String> warnings = new LinkedHashSet<>();
        for (String serviceId : this.services(context, request)) {
            ManagementTarget serviceTarget = target.atInstance(target.getInstanceId());
            serviceTarget.setServiceId(serviceId);
            this.collect(context.service(serviceTarget), request, result.getTargets(), warnings);
        }
        result.setWarnings(new ArrayList<>(warnings));
        return result;
    }

    private List<String> services(CommandContext context, ExampleTargetsRequest request) {
        ManagementTarget target = request.getTarget();
        if (StringUtils.isNotBlank(target.getServiceId())) {
            return List.of(target.getServiceId());
        }

        // 实例的协议入口可能用于多个服务，逐页读取提供者声明，让调用者明确选择服务。
        MetInstanceAdmin instance = context.instance(target);
        MetDirectoryQuery query = new MetDirectoryQuery();
        query.setLimit(1000);
        query.setProtocol(request.getProtocol());
        List<String> services = new ArrayList<>();
        do {
            MetPage<MetServiceInfo> page = instance.getServiceList(query);
            for (MetServiceInfo service : page.getItems()) {
                if (service.getRole() == MetServiceType.Provider) {
                    services.add(service.getServiceId());
                }
            }
            query.setCursor(page.getNextCursor());
        } while (query.getCursor() != null);
        return services;
    }

    private void collect(MetServiceAdmin admin, ExampleTargetsRequest request, //
            List<ExampleTarget> targets, Set<String> warnings) {
        if (!admin.isEnable()) {
            return;
        }
        Map<String, MetServiceDescription> descriptions = admin.descriptions();
        MetEndpointFilter filter = new MetEndpointFilter();
        filter.setProtocol(request.getProtocol());
        filter.setContextPath(request.getContextPath());
        MetDirectoryQuery query = new MetDirectoryQuery();
        query.setLimit(1000);
        do {
            MetPage<MetServiceMember> page = admin.providers(filter, query);
            for (MetServiceMember member : page.getItems()) {
                MetInstanceInfo instance = member.getInstance();
                if (!instance.isOnline() || instance.isBlock() || !member.isEnabled()) {
                    continue;
                }
                for (MetServiceEndpoint endpoint : member.getEndpoints()) {
                    if (this.matches(endpoint, request)) {
                        MetServiceDescription description = descriptions.get(instance.getInstanceId());
                        this.add(member, endpoint, description, request.getTarget(), targets, warnings);
                    }
                }
            }
            query.setCursor(page.getNextCursor());
        } while (query.getCursor() != null);
    }

    private boolean matches(MetServiceEndpoint endpoint, ExampleTargetsRequest request) {
        if (StringUtils.isNotBlank(request.getProtocol()) && !StringUtils.equals(request.getProtocol(), endpoint.getProtocolName())) {
            return false;
        }

        String endpointContext = StringUtils.defaultString(endpoint.getContextPath());
        if (request.getContextPath() != null && !StringUtils.equals(request.getContextPath(), endpointContext)) {
            return false;
        }
        if (StringUtils.isBlank(endpoint.getPublishedAddress()) //
                && (!request.getTarget().isDirect() || StringUtils.isBlank(endpoint.getListenAddress()))) {
            return false;
        }
        return StringUtils.isBlank(request.getAddress()) //
                || StringUtils.equals(request.getAddress(), this.address(endpoint, request.getTarget()));
    }

    private void add(MetServiceMember member, MetServiceEndpoint endpoint, MetServiceDescription description, //
            ManagementTarget source, List<ExampleTarget> targets, Set<String> warnings) {
        MetInstanceInfo instance = member.getInstance();
        MetServiceInfo service = member.getService();
        String identity = service.getServiceId() + " / " + instance.getInstanceId();
        if (description == null) {
            warnings.add(ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_EXAMPLE_MISSING_SERVICE_DESCRIPTION, identity));
            return;
        }

        if (endpoint.getDescription() == null) {
            String mount = endpoint.getProtocolName() + " / " + this.address(endpoint, source);
            warnings.add(ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_EXAMPLE_MISSING_ENDPOINT_CONTRACT, identity, mount));
            return;
        }

        ExampleTarget target = new ExampleTarget();
        // ID 只用于抽屉选择；后端生成示例时仍重新查询并验证当前服务登记。
        String endpointContext = StringUtils.defaultString(endpoint.getContextPath());
        String key = String.join("\0", service.getServiceId(), instance.getInstanceId(), //
                endpoint.getProtocolName(), this.address(endpoint, source), endpointContext);
        target.setId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
        target.setServiceId(service.getServiceId());
        target.setInstanceId(instance.getInstanceId());
        target.setApplication(instance.getApplicationName());
        target.setUnit(instance.getUnitName());
        target.setAddress(this.address(endpoint, source));
        target.setService(service);
        target.setEndpoint(endpoint);
        target.setDescription(description);
        targets.add(target);
    }

    private String address(MetServiceEndpoint endpoint, ManagementTarget source) {
        String selected = endpoint.getPublishedAddress();
        if (StringUtils.isBlank(selected) && source.isDirect()) {
            selected = endpoint.getListenAddress();
        }
        if (!source.isDirect()) {
            return selected;
        }

        // 全地址监听不是可复制的目标主机；只替换主机，保留协议、端口及地址路径。
        URI address = URI.create(selected);
        String host = address.getHost();
        Set<String> wildcardHosts = Set.of("0.0.0.0", "::", "[::]", "0:0:0:0:0:0:0:0", "[0:0:0:0:0:0:0:0]");
        if (host == null || !wildcardHosts.contains(host)) {
            return selected;
        }
        try {
            return new URI(address.getScheme(), address.getUserInfo(), source.getTarget().getHost(), //
                    address.getPort(), address.getPath(), address.getQuery(), address.getFragment()).toASCIIString();
        } catch (URISyntaxException invalid) {
            throw new ErrorMessageException(invalid, ConsoleMessageKey.ERROR_INVALID_DIRECT_SERVICE_ADDRESS_FOR_THE);
        }
    }
}
