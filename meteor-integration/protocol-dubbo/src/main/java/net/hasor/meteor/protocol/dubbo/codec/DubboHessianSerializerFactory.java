/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.dubbo.codec;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import com.caucho.hessian.io.*;
import net.hasor.meteor.serialize.StructuredValue;

/** 使用容器编码，避免 Java 17 不可变集合的内部反射。 */
final class DubboHessianSerializerFactory extends SerializerFactory {
    private static final Serializer STRUCT = new AbstractSerializer() {
        @Override
        public void writeObject(Object value, AbstractHessianOutput output) throws IOException {
            if (output.addRef(value)) {
                return;
            }

            StructuredValue object = (StructuredValue) value;
            output.writeMapBegin(object.typeName());
            for (var field : object.fields().entrySet()) {
                output.writeString(field.getKey());
                output.writeObject(field.getValue());
            }
            output.writeMapEnd();
        }
    };

    private final CollectionSerializer collections = new CollectionSerializer();
    private final MapSerializer        maps        = new MapSerializer();

    DubboHessianSerializerFactory(ClassLoader loader) {
        super(loader);
        this.setAllowNonSerializable(true);
    }

    @Override
    public Serializer getSerializer(Class type) throws HessianProtocolException {
        if (StructuredValue.class.isAssignableFrom(type)) {
            return STRUCT;
        }
        if (Collection.class.isAssignableFrom(type)) {
            return this.collections;
        }
        if (Map.class.isAssignableFrom(type)) {
            return this.maps;
        }
        return super.getSerializer(type);
    }
}
