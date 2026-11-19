/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.web;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.core.Inject;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.http.ConsoleRequest;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.service.GovernanceService;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.web.Invoker;
import net.hasor.web.annotation.Get;
import net.hasor.web.annotation.HttpMethod;
import net.hasor.web.annotation.MappingTo;
import net.hasor.web.annotation.Put;

/** 元信息键值查询与原子合并、删除。 */
@MappingTo("/api")
public class MetadataController {
    @Inject
    private GovernanceService governance;

    @Get
    @MappingTo("/metadata")
    public Object fetch(Invoker invoker) {
        return this.governance.fetchMeta(ConsoleRequest.from(invoker).target()).values();
    }

    @Put
    @MappingTo("/metadata")
    public Object put(Invoker invoker) {
        ConsoleRequest request = ConsoleRequest.from(invoker);
        JsonNode values = request.body().get("values");
        if (values == null || !values.isObject()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_VALUES_MUST_BE_A_JSON_OBJECT);
        }

        Map<String, Object> metadata = JsonUtils.convertValue(values, new TypeReference<>() {
        });

        return this.governance.putMeta(request.target(), metadata);
    }

    @HttpMethod("DELETE")
    @MappingTo("/metadata")
    public Object remove(Invoker invoker) {
        ConsoleRequest request = ConsoleRequest.from(invoker);
        JsonNode keys = request.body().get("keys");
        if (keys == null || !keys.isArray()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_KEYS_MUST_BE_AN_ARRAY_OF);
        }

        for (JsonNode key : keys) {
            if (!key.isTextual()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_METADATA_KEY_MUST_BE_A);
            }
        }

        return this.governance.removeMeta(request.target(), JsonUtils.convertValue(keys, String[].class));
    }
}
