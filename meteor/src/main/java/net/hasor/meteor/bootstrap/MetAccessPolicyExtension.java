/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.hasor.meteor.*;
import net.hasor.meteor.container.MetContainer;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;

final class MetAccessPolicyExtension implements MetExtension, MetAccessPolicy {
    private final MetContainer                            container;
    private final Map<MetBindInfo<?>, Set<MetAccessEnum>> allowed = new ConcurrentHashMap<>();
    private       boolean                                 closed;

    MetAccessPolicyExtension(MetContainer container) {
        this.container = container;
    }

    @Override
    public synchronized void allow(MetBindInfo<?> binding, MetAccessEnum... conditions) {
        Objects.requireNonNull(binding, "binding");
        if (this.closed) {
            throw new IllegalStateException("RPC access policy is closed");
        }

        MetBindInfo<?> registered = this.container.getRsfBindInfo(binding.getBindID());
        if (registered == null || registered != unwrap(binding)) {
            throw new IllegalArgumentException("Binding is not registered in this context: " + binding.getBindID());
        }

        EnumSet<MetAccessEnum> added = EnumSet.noneOf(MetAccessEnum.class);
        Collections.addAll(added, conditions);
        if (!added.isEmpty()) {
            added.addAll(this.allowed.getOrDefault(registered, Set.of()));
            this.allowed.put(registered, Set.copyOf(added));
        }
    }

    boolean allows(MetBindInfo<?> binding, MetBindInfo<?> registered, MetAccessEnum condition) {
        if (registered == null) {
            return false;
        }
        Set<MetAccessEnum> permitted = this.allowed.get(registered);
        return permitted != null && permitted.contains(condition) && registered == unwrap(binding);
    }

    synchronized void onServiceEvent(MetContextEvent event) {
        if (event.getType() == MetContextEventType.REMOVED) {
            this.allowed.remove(event.getServiceInfo());
        }
    }

    private static MetBindInfo<?> unwrap(MetBindInfo<?> binding) {
        if (binding instanceof MetBindInfoWrap<?>) {
            try {
                return MetBindInfoWrap.unwrap(binding);
            } catch (ClassCastException ignored) {
                // 外部包装可能包装自定义绑定，此类绑定不属于本地容器。
            }
        }
        return binding;
    }

    @Override
    public <T> T getService(Class<T> type) {
        return null;
    }

    @Override
    public void beforeStart() {
    }

    @Override
    public void afterStart() {
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.allowed.clear();
    }
}
