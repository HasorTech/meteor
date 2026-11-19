/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.error;
import java.io.Serializable;
import java.util.Locale;
import java.util.function.UnaryOperator;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** Resolves Console message keys and displays the diagnostics of other failures. */
public final class ConsoleErrorMessages {
    private ConsoleErrorMessages() {
    }

    public static String getMessage(Throwable failure) {
        return getMessage(failure, ConsoleI18n.getLocale());
    }

    public static String getMessage(Throwable failure, Locale locale) {
        Message message = messageOf(failure);
        return message != null ? message.format(locale) : diagnostic(failure);
    }

    public static Message messageOf(Throwable failure) {
        return failure instanceof Keyed keyed ? keyed.message() : null;
    }

    interface Keyed {
        Message message();
    }

    static String diagnostic(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public static final class Message implements Serializable {
        private static final long              serialVersionUID = 1L;
        private final        ConsoleMessageKey key;
        private final        Object[]          arguments;

        public Message(ConsoleMessageKey key, Object... arguments) {
            this.key = key;
            this.arguments = arguments.clone();
        }

        public ConsoleMessageKey key() {
            return this.key;
        }

        public String[] arguments(Locale locale) {
            String[] result = new String[this.arguments.length];
            for (int i = 0; i < this.arguments.length; i++) {
                Object argument = this.arguments[i];
                if (argument instanceof Message nested) {
                    result[i] = nested.format(locale);
                } else if (argument instanceof Throwable failure) {
                    result[i] = getMessage(failure, locale);
                } else {
                    result[i] = String.valueOf(argument);
                }
            }
            return result;
        }

        public String format(Locale locale) {
            return ConsoleI18n.getMessage(this.key, locale, (Object[]) this.arguments(locale));
        }

        public Message redact(UnaryOperator<String> redact) {
            Object[] sanitized = new Object[this.arguments.length];
            for (int i = 0; i < this.arguments.length; i++) {
                Object argument = this.arguments[i];
                if (argument instanceof Throwable failure) {
                    Message nested = messageOf(failure);
                    sanitized[i] = nested != null ? nested.redact(redact) : redact.apply(diagnostic(failure));
                } else if (argument instanceof Message nested) {
                    sanitized[i] = nested.redact(redact);
                } else {
                    sanitized[i] = redact.apply(String.valueOf(argument));
                }
            }
            return new Message(this.key, sanitized);
        }
    }
}
