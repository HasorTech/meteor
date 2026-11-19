/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.i18n;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.hasor.cobble.i18n.I18nUtils;
import net.hasor.web.Invoker;
import net.hasor.web.invoker.HttpParameters;

/** Console dictionary and request language, backed by Cobble and Hasor. */
public final class ConsoleI18n {
    public static final  Locale                        DEFAULT_LOCALE    = Locale.SIMPLIFIED_CHINESE;
    private static final List<Locale>                  SUPPORTED_LOCALES = List.of(DEFAULT_LOCALE, Locale.US);
    private static final ThreadLocal<Supplier<Locale>> LOCALE_SOURCE     = new ThreadLocal<>();
    private static final I18nUtils                     I18N              = loadMessages();

    private ConsoleI18n() {
    }

    public static Locale getLocale() {
        return getLocale(DEFAULT_LOCALE);
    }

    public static Locale getLocale(Locale fallback) {
        Supplier<Locale> source = LOCALE_SOURCE.get();
        if (source != null) {
            return source.get();
        }
        Invoker invoker = HttpParameters.localInvoker();
        return invoker == null ? fallback : resolveLocale(invoker.getHttpRequest().getHeader("Accept-Language"));
    }

    /** Uses the connection's current language for the duration of a synchronous command. */
    public static <T> T withLocale(Supplier<Locale> source, Supplier<T> command) {
        Supplier<Locale> previous = LOCALE_SOURCE.get();
        LOCALE_SOURCE.set(source);
        try {
            return command.get();
        } finally {
            if (previous == null) {
                LOCALE_SOURCE.remove();
            } else {
                LOCALE_SOURCE.set(previous);
            }
        }
    }

    public static Locale resolveLocale(String acceptLanguage) {
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            try {
                for (Locale.LanguageRange preferred : Locale.LanguageRange.parse(acceptLanguage)) {
                    if (preferred.getWeight() == 0) {
                        continue;
                    }

                    String language = Locale.forLanguageTag(preferred.getRange()).getLanguage();
                    for (Locale supported : SUPPORTED_LOCALES) {
                        if (supported.getLanguage().equals(language)) {
                            return supported;
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed header does not prevent loading the console.
            }
        }
        return DEFAULT_LOCALE;
    }

    public static String getMessage(Enum<?> key, Object... args) {
        return getMessage(key.name(), getLocale(), args);
    }

    public static String getMessage(Enum<?> key, Locale locale, Object... args) {
        return getMessage(key.name(), locale, args);
    }

    public static String getMessage(String key, Locale locale, Object... args) {
        return I18N.getMessage(key, args, locale);
    }

    private static I18nUtils loadMessages() {
        try {
            I18nUtils messages = I18nUtils.initI18n();
            messages.setDefaultI18nKey(DEFAULT_LOCALE);
            messages.loadResources(ConsoleMessageKey.class);
            for (ConsoleMessageKey key : ConsoleMessageKey.values()) {
                for (Locale locale : SUPPORTED_LOCALES) {
                    if (key.name().equals(messages.getMessage(key.name(), null, locale))) {
                        throw new IllegalStateException("Missing Console message " + key + " for " + locale.toLanguageTag());
                    }
                }
            }
            return messages;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load Console messages", failure);
        }
    }
}
