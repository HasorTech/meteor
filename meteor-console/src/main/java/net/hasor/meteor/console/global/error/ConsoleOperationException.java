/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.error;

import java.util.Locale;
import java.util.function.UnaryOperator;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages.Keyed;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages.Message;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** Retains the failure for status mapping and exposes its sanitized diagnostic. */
public final class ConsoleOperationException extends RuntimeException implements Keyed {
    private final Message message;

    public ConsoleOperationException(ConsoleMessageKey key, Throwable cause) {
        this(cause, new Message(key), UnaryOperator.identity());
    }

    public ConsoleOperationException(Throwable cause, UnaryOperator<String> redact) {
        this(cause, ConsoleErrorMessages.messageOf(cause), redact);
    }

    public ConsoleOperationException(Throwable cause, Message message, UnaryOperator<String> redact) {
        super(message == null ? redact.apply(ConsoleErrorMessages.diagnostic(cause)) : null, cause);
        this.message = message == null ? null : message.redact(redact);
    }

    @Override
    public Message message() {
        return this.message;
    }

    @Override
    public String getMessage() {
        return this.message == null ? super.getMessage() : this.message.format(Locale.US);
    }
}
