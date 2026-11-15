/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.text.token.GenericTokenParser;
import net.hasor.core.AnnotationInjector;
import net.hasor.core.AppContext;
import net.hasor.meteor.annotation.MetAddress;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;

/** Resolves a field reference without changing the shared service binding or address book. */
final class AddressInjector implements AnnotationInjector<MetAddress> {
    private final Supplier<MetContext> context;

    AddressInjector(Supplier<MetContext> context) {
        this.context = context;
    }

    @Override
    public Object resolve(AppContext app, Field field, MetAddress annotation) throws Exception {
        if (!field.getType().isInterface()) {
            throw new IllegalArgumentException("@MetAddress requires a service interface: " + field);
        }

        String template = annotation.value();
        String address = new GenericTokenParser(new String[] { "${" }, "}", (found, open, close, content) -> {
            int split = content.indexOf(':');
            String key = split < 0 ? content : content.substring(0, split);
            String fallback = split < 0 ? null : content.substring(split + 1);
            String value = app.getSettings().getString(key, fallback);
            if (value == null) {
                throw new IllegalArgumentException("Missing @MetAddress configuration: " + key);
            }
            return value;
        }).parse(template);

        if (StringUtils.isBlank(address) || address.contains("${")) {
            throw new IllegalArgumentException("Unresolved @MetAddress: " + template);
        }

        MetContext meteor = this.context.get();
        MetClient client = meteor.getRsfClient(address.trim());
        return createProxy(meteor, client, field.getType());
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