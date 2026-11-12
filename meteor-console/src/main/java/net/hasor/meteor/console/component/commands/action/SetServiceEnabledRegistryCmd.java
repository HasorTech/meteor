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
import net.hasor.meteor.console.component.commands.dto.service.SetServiceEnabledRequest;
import net.hasor.meteor.registry.admin.MetServiceAdmin;

/** 切换当前服务或实例服务的可用状态。 */
public final class SetServiceEnabledRegistryCmd implements RegistryCommand<SetServiceEnabledRequest, SuccessResult> {
    @Override
    public SuccessResult execute(CommandContext context, SetServiceEnabledRequest request) {
        MetServiceAdmin admin = context.service(request.getTarget());
        if (request.isEnabled()) {
            admin.enable();
        } else {
            admin.disable();
        }
        return new SuccessResult(true);
    }
}
