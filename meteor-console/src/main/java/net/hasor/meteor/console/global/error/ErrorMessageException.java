/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.error;

import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.NoSuchElementException;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages.Keyed;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages.Message;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** A Console error with a message key and its original arguments. */
public class ErrorMessageException extends IllegalArgumentException implements Keyed {
    private final Message message;

    public ErrorMessageException(ConsoleMessageKey code, Object... arguments) {
        this(null, code, arguments);
    }

    public ErrorMessageException(Throwable cause, ConsoleMessageKey code, Object... arguments) {
        super(null, cause);
        this.message = new Message(code, arguments);
    }

    @Override
    public Message message() {
        return this.message;
    }

    @Override
    public String getMessage() {
        return this.message.format(Locale.US);
    }

    public static NoSuchElementException missing(ConsoleMessageKey key, Object... arguments) {
        return new Missing(new Message(key, arguments));
    }

    public static ConcurrentModificationException conflict(ConsoleMessageKey key, Object... arguments) {
        return new Conflict(new Message(key, arguments));
    }

    public static UnsupportedOperationException unsupported(ConsoleMessageKey key, Object... arguments) {
        return new Unsupported(new Message(key, arguments));
    }

    public static IllegalStateException state(Throwable cause, ConsoleMessageKey key, Object... arguments) {
        return new State(new Message(key, arguments), cause);
    }

    private static final class Missing extends NoSuchElementException implements Keyed {
        private final Message message;

        private Missing(Message message) {
            super(message.format(Locale.US));
            this.message = message;
        }

        @Override
        public Message message() {
            return this.message;
        }
    }

    private static final class Conflict extends ConcurrentModificationException implements Keyed {
        private final Message message;

        private Conflict(Message message) {
            super(message.format(Locale.US));
            this.message = message;
        }

        @Override
        public Message message() {
            return this.message;
        }
    }

    private static final class Unsupported extends UnsupportedOperationException implements Keyed {
        private final Message message;

        private Unsupported(Message message) {
            super(message.format(Locale.US));
            this.message = message;
        }

        @Override
        public Message message() {
            return this.message;
        }
    }

    private static final class State extends IllegalStateException implements Keyed {
        private final Message message;

        private State(Message message, Throwable cause) {
            super(message.format(Locale.US), cause);
            this.message = message;
        }

        @Override
        public Message message() {
            return this.message;
        }
    }
}
