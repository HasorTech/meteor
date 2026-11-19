/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.List;
import java.util.StringJoiner;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.tconsole.TelCommand;
import net.hasor.tconsole.TelContext;
import net.hasor.tconsole.TelExecutor;

/** Resolves Console help in the language of the current connection. */
final class HelpCommand extends AbstractCommand {
    @Override
    protected ConsoleMessageKey helpKey() {
        return ConsoleMessageKey.TELNET_HELP_HELP;
    }

    @Override
    protected String execute(TelCommand command, List<String> args) {
        TelContext context = command.getSession().getTelContext();
        if (!args.isEmpty()) {
            requireCount(args, 1);
            String name = args.get(0);
            TelExecutor executor = context.findCommand(name);
            if (executor == null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_HELP_COMMAND_NOT_FOUND, name);
            }
            return executor.helpInfo();
        }

        StringJoiner help = new StringJoiner("\r\n");
        for (String name : context.getCommandNames()) {
            TelExecutor executor = context.findCommand(name);
            help.add(" - " + name + "  " + executor.helpInfo().lines().findFirst().orElse(""));
        }
        return help.toString();
    }
}
