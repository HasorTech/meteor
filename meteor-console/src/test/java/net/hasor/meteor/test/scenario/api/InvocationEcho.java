/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.scenario.api;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/** 示例调用的标量契约，用真实返回值验证生成客户端跨 JVM 调用。 */
@MetService(id = 0x31234573L, group = "invocation-examples", name = "InvocationEcho", version = "1")
public interface InvocationEcho {
    @MetMethod(id = 1)
    String echo(@MetParam(id = 1, name = "value") String value);
}
