/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import net.hasor.meteor.connector.ProtocolOperationBinder.Target;
import net.hasor.meteor.domain.payload.RequestPayload;

/** Outbound target and prepared protocol binding owned by the RPC caller. */
final class ProtocolOperation implements Target {
    private final String   serviceName;
    private final String   methodName;
    private final String   requestPath;
    private final Class<?> returnType;
    private final String   declaredReturnType;
    private       String   protocolPath;
    private       Object   protocolBinding;

    ProtocolOperation(String serviceName, String methodName, String requestPath, Class<?> returnType, String declaredReturnType) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.requestPath = requestPath;
        this.returnType = returnType;
        this.declaredReturnType = declaredReturnType;
        this.protocolPath = requestPath;
    }

    void bind(String path, Object value) {
        if (path != null) {
            this.protocolPath = path;
        }
        this.protocolBinding = value;
    }

    void apply(RequestPayload request) {
        request.setProtocolBinding(this, this.protocolPath, this.protocolBinding);
    }

    @Override
    public String serviceName() {
        return this.serviceName;
    }

    @Override
    public String methodName() {
        return this.methodName;
    }

    @Override
    public String requestPath() {
        return this.requestPath;
    }

    @Override
    public Class<?> returnType() {
        return this.returnType;
    }

    @Override
    public String declaredReturnType() {
        return this.declaredReturnType;
    }
}
