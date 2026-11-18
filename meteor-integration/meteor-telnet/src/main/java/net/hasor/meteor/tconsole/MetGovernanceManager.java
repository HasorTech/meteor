/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import java.util.List;
import java.util.Map;
import net.hasor.meteor.MetBindInfo;

/** Telnet 治理入口，由本地或注册中心实现。 */
public interface MetGovernanceManager {
    Map<String, Object> status();

    List<MetBindInfo<?>> bindings();

    MetBindInfo<?> requireService(String id);

    String readRule(String id, String rule);

    void validateRule(String rule, String body);

    String write(String action, String id, String rule, String body, Map<String, Object> metadata, String key) throws Exception;
}
