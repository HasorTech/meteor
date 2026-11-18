/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global;
import net.hasor.cobble.StringUtils;
import net.hasor.core.InjectSettings;

/** 控制台运行配置，由 Hasor 统一注入。 */
public final class MeteorConfig {
    public static final String  WEB_ENABLED_KEY    = "meteor.web.enabled";
    public static final String  TELNET_ENABLED_KEY = "meteor.telnet.enabled";
    public static final String  RPC_ENABLED_KEY    = "meteor.rpc.enabled";
    // 控制台本地数据
    @InjectSettings(value = "meteor.data.directory", defaultValue = "data")
    private             String  dataDirectory;
    // 命令执行上下文
    @InjectSettings(value = "meteor.commands.context.ttl-millis", defaultValue = "300000")
    private             long    commandContextTtl;
    // 出站管理客户端
    @InjectSettings(value = "meteor.client.unit", defaultValue = "default")
    private             String  unit;
    // Web 入口、认证与会话
    @InjectSettings(value = WEB_ENABLED_KEY, defaultValue = "true")
    private             boolean webEnabled;
    @InjectSettings(value = "meteor.web.auth.enabled", defaultValue = "false")
    private             String  security;
    @InjectSettings(value = "meteor.web.auth.account")
    private             String  securityAccount;
    @InjectSettings(value = "meteor.web.session.secure-cookie", defaultValue = "false")
    private             boolean secureCookie;
    // 注册中心管理 RPC 监听与认证
    @InjectSettings(value = RPC_ENABLED_KEY, defaultValue = "false")
    private             boolean rpcEnabled;
    @InjectSettings(value = "meteor.rpc.unit", defaultValue = "default")
    private             String  rpcUnit;
    @InjectSettings(value = "meteor.rpc.host", defaultValue = "127.0.0.1")
    private             String  rpcHost;
    @InjectSettings(value = "meteor.rpc.port", defaultValue = "19193")
    private             int     rpcPort;
    @InjectSettings(value = "meteor.rpc.token")
    private             String  managementToken;
    // Telnet 监听、认证与会话
    @InjectSettings(value = TELNET_ENABLED_KEY, defaultValue = "false")
    private             boolean telnetEnabled;
    @InjectSettings(value = "meteor.telnet.host", defaultValue = "127.0.0.1")
    private             String  telnetHost;
    @InjectSettings(value = "meteor.telnet.port", defaultValue = "2182")
    private             int     telnetPort;
    @InjectSettings(value = "meteor.telnet.auth.password")
    private             String  telnetPassword;
    @InjectSettings(value = "meteor.telnet.auth.max-failures", defaultValue = "3")
    private             int     telnetMaxAuthenticationFailures;
    @InjectSettings(value = "meteor.telnet.max-sessions", defaultValue = "8")
    private             int     telnetMaxSessions;
    @InjectSettings(value = "meteor.telnet.session.max-input-bytes", defaultValue = "65536")
    private             int     telnetMaxInputBytes;
    @InjectSettings(value = "meteor.telnet.session.idle-timeout-millis", defaultValue = "300000")
    private             long    telnetIdleTimeoutMillis;

    public boolean isWebEnabled() {
        return this.webEnabled;
    }

    public boolean isTelnetEnabled() {
        return this.telnetEnabled;
    }

    public String getTelnetHost() {
        return this.telnetHost;
    }

    public int getTelnetPort() {
        return this.telnetPort;
    }

    public String getTelnetPassword() {
        String password = System.getenv("METEOR_TELNET_PASSWORD");
        return StringUtils.isNotBlank(password) ? password : this.telnetPassword;
    }

    public int getTelnetMaxAuthenticationFailures() {
        return this.telnetMaxAuthenticationFailures;
    }

    public int getTelnetMaxSessions() {
        return this.telnetMaxSessions;
    }

    public int getTelnetMaxInputBytes() {
        return this.telnetMaxInputBytes;
    }

    public long getTelnetIdleTimeoutMillis() {
        return this.telnetIdleTimeoutMillis;
    }

    public String getUnit() {
        return this.unit;
    }

    public String getDataDirectory() {
        return this.dataDirectory;
    }

    public boolean isSecureCookie() {
        return this.secureCookie;
    }

    public long getCommandContextTtl() {
        return this.commandContextTtl;
    }

    public boolean isSecurityEnabled() {
        if (StringUtils.equalsIgnoreCase(this.security, "true")) {
            return true;
        }
        if (StringUtils.equalsIgnoreCase(this.security, "false")) {
            return false;
        }
        throw new IllegalArgumentException("meteor.web.auth.enabled 必须为 true 或 false");
    }

    public String getSecurityAccount() {
        return this.securityAccount;
    }

    public String getManagementToken() {
        String token = System.getenv("METEOR_REGISTRY_MANAGEMENT_TOKEN");
        if (StringUtils.isNotBlank(token)) {
            return token;
        }
        return this.managementToken;
    }

    public boolean isRpcEnabled() {
        return this.rpcEnabled;
    }

    public String getRpcUnit() {
        return this.rpcUnit;
    }

    public int getRpcPort() {
        return this.rpcPort;
    }

    public String getRpcHost() {
        return this.rpcHost;
    }
}
