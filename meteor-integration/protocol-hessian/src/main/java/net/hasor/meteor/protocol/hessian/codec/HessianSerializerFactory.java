/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.hessian.codec;
import java.io.IOException;
import java.util.Collection;
import com.caucho.burlap.io.BurlapRemoteObject;
import com.caucho.hessian.io.*;
import net.hasor.meteor.serialize.StructuredValue;

/** 保留原生对象图编码，阻止可调用远程对象进入 RPC 参数与返回值。 */
final class HessianSerializerFactory extends SerializerFactory {
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

    private static final Serializer COLLECTION = new AbstractSerializer() {
        @Override
        public void writeObject(Object value, AbstractHessianOutput output) throws IOException {
            if (output.addRef(value)) {
                return;
            }

            Collection<?> collection = (Collection<?>) value;
            boolean end = output.writeListBegin(collection.size(), null);
            for (Object item : collection) {
                output.writeObject(item);
            }
            if (end) {
                output.writeListEnd();
            }
        }
    };

    HessianSerializerFactory(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public Serializer getSerializer(Class type) throws HessianProtocolException {
        if (StructuredValue.class.isAssignableFrom(type)) {
            return STRUCT;
        }
        if (Collection.class.isAssignableFrom(type) && type.getName().startsWith("java.util.ImmutableCollections$")) {
            return COLLECTION;
        }
        rejectRemote(type);
        return super.getSerializer(type);
    }

    @Override
    public Deserializer getDeserializer(Class type) throws HessianProtocolException {
        rejectRemote(type);
        return super.getDeserializer(type);
    }

    @Override
    public Deserializer getDeserializer(String type) throws HessianProtocolException {
        // 原生工厂递归解析数组类型；先限制维数，避免短报文触发深层递归。
        if (type != null) {
            int dimensions = 0;
            while (dimensions < type.length() && type.charAt(dimensions) == '[') {
                dimensions++;
            }
            if (dimensions > 64) {
                throw new HessianProtocolException("Hessian array type exceeds maximum depth");
            }
        }

        // 在原生工厂的容错分支之前检查，避免被拒绝的类型退化成普通 Map。
        if (type != null && !type.isEmpty() && !type.startsWith("[")) {
            try {
                rejectRemote(this.loadSerializedClass(type));
            } catch (ClassNotFoundException ignored) {
                // 原生工厂继续处理内置短类型名及接收端没有定义的普通业务类型。
            }
        }
        return super.getDeserializer(type);
    }

    private static void rejectRemote(Class<?> type) throws HessianProtocolException {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        boolean hessianRemote = HessianRemote.class.isAssignableFrom(type);
        boolean remoteObject = HessianRemoteObject.class.isAssignableFrom(type);
        boolean burlapRemote = BurlapRemoteObject.class.isAssignableFrom(type);
        if (hessianRemote || remoteObject || burlapRemote) {
            throw new HessianProtocolException("Callable remote objects are not supported");
        }
    }
}
