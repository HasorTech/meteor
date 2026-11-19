/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.CommandCredentials;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.service.ClusterService;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.neta.bytebuf.ByteBuf;
import net.hasor.tconsole.TelCommand;

public final class RuleCommand extends AbstractGovernanceCommand {
    public RuleCommand(GovernanceService governance, ClusterService clusters, CommandCredentials credentials) {
        super(governance, clusters, credentials);
    }

    @Override
    protected ConsoleMessageKey helpKey() {
        return ConsoleMessageKey.TELNET_HELP_RULE;
    }

    @Override
    protected Set<String> options() {
        return Set.of("--cluster", "--instance", "--scope", "--multiline");
    }

    @Override
    public int readCommandBodyLength(TelCommand command, ByteBuf buffer, Charset charset) {
        // 先解析合法选项，避免 XML 字符串中的 --multiline 被误认作多行输入。
        try {
            CommandOptions options = new CommandOptions(parseArguments(command), this.options());
            if (options.option("--multiline") == null) {
                return 0;
            }
        } catch (IllegalArgumentException invalid) {
            return 0;
        }
        String body = buffer.getString(buffer.readerIndex(), buffer.readableBytes(), StandardCharsets.UTF_8);
        int start = 0;
        int end;
        while ((end = body.indexOf('\n', start)) >= 0) {
            if (StringUtils.equals(body.substring(start, end).stripTrailing(), ".end")) {
                return body.substring(0, end + 1).getBytes(StandardCharsets.UTF_8).length;
            }
            start = end + 1;
        }
        return -1;
    }

    @Override
    protected String execute(TelCommand command, CommandOptions options, ManagementTarget target) throws Exception {
        List<String> args = new ArrayList<>(options.arguments());
        if (args.isEmpty()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_RULE_OPERATION_REQUIRED);
        }
        String action = args.remove(0);
        if (StringUtils.equals(target.getScope(), "service")) {
            if (args.isEmpty()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_SERVICE_ID_REQUIRED);
            }
            target.setServiceId(args.remove(0));
        }
        CommandOptions.requireScope(target);
        if (args.isEmpty()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_RULE_TYPE_REQUIRED);
        }
        MetRuleType type = switch (args.remove(0)) {
            case "service" -> MetRuleType.SERVICE_ROUTE;
            case "method" -> MetRuleType.METHOD_ROUTE;
            case "args" -> MetRuleType.ARGS_ROUTE;
            case "flow" -> MetRuleType.FLOW_CONTROL;
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_RULE_TYPE);
        };
        boolean multiline = options.option("--multiline") != null;
        if (StringUtils.equals(action, "put") || StringUtils.equals(action, "validate")) {
            requireCount(args, multiline ? 0 : 1);
            String body = multiline ? command.getCommandBody() : args.get(0);
            if (multiline) {
                body = body.substring(0, body.lastIndexOf(".end")).stripTrailing();
            }
            if (StringUtils.equals(action, "validate")) {
                return json(this.governance.validateRule(target, type, body));
            }
            this.governance.updateRule(target, type, body);
        } else {
            requireCount(args, 0);
            if (multiline) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_MULTILINE);
            }
            switch (action) {
                case "show":
                    return json(this.governance.rule(target, type));
                case "clear":
                    this.governance.resetRule(target, type);
                    break;
                case "enable":
                    this.governance.ruleEnabled(target, type, true);
                    break;
                case "disable":
                    this.governance.ruleEnabled(target, type, false);
                    break;
                default:
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_TELNET_INVALID_RULE_OPERATION);
            }
        }
        return "SAVED  " + ConsoleI18n.getMessage(ConsoleMessageKey.TELNET_RULE_SAVED);
    }
}
