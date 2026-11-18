/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.tconsole.MetGovernanceManager;
import net.hasor.neta.bytebuf.ByteBuf;
import net.hasor.tconsole.TelCommand;

/** 读取、校验、保存或清空治理规则，支持多行 XML。 仅供管理器注册，不作为应用 API。 */
public final class RuleCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "rule show|validate|put|clear <serviceId> flow|service|method|args [XML | --multiline]; finish multiline with .end";
    }

    @Override
    public int readCommandBodyLength(TelCommand command, ByteBuf buffer, Charset charset) {
        List<String> args;
        try {
            args = parseArguments(command);
        } catch (IllegalArgumentException invalid) {
            return 0;
        }

        if (args.contains("--multiline")) {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            String body = new String(bytes, StandardCharsets.UTF_8);
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
        return 0;
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        String body = command.getCommandBody();
        if (args.size() < 3 || args.size() > 4) {
            throw new IllegalArgumentException("rule show|validate|put|clear <serviceId> flow|service|method|args [XML|--multiline]");
        }

        String action = args.get(0);
        String id = args.get(1);
        MetGovernanceManager governance = this.getGovernanceManager(command);
        governance.requireService(id);
        String type = args.get(2);
        if (StringUtils.equals(action, "show")) {
            return StringUtils.defaultIfBlank(governance.readRule(id, type), "EMPTY");
        }

        String value = args.size() == 4 ? args.get(3) : null;
        if (StringUtils.equals(value, "--multiline")) {
            int end = body.lastIndexOf(".end");
            value = body.substring(0, end).stripTrailing();
        }
        if (StringUtils.equals(action, "clear")) {
            value = "";
        } else if (value == null) {
            throw new IllegalArgumentException("Rule body is required");
        }

        if (StringUtils.equals(action, "validate")) {
            governance.validateRule(type, value);
            return "OK  valid XML; no changes applied";
        }

        if (!StringUtils.equals(action, "put") && !StringUtils.equals(action, "clear")) {
            throw new IllegalArgumentException("Unknown rule operation");
        }

        return governance.write("rule", id, type, value, null, null);
    }
}
