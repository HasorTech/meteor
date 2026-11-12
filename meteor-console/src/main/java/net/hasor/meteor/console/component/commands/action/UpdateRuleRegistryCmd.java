/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.commands.action;
import net.hasor.meteor.console.component.commands.SuccessResult;
import net.hasor.meteor.console.component.commands.CommandContext;
import net.hasor.meteor.console.component.commands.RegistryCommand;
import net.hasor.meteor.console.component.commands.dto.rule.UpdateRuleRequest;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.registry.admin.MetRuleType;

/** 更新当前集群、实例或服务的规则，由对应管理入口校验并写入。 */
public final class UpdateRuleRegistryCmd implements RegistryCommand<UpdateRuleRequest, SuccessResult> {
    @Override
    public SuccessResult execute(CommandContext context, UpdateRuleRequest request) {
        ManagementTarget target = request.getTarget();
        MetRuleType type = request.getRuleType();
        String rule = request.getRule();
        switch (context.scope(target)) {
            case "cluster" -> context.cluster().updateRule(type, rule);
            case "instance" -> context.instance(target).updateRule(type, rule);
            case "service" -> context.service(target).updateRule(type, rule);
            default -> throw new IllegalArgumentException("未知治理范围");
        }
        return new SuccessResult(true);
    }
}
