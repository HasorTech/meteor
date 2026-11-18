/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.hasor.tconsole.TelAuthenticator;
import net.hasor.tconsole.TelSession;

/** Console Telnet 口令认证，与 Web 登录和注册中心凭据分开。 */
public final class ConsoleTelnetAuthenticator implements TelAuthenticator {
    private final byte[] expected;

    public ConsoleTelnetAuthenticator(String password) {
        this.expected = password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean authenticate(TelSession session, String password) {
        return MessageDigest.isEqual(this.expected, password.getBytes(StandardCharsets.UTF_8));
    }
}
