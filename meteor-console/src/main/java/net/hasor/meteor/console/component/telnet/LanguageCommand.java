/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.List;
import java.util.Locale;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.tconsole.TelCommand;
import net.hasor.tconsole.TelSession;

/** Reads or changes the language for one authenticated Telnet connection. */
final class LanguageCommand extends AbstractCommand {
    private static final String LANGUAGE = LanguageCommand.class.getName() + ".locale";

    static Locale locale(TelSession session) {
        Object preference = session.getAttribute(LANGUAGE);
        return preference instanceof Locale locale ? locale : Locale.US;
    }

    @Override
    protected ConsoleMessageKey helpKey() {
        return ConsoleMessageKey.TELNET_HELP_LANG;
    }

    @Override
    protected String execute(TelCommand command, List<String> args) {
        TelSession session = command.getSession();
        if (!args.isEmpty()) {
            requireCount(args, 1);
            Locale selection = switch (args.get(0).toLowerCase(Locale.ROOT)) {
                case "en", "en-us" -> Locale.US;
                case "zh", "zh-cn" -> Locale.SIMPLIFIED_CHINESE;
                default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_UNSUPPORTED_LANGUAGE, args.get(0));
            };
            session.setAttribute(LANGUAGE, selection);
        }
        return "OK  " + ConsoleI18n.getMessage(ConsoleMessageKey.TELNET_LANGUAGE, ConsoleI18n.getLocale().toLanguageTag());
    }
}
