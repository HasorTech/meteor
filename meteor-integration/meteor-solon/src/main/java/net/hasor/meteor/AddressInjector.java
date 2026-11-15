/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.lang.annotation.Annotation;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.annotation.MetAddress;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.BeanInjector;
import org.noear.solon.core.VarHolder;

/** Defers proxy creation until the plugin has registered the shared MetContext. */
final class AddressInjector implements BeanInjector<MetAddress> {
    @Override
    public void doInject(VarHolder holder, MetAddress annotation) {
        if (!holder.isField() || !holder.getType().isInterface()) {
            throw new IllegalArgumentException("@MetAddress requires a service interface field: " + holder.getFullName());
        }

        for (Annotation candidate : holder.getAnnoS()) {
            if (candidate.annotationType() == Inject.class) {
                throw new IllegalArgumentException("Multiple injection sources for " + holder.getFullName());
            }
        }

        String template = annotation.value();
        String address = holder.context().cfg().getByTmpl(template);
        if (StringUtils.isBlank(address) || address.contains("${")) {
            throw new IllegalArgumentException("Unresolved @MetAddress: " + template);
        }

        holder.context().getBeanAsync(MetContext.class, context -> {
            try {
                MetClient client = context.getRsfClient(address.trim());
                holder.setValue(createProxy(context, client, holder.getType()));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot inject @MetAddress: " + holder.getFullName(), e);
            }
        });
    }

    private static <T> T createProxy(MetContext context, MetClient client, Class<T> type) {
        MetBindInfo<T> service = context.getServiceInfo(type);
        if (service == null) {
            throw new IllegalArgumentException("Undefined @MetAddress service: " + type.getName());
        }

        // Force RPC for this reference only; ordinary injection retains local-provider preference.
        MetBindInfo<T> reference = new MetBindInfoWrap<T>(service) {
            @Override
            public boolean isDistributedCall() {
                return true;
            }
        };
        return client.getRemote(reference);
    }
}