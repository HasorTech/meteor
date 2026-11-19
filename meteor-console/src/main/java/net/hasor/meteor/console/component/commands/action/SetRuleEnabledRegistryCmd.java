/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.commands.action;
import net.hasor.meteor.console.component.commands.CommandContext;
import net.hasor.meteor.console.component.commands.RegistryCommand;
import net.hasor.meteor.console.component.commands.SuccessResult;
import net.hasor.meteor.console.component.commands.dto.rule.SetRuleEnabledRequest;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.registry.admin.MetRuleType;

/** 切换注册中心保存的规则状态，保留其正文。 */
public final class SetRuleEnabledRegistryCmd implements RegistryCommand<SetRuleEnabledRequest, SuccessResult> {
    @Override
    public SuccessResult execute(CommandContext context, SetRuleEnabledRequest request) {
        ManagementTarget target = request.getTarget();
        MetRuleType type = request.getRuleType();
        boolean enabled = request.isEnabled();
        if (target.isDirect()) {
            throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_TOGGLING_A_RULE_REQUIRES_THE_REGISTRY);
        }
        switch (context.scope(target)) {
            case "cluster" -> {
                if (enabled) {
                    context.cluster().enableRule(type);
                } else {
                    context.cluster().disableRule(type);
                }
            }
            case "instance" -> {
                if (enabled) {
                    context.instance(target).enableRule(type);
                } else {
                    context.instance(target).disableRule(type);
                }
            }
            case "service" -> {
                if (enabled) {
                    context.service(target).enableRule(type);
                } else {
                    context.service(target).disableRule(type);
                }
            }
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_UNKNOWN_GOVERNANCE_SCOPE);
        }
        return new SuccessResult(true);
    }
}
