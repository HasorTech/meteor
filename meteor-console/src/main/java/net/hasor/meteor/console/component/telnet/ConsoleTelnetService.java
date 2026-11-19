/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import net.hasor.cobble.StringUtils;
import net.hasor.core.Destroy;
import net.hasor.core.Init;
import net.hasor.core.Inject;
import net.hasor.core.Singleton;
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.tconsole.TelConfig;
import net.hasor.tconsole.TelSession;

/** 容器持有的 Telnet 监听，借用公共治理服务，不管理业务实例自身。 */
@Singleton
public final class ConsoleTelnetService implements AutoCloseable {
    private static final String              TOKEN = ConsoleTelnetService.class.getName() + ".token";
    private              ConsoleTelnetServer server;
    @Inject
    private              MeteorConfig        config;
    @Inject
    private              GovernanceService   governance;
    @Inject
    private              ClusterService      clusters;
    @Inject
    private              CommandCredentials  credentials;

    @Init
    public void initialize() throws Exception {
        if (!this.config.isTelnetEnabled()) {
            return;
        }
        InetAddress host = InetAddress.getByName(this.config.getTelnetHost());
        String password = this.config.getTelnetPassword();
        if (!host.isLoopbackAddress() && StringUtils.isBlank(password)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_PASSWORD_REQUIRED);
        }

        this.server = new ConsoleTelnetServer(this.configuration());
        this.server.addCommand("help", new HelpCommand());
        this.server.addCommand("lang", new LanguageCommand());
        this.server.addCommand("status", new StatusCommand(this.config));
        this.server.addCommand("clusters", new ClustersCommand(this.clusters));
        this.server.addCommand("applications", new ApplicationsCommand(this.governance, this.clusters, this.credentials));
        this.server.addCommand("services", new ServicesCommand(this.governance, this.clusters, this.credentials));
        this.server.addCommand("instances", new InstancesCommand(this.governance, this.clusters, this.credentials));
        this.server.addCommand("service", new ServiceCommand(this.governance, this.clusters, this.credentials));
        this.server.addCommand("instance", new InstanceCommand(this.governance, this.clusters, this.credentials));
        this.server.addCommand("rule", new RuleCommand(this.governance, this.clusters, this.credentials));
        this.server.addCommand("meta", new MetaCommand(this.governance, this.clusters, this.credentials));
        try {
            this.server.start(new InetSocketAddress(host, this.config.getTelnetPort()), ignored -> true);
        } catch (Exception | Error failure) {
            this.server.close();
            throw failure;
        }
    }

    private TelConfig configuration() {
        TelConfig tel = new TelConfig();
        tel.setMaxSessions(this.config.getTelnetMaxSessions());
        tel.setMaxPendingBytes(this.config.getTelnetMaxInputBytes());
        tel.setIdleTimeoutMillis(this.config.getTelnetIdleTimeoutMillis());
        tel.setMaxAuthenticationFailures(this.config.getTelnetMaxAuthenticationFailures());
        String password = this.config.getTelnetPassword();
        if (StringUtils.isNotBlank(password)) {
            tel.setAuthenticator(new ConsoleTelnetAuthenticator(password));
        }
        return tel;
    }

    public InetSocketAddress getListenAddress() {
        return this.server == null ? null : (InetSocketAddress) this.server.getListenAddress();
    }

    static String token(TelSession session) {
        String token = (String) session.getAttribute(TOKEN);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(TOKEN, token);
        }
        return token;
    }

    @Override
    @Destroy
    public void close() {
        if (this.server != null) {
            this.server.close();
        }
    }
}
