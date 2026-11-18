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
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.tconsole.TelCommand;
import net.hasor.tconsole.TelExecutor;

/** 仅负责 Console 命令语法和错误输出，不执行治理。 */
public abstract class AbstractCommand implements TelExecutor {
    @Override
    public final String doCommand(TelCommand command) {
        try {
            return this.execute(command, parseArguments(command));
        } catch (IllegalArgumentException invalid) {
            return "ERROR INVALID_ARGUMENT  " + invalid.getMessage();
        } catch (Exception failure) {
            // 不输出配置、脚本或底层异常消息中的凭据。
            return "ERROR OPERATION_FAILED  " + failure.getClass().getSimpleName();
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
            throw new IllegalArgumentException("Unclosed quote or escape");
        }
        if (started) {
            result.add(token.toString());
        }
        return result;
    }

    protected static void requireCount(List<String> args, int count) {
        if (args.size() != count) {
            throw new IllegalArgumentException("Unexpected argument count; use help");
        }
    }
}
