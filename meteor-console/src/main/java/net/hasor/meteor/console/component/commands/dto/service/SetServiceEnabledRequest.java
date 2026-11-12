/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.commands.dto.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.hasor.meteor.console.component.commands.SuccessResult;
import net.hasor.meteor.console.component.commands.CommandRequest;
import net.hasor.meteor.console.component.commands.CommandType;
import net.hasor.meteor.console.model.fo.ManagementTarget;

public final class SetServiceEnabledRequest extends CommandRequest<SuccessResult> {
    private final boolean enabled;

    @JsonCreator
    public SetServiceEnabledRequest(@JsonProperty("target") ManagementTarget target, @JsonProperty("enabled") boolean enabled) {
        super(target);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public CommandType getCommand() {
        return CommandType.SET_SERVICE_ENABLED;
    }
}
