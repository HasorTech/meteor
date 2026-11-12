/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.service;
import java.util.Map;
import net.hasor.core.Inject;
import net.hasor.core.Singleton;
import net.hasor.meteor.console.component.commands.CommandDispatcher;
import net.hasor.meteor.console.component.commands.SuccessResult;
import net.hasor.meteor.console.component.commands.dto.directory.*;
import net.hasor.meteor.console.component.commands.dto.instance.InstanceInfoRequest;
import net.hasor.meteor.console.component.commands.dto.instance.InstanceInfoResult;
import net.hasor.meteor.console.component.commands.dto.instance.SetInstanceBlockedRequest;
import net.hasor.meteor.console.component.commands.dto.metadata.FetchMetadataRequest;
import net.hasor.meteor.console.component.commands.dto.metadata.MetadataResult;
import net.hasor.meteor.console.component.commands.dto.metadata.PutMetadataRequest;
import net.hasor.meteor.console.component.commands.dto.metadata.RemoveMetadataRequest;
import net.hasor.meteor.console.component.commands.dto.rule.*;
import net.hasor.meteor.console.component.commands.dto.service.*;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.meteor.registry.admin.MetServiceMethodPage;
import net.hasor.meteor.registry.admin.MetDirectoryQuery;

/** 按管理目标选择命令入口，上层业务不接触具体提供者。 */
@Singleton
public final class GovernanceService {
    @Inject
    private CommandDispatcher dispatcher;

    public ClusterInfoResult clusterInfo(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new ClusterInfoRequest(target));
    }

    public UnitsResult units(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new UnitsRequest(target));
    }

    public ServicesResult services(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new ServicesRequest(target));
    }

    public InstancesResult instances(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new InstancesRequest(target));
    }

    public ApplicationsResult applications(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new ApplicationsRequest(target));
    }

    public InstanceInfoResult instanceInfo(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new InstanceInfoRequest(target));
    }

    public MetServiceMethodPage methods(ManagementTarget target, String methodName,
            MetDirectoryQuery query) {
        return this.dispatcher.execute(target.getClusterId(), new ServiceMethodsRequest(target, methodName, query));
    }

    public ServiceInfoResult serviceInfo(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new ServiceInfoRequest(target));
    }

    public ExampleTargetsResult exampleTargets(ManagementTarget target, String protocol, //
            String address, String contextPath) {
        ExampleTargetsRequest request = new ExampleTargetsRequest(target, protocol, address, contextPath);
        return this.dispatcher.execute(target.getClusterId(), request);
    }

    public MembersResult members(ManagementTarget target, MetServiceType role) {
        return this.dispatcher.execute(target.getClusterId(), new MembersRequest(target, role));
    }

    public RuleResult rule(ManagementTarget target, MetRuleType type) {
        return this.dispatcher.execute(target.getClusterId(), new ReadRuleRequest(target, type));
    }

    public RuleValidationResult validateRule(ManagementTarget target, MetRuleType type, String rule) {
        return this.dispatcher.execute(target.getClusterId(), new ValidateRuleRequest(target, type, rule));
    }

    public SuccessResult updateRule(ManagementTarget target, MetRuleType type, String rule) {
        return this.dispatcher.execute(target.getClusterId(), new UpdateRuleRequest(target, type, rule));
    }

    public SuccessResult resetRule(ManagementTarget target, MetRuleType type) {
        return this.dispatcher.execute(target.getClusterId(), new ResetRuleRequest(target, type));
    }

    public SuccessResult ruleEnabled(ManagementTarget target, MetRuleType type, boolean enabled) {
        return this.dispatcher.execute(target.getClusterId(), new SetRuleEnabledRequest(target, type, enabled));
    }

    public SuccessResult serviceEnabled(ManagementTarget target, boolean enabled) {
        return this.dispatcher.execute(target.getClusterId(), new SetServiceEnabledRequest(target, enabled));
    }

    public SuccessResult instanceBlocked(ManagementTarget target, boolean blocked) {
        return this.dispatcher.execute(target.getClusterId(), new SetInstanceBlockedRequest(target, blocked));
    }

    public MetadataResult fetchMeta(ManagementTarget target) {
        return this.dispatcher.execute(target.getClusterId(), new FetchMetadataRequest(target));
    }

    public SuccessResult putMeta(ManagementTarget target, Map<String, Object> values) {
        return this.dispatcher.execute(target.getClusterId(), new PutMetadataRequest(target, values));
    }

    public SuccessResult removeMeta(ManagementTarget target, String[] keys) {
        return this.dispatcher.execute(target.getClusterId(), new RemoveMetadataRequest(target, keys));
    }

}
