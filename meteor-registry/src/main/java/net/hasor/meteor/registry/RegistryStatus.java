/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.registry;
import java.time.Instant;
import net.hasor.cobble.StringUtils;

/** 注册接入的最近同步状态快照；CONNECTED 表示最近一次完整同步成功，不保证下一次 I/O 成功。 */
public final class RegistryStatus {
    private final String  provider;
    private final String  connection;
    private final Instant lastAttempt;
    private final Instant lastSuccess;
    private final String  error;

    public RegistryStatus(String provider, String connection, Instant lastAttempt, Instant lastSuccess, String error) {
        this.provider = provider;
        this.connection = connection;
        this.lastAttempt = lastAttempt;
        this.lastSuccess = lastSuccess;
        this.error = error;
    }

    public String getProvider() {
        return this.provider;
    }

    public String getConnection() {
        return this.connection;
    }

    public Instant getLastAttempt() {
        return this.lastAttempt;
    }

    public Instant getLastSuccess() {
        return this.lastSuccess;
    }

    public String getError() {
        return this.error;
    }

    public boolean isConnected() {
        return StringUtils.equals("CONNECTED", this.connection);
    }
}
