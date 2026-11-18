/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.hasor.tconsole.TelAuthenticator;
import net.hasor.tconsole.TelSession;

/** 使用扩展创建时的口令配置验证 Telnet 连接。 */
public final class MetTelnetAuthenticator implements TelAuthenticator {
    private final byte[] expected;

    public MetTelnetAuthenticator(String password) {
        this.expected = password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean authenticate(TelSession session, String password) {
        return MessageDigest.isEqual(this.expected, password.getBytes(StandardCharsets.UTF_8));
    }
}
