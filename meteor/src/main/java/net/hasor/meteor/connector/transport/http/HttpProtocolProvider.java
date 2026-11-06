/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http;
import java.util.List;
import java.util.Set;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.connector.ProtocolProvider;
import net.hasor.meteor.connector.transport.http.channel.HttpExchange;
import net.hasor.meteor.domain.ProtocolBinding;

/** HTTP protocol SPI; requests select protocol mounts by their URI path. */
public interface HttpProtocolProvider extends ProtocolProvider<HttpExchange> {
    /** 按协议固定顺序返回已准备绑定的路由身份分量；空列表表示不参与名称冲突校验。 */
    List<String> serviceKey(MetBindInfo<?> service, ProtocolBinding binding);

    /** 此协议挂载支持提供的附带接入点类型，不包含服务名称及具体地址。 */
    Set<String> endpointTypes();

    @Override
    default String transportType() {
        return "http";
    }

    @Override
    default Class<HttpExchange> messageType() {
        return HttpExchange.class;
    }
}