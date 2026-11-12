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
import net.hasor.meteor.console.component.commands.dto.rule.ResetRuleRequest;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.registry.admin.MetRuleType;

/** 清除当前集群、实例或服务的规则。 */
public final class ResetRuleRegistryCmd implements RegistryCommand<ResetRuleRequest, SuccessResult> {
    @Override
    public SuccessResult execute(CommandContext context, ResetRuleRequest request) {
        ManagementTarget target = request.getTarget();
        MetRuleType type = request.getRuleType();
        switch (context.scope(target)) {
            case "cluster" -> context.cluster().resetRule(type);
            case "instance" -> context.instance(target).resetRule(type);
            case "service" -> context.service(target).resetRule(type);
            default -> throw new IllegalArgumentException("未知治理范围");
        }
        return new SuccessResult(true);
    }
}
