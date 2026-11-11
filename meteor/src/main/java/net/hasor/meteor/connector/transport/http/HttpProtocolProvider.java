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
import net.hasor.meteor.domain.ProtocolConfig;

/** HTTP protocol SPI; requests select protocol mounts by their URI path. */
public interface HttpProtocolProvider extends ProtocolProvider<HttpExchange> {
    /** 按协议固定顺序返回已准备绑定的路由身份分量；空列表表示不参与名称冲突校验。 */
    List<String> serviceKey(MetBindInfo<?> service, ProtocolBinding binding);

    /** 此协议挂载支持的全部接入点用途 key，如 endpoint、http、schema；不包含具体地址。 */
    Set<String> endpointTypes();

    /** 此挂载的业务请求支持的 HTTP 主版本；不包含仅用于获取描述文件的版本。 */
    Set<Integer> httpVersions(ProtocolConfig config);

    @Override
    default String transportType() {
        return "http";
    }

    @Override
    default Class<HttpExchange> messageType() {
        return HttpExchange.class;
    }
}
