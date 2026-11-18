/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetExtension;
import net.hasor.meteor.MetExtensionFactory;

/** 显式安装的本机管理监听；配置在 create 时捕获，不随配置对象后续修改。 */
public final class MetTelnetConfiguration implements MetExtensionFactory {
    private String host                      = "127.0.0.1";
    private int    port                      = 2182;
    private String password;
    private int    maxAuthenticationFailures = 3;
    private int    maxSessions               = 8;
    private int    maxInputBytes             = 65536;
    private long   idleTimeoutMillis         = 300000;
    private long   operationTimeoutMillis    = 10000;

    public String getHost() {
        return this.host;
    }

    public MetTelnetConfiguration setHost(String value) {
        this.host = value;
        return this;
    }

    public int getPort() {
        return this.port;
    }

    public MetTelnetConfiguration setPort(int value) {
        this.port = value;
        return this;
    }

    public String getPassword() {
        return this.password;
    }

    public MetTelnetConfiguration setPassword(String value) {
        this.password = value;
        return this;
    }

    public int getMaxAuthenticationFailures() {
        return this.maxAuthenticationFailures;
    }

    /** 允许的口令错误次数；-1 表示不限制。 */
    public MetTelnetConfiguration setMaxAuthenticationFailures(int value) {
        this.maxAuthenticationFailures = value;
        return this;
    }

    public int getMaxSessions() {
        return this.maxSessions;
    }

    public MetTelnetConfiguration setMaxSessions(int value) {
        this.maxSessions = value;
        return this;
    }

    public int getMaxInputBytes() {
        return this.maxInputBytes;
    }

    public MetTelnetConfiguration setMaxInputBytes(int value) {
        this.maxInputBytes = value;
        return this;
    }

    public long getIdleTimeoutMillis() {
        return this.idleTimeoutMillis;
    }

    public MetTelnetConfiguration setIdleTimeoutMillis(long value) {
        this.idleTimeoutMillis = value;
        return this;
    }

    public long getOperationTimeoutMillis() {
        return this.operationTimeoutMillis;
    }

    public MetTelnetConfiguration setOperationTimeoutMillis(long value) {
        this.operationTimeoutMillis = value;
        return this;
    }

    @Override
    public MetExtension create(MetContext context) throws Exception {
        if (context.getExtension(MetTelnetExtension.class) != null) {
            throw new IllegalStateException("Only one tConsole may be installed per Meteor context");
        }

        return new MetTelnetExtension(context, this);
    }
}
