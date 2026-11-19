/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.tconsole.TelCommand;
import net.hasor.tconsole.TelExecutor;

/** 仅负责 Console 命令语法和错误输出，不执行治理。 */
public abstract class AbstractCommand implements TelExecutor {
    protected abstract ConsoleMessageKey helpKey();

    @Override
    public final String helpInfo() {
        return ConsoleI18n.getMessage(this.helpKey(), ConsoleI18n.getLocale(Locale.US));
    }

    @Override
    public final String doCommand(TelCommand command) {
        return ConsoleI18n.withLocale(() -> {
            return LanguageCommand.locale(command.getSession());
        }, () -> this.executeCommand(command));
    }

    private String executeCommand(TelCommand command) {
        try {
            return this.execute(command, parseArguments(command));
        } catch (IllegalArgumentException invalid) {
            return "ERROR INVALID_ARGUMENT  " + ConsoleErrorMessages.getMessage(invalid);
        } catch (Exception failure) {
            // 未知的底层异常可能包含配置或脚本中的凭据，只输出类型。
            String details = ConsoleErrorMessages.messageOf(failure) == null ? ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_TELNET_OPERATION_FAILED, failure.getClass().getSimpleName()) : ConsoleErrorMessages.getMessage(failure);
            return "ERROR OPERATION_FAILED  " + details;
        }
    }

    protected abstract String execute(TelCommand command, List<String> args) throws Exception;

    protected static String json(Object result) throws Exception {
        return JsonUtils.writeValueAsString(result);
    }

    /** 解析引号和转义，不执行变量、文件或命令替换。 */
    protected static List<String> parseArguments(TelCommand command) {
        String value = String.join(" ", command.getCommandArgs());
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean started = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                token.append(ch);
                escaped = false;
            } else if (ch == '\\' && quote != '\'') {
                escaped = true;
                started = true;
            } else if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    token.append(ch);
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
                started = true;
            } else if (Character.isWhitespace(ch)) {
                if (started) {
                    result.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(ch);
                started = true;
            }
        }
        if (escaped || quote != 0) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_UNCLOSED_QUOTE);
        }
        if (started) {
            result.add(token.toString());
        }
        return result;
    }

    protected static void requireCount(List<String> args, int count) {
        if (args.size() != count) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_ARGUMENT_COUNT);
        }
    }
}
