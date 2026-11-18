/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;

/** 上下文内按条件放行网络 RPC 的访问策略，通过 {@link MetContext#getExtension(Class)} 获取。 */
public interface MetAccessPolicy {
    /**
     * 允许当前上下文中的本地绑定在指定限制下继续执行网络 RPC。
     * 多次登记合并条件。使用 {@link MetAccessEnum#values()} 可放行所有条件，空集合不授权。
     * 每个生效的限制都必须获得授权。绑定移除时自动撤销，后续过滤器照常执行。
     */
    void allow(MetBindInfo<?> binding, MetAccessEnum... conditions);
}
