/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc.binding;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.concurrent.Flow;
import com.fasterxml.jackson.databind.JavaType;

/** 一个 Java 方法对应一条请求消息序列和一条响应消息序列。 */
public final class GrpcMethodShape {
    private final Method   method;
    private final Class<?> requestType;
    private final Class<?> responseType;
    private final boolean  streamingRequest;
    private final boolean  streamingResponse;

    private GrpcMethodShape(Method method, Class<?> requestType, Class<?> responseType, boolean streamingRequest, boolean streamingResponse) {
        this.method = method;
        this.requestType = requestType;
        this.responseType = responseType;
        this.streamingRequest = streamingRequest;
        this.streamingResponse = streamingResponse;
    }

    public static GrpcMethodShape fromMethod(Method method, Class<?> service) {
        if (method.getParameterCount() != 1 || method.getReturnType() == void.class) {
            throw new IllegalArgumentException("gRPC requires one request and one response type: " + method);
        }
        Class<?> request = messageType(GrpcValueMapping.messageType(service, method, true));
        Class<?> response = messageType(GrpcValueMapping.messageType(service, method, false));
        return new GrpcMethodShape(method, request, response, method.getParameterTypes()[0] == Flow.Publisher.class, method.getReturnType() == Flow.Publisher.class);
    }

    static GrpcMethodShape unary(Class<?> requestType, Class<?> responseType) {
        return new GrpcMethodShape(null, requestType, responseType, false, false);
    }

    public Method method() {
        return this.method;
    }

    public Class<?> requestType() {
        return this.requestType;
    }

    public Class<?> responseType() {
        return this.responseType;
    }

    public boolean streamingRequest() {
        return this.streamingRequest;
    }

    public boolean streamingResponse() {
        return this.streamingResponse;
    }

    public boolean streaming() {
        return this.streamingRequest() || this.streamingResponse();
    }

    /** 判断 Java 方法能否表示为 gRPC 请求和响应，不创建调用或协议状态。 */
    public static boolean supports(Method method, Class<?> service) {
        try {
            fromMethod(method, service);
            return true;
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    private static Class<?> messageType(JavaType type) {
        Class<?> value = type.getRawClass();
        boolean publisher = Flow.Publisher.class.isAssignableFrom(value);
        boolean input = InputStream.class.isAssignableFrom(value) || Reader.class.isAssignableFrom(value);
        boolean output = OutputStream.class.isAssignableFrom(value) || Writer.class.isAssignableFrom(value);
        if (publisher || input || output || value == Object.class) {
            throw new IllegalArgumentException("gRPC requires a concrete message or Flow.Publisher<T>: " + type);
        }
        return value;
    }
}
