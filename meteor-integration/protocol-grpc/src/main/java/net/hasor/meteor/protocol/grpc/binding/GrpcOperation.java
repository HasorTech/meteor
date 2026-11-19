/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc.binding;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;

/** The protobuf and Java message bindings for one wire operation. */
public final class GrpcOperation {
    private final GrpcMethodShape         shape;
    private final Encoder                 encoder;
    private final Decoder                 decoder;
    private final MethodDescriptor        descriptor;
    private final JsonFormat.TypeRegistry registry;

    GrpcOperation(GrpcMethodShape shape, MethodDescriptor descriptor, JsonFormat.TypeRegistry registry, Encoder encoder, Decoder decoder) {
        this.shape = shape;
        this.descriptor = descriptor;
        this.registry = registry;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public GrpcMethodShape shape() {
        return this.shape;
    }

    public DynamicMessage encode(Object value, boolean request) throws Exception {
        return this.encoder.encode(value, request);
    }

    public Object decode(byte[] bytes, boolean request) throws Exception {
        return this.decoder.decode(bytes, request);
    }

    public MethodDescriptor descriptor() {
        return this.descriptor;
    }

    public String json(DynamicMessage message) throws Exception {
        return JsonFormat.printer().usingTypeRegistry(this.registry).omittingInsignificantWhitespace().sortingMapKeys().print(message);
    }

    public DynamicMessage parseJson(String json, boolean request) throws Exception {
        DynamicMessage.Builder result = DynamicMessage.newBuilder(this.message(request));
        GrpcProtoSchema.mergeJson(json, result, this.registry);
        return result.build();
    }

    public DynamicMessage parse(byte[] bytes, boolean request) throws Exception {
        return DynamicMessage.parseFrom(this.message(request), bytes);
    }

    private Descriptor message(boolean request) {
        return request ? this.descriptor.getInputType() : this.descriptor.getOutputType();
    }

    @FunctionalInterface
    interface Encoder {
        DynamicMessage encode(Object value, boolean request) throws Exception;
    }

    @FunctionalInterface
    interface Decoder {
        Object decode(byte[] bytes, boolean request) throws Exception;
    }
}
