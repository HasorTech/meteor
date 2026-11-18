/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole.command;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.connector.ConnectorConfig;
import net.hasor.meteor.domain.ProtocolBinding;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.meta.ProtocolMeta;
import net.hasor.meteor.domain.meta.ServiceMetaParser;
import net.hasor.meteor.domain.warp.MetBindInfoWrap;
import net.hasor.tconsole.TelCommand;

/** 查询本机服务描述，启用或停用服务。 仅供管理器注册，不作为应用 API。 */
public final class ServiceCommand extends AbstractCommand {
    @Override
    public String helpInfo() {
        return "service info|enable|disable <serviceId>";
    }

    @Override
    protected String execute(TelCommand command, List<String> args) throws Exception {
        requireCount(args, 2);
        String action = args.get(0);
        String id = args.get(1);
        MetBindInfo<?> binding = this.getGovernanceManager(command).requireService(id);
        if (StringUtils.equals(action, "info")) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("serviceId", id);
            info.put("role", binding.getServiceType());
            info.put("enabled", this.getService(command).getContext().getUpdater().isServiceEnabled(id));
            info.put("protocols", binding.getBindProtocols());
            List<Map<String, Object>> endpoints = new ArrayList<>();
            Map<String, ProtocolMeta> publications = MetBindInfoWrap.unwrap(binding).getProtocolMetas();
            for (ConnectorConfig connector : this.getService(command).getContext().getSettings().getConnectorConfigs()) {
                if (!connector.bindEnabled()) {
                    continue;
                }

                for (ProtocolConfig protocol : connector.protocols()) {
                    ProtocolMeta publication = publications.get(protocol.name());
                    ProtocolBinding endpoint = publication == null ? null : publication.getEndpoint(connector.name());
                    if (endpoint != null) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("connector", connector.name());
                        data.put("protocol", protocol.name());
                        var listen = this.getService(command).getContext().getListenAddress(connector.name(), protocol.name(), protocol.scheme());
                        data.put("listenAddress", listen == null ? null : listen.toString());
                        data.put("contextPath", endpoint.getContextPath() == null ? null : endpoint.getContextPath().getPaths());
                        data.put("contract", endpoint.getDescription());
                        endpoints.add(data);
                    }
                }
            }

            info.put("endpoints", endpoints);
            info.put("description", ServiceMetaParser.parse(binding.getBindType()).createServiceDescription());
            return this.getService(command).getJson().writerWithDefaultPrettyPrinter().writeValueAsString(info);
        }

        if (!StringUtils.equals(action, "enable") && !StringUtils.equals(action, "disable")) {
            throw new IllegalArgumentException("Expected info, enable or disable");
        }

        return this.getGovernanceManager(command).write(action, id, null, null, null, null);
    }
}
