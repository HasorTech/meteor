/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.oncrpc.codec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.UnaryOperator;

/** 一个 XDR 成员的名称、Java 访问方式及已编译的值映射。 */
public final class XdrPropertyBinding {
    public String                name;
    public Field                 field;
    public Method                getter;
    public Method                setter;
    public XdrCodec<Object>      codec;
    public UnaryOperator<Object> valueBinding = UnaryOperator.identity();

    public XdrPropertyBinding() {
    }

    Object get(Object owner) throws ReflectiveOperationException {
        if (owner instanceof Map<?, ?> fields) {
            return this.valueBinding.apply(fields.get(this.name));
        }
        return this.getter == null ? this.field.get(owner) : this.getter.invoke(owner);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    void set(Object owner, Object value) throws ReflectiveOperationException {
        if (owner instanceof Map fields) {
            fields.put(this.name, value);
        } else if (this.setter != null) {
            this.setter.invoke(owner, value);
        } else {
            this.field.set(owner, value);
        }
    }
}
