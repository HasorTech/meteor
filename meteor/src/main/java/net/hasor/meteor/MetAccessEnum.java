/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;

/** 可以通过本地绑定访问策略放行的 RPC 限制。 */
public enum MetAccessEnum {
    /** 当前实例处于阻断状态。 */
    INSTANCE_BLOCKED,
    /** 当前服务已停用。 */
    SERVICE_DISABLED
}
