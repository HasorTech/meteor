/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.test.scenario.api;
import net.hasor.meteor.annotation.MetField;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/** 业务 API 的轻量外层，供生成源码从独立 API JAR 解析真实嵌套关系。 */
public final class InvocationNestedApi {
    private InvocationNestedApi() {
    }

    @MetService(id = 0x31234574L, group = "invocation-examples", name = "NestedEcho", version = "1")
    public interface Service {
        @MetMethod(id = 1)
        Message echo(@MetParam(id = 1, name = "value") Message value);
    }

    public static class Message {
        @MetField(id = 1)
        private String value;

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
