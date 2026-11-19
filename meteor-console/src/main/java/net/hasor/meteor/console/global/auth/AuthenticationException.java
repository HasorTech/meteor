/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.auth;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** 登录凭据无效或会话已失效。 */
public final class AuthenticationException extends ErrorMessageException {
    public AuthenticationException(ConsoleMessageKey code) {
        super(code);
    }
}
