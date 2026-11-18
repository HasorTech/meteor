/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import net.hasor.neta.channel.NetConfig;
import net.hasor.tconsole.TelConfig;
import net.hasor.tconsole.launcher.telnet.SocketTelService;

/** Console 专用命令提示符，监听和会话仍由 tconsole 实现。 */
public final class ConsoleTelnetServer extends SocketTelService {
    public ConsoleTelnetServer(TelConfig config) {
        super(new NetConfig(), config);
    }

    @Override
    public String getPrompt() {
        return "meteor>";
    }
}
