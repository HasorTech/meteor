/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.protocols;

/** 参数类型的展示与输入提示；不改变协议本身的类型支持及编解码规则。 */
public record ConsoleParameterType(String type, String component, String defaultValue) {

    /** input、textarea 或 json；控件由控制台实现。 */
    @Override
    public String component() {
        return this.component;
    }

    /** 输入框文本；null 表示根据服务契约构造结构示例。 */
    @Override
    public String defaultValue() {
        return this.defaultValue;
    }

}
