/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.commands;
import java.util.concurrent.Callable;
import net.hasor.cobble.StringUtils;
import net.hasor.core.AppContext;
import net.hasor.core.Inject;
import net.hasor.core.Singleton;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.auth.SessionService;
import net.hasor.meteor.registry.RegistryTokenProvider;

/** 管理连接按本次调用取凭据，缓存连接不捕获某个 Telnet 或浏览器会话。 */
@Singleton
public final class CommandCredentials implements RegistryTokenProvider {
    @Inject
    private       AppContext          appContext;
    @Inject
    private       MeteorConfig        config;
    private final ThreadLocal<String> token = new ThreadLocal<>();

    public <T> T withToken(String value, Callable<T> operation) throws Exception {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Management credentials must not be blank");
        }
        String previous = this.token.get();
        this.token.set(value);
        try {
            return operation.call();
        } finally {
            if (previous == null) {
                this.token.remove();
            } else {
                this.token.set(previous);
            }
        }
    }

    @Override
    public String getToken() {
        String current = this.token.get();
        if (current != null) {
            return current;
        }
        if (!this.config.isWebEnabled()) {
            throw new IllegalStateException("No management credentials in current call");
        }
        return this.appContext.getInstance(SessionService.class).getToken();
    }
}
