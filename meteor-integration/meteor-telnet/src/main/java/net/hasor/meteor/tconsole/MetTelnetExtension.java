/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetExtension;
import net.hasor.meteor.tconsole.command.*;
import net.hasor.neta.channel.NetConfig;
import net.hasor.tconsole.TelConfig;

/** 上下文持有的 Telnet 管理器，负责管理监听和命令组装。 */
public final class MetTelnetExtension implements MetExtension {
    private final InetSocketAddress address;
    private final MetTelnetService  server;

    MetTelnetExtension(MetContext context, MetTelnetConfiguration configuration) throws Exception {
        InetAddress host = InetAddress.getByName(configuration.getHost());
        int port = configuration.getPort();
        String password = configuration.getPassword();
        int maxSessions = configuration.getMaxSessions();
        int maxInputBytes = configuration.getMaxInputBytes();
        long idleTimeoutMillis = configuration.getIdleTimeoutMillis();
        long timeoutMillis = configuration.getOperationTimeoutMillis();
        if (port < 0 || port > 65535 || maxSessions < 1 || maxInputBytes < 128 || idleTimeoutMillis < 1 || timeoutMillis < 1) {
            throw new IllegalArgumentException("Invalid tConsole limits or port");
        }
        if (!host.isLoopbackAddress() && (password == null || password.isBlank())) {
            throw new IllegalArgumentException("Non-loopback tConsole requires a password");
        }

        this.address = new InetSocketAddress(host, port);
        NetConfig network = new NetConfig();
        network.setIoThreads(2);
        network.setTaskThreads(2);
        TelConfig tel = new TelConfig();
        tel.setMaxSessions(maxSessions);
        tel.setMaxPendingBytes(maxInputBytes);
        tel.setIdleTimeoutMillis(idleTimeoutMillis);
        tel.setMaxAuthenticationFailures(configuration.getMaxAuthenticationFailures());
        if (password != null && !password.isBlank()) {
            tel.setAuthenticator(new MetTelnetAuthenticator(password));
        }
        this.server = new MetTelnetService(network, tel, context, timeoutMillis);

        // 命令不保存会话状态，可由 tconsole 在不同连接间复用。
        this.server.addCommand("status", new StatusCommand());
        this.server.addCommand("services", new ServicesCommand());
        this.server.addCommand("service", new ServiceCommand());
        this.server.addCommand("instance", new InstanceCommand());
        this.server.addCommand("addresses", new AddressesCommand());
        this.server.addCommand("rule", new RuleCommand());
        this.server.addCommand("meta", new MetaCommand());
    }

    public InetSocketAddress getListenAddress() {
        return (InetSocketAddress) this.server.getListenAddress();
    }

    @Override
    public <T> T getService(Class<T> type) {
        return type == MetTelnetExtension.class ? type.cast(this) : null;
    }

    @Override
    public void beforeStart() throws Exception {
        this.server.beforeStart();
    }

    @Override
    public void afterStart() throws Exception {
        this.server.start(this.address, ignored -> true);
    }

    @Override
    public void close() {
        this.server.close();
    }
}
