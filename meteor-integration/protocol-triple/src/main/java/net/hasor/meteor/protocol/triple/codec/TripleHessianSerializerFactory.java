/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.triple.codec;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import com.caucho.hessian.io.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.serialize.StructuredValue;

/** 集合按容器协议编码，避免原生工厂反射 JDK 不可变集合的 writeReplace。 */
final class TripleHessianSerializerFactory extends SerializerFactory {
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

    TripleHessianSerializerFactory(ClassLoader classLoader) {
        super(classLoader);
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

    @Override
    public Deserializer getDeserializer(String type) throws HessianProtocolException {
        // Dubbo 的 HTTP 期限异常属于协议状态，客户端无需安装 Dubbo 才能识别它。
        String timeout = "org.apache.dubbo.remoting.http12.exception.HttpRequestTimeout";
        if (StringUtils.equals(type, timeout)) {
            return super.getDeserializer(TimeoutException.class);
        }
        return super.getDeserializer(type);
    }
}
