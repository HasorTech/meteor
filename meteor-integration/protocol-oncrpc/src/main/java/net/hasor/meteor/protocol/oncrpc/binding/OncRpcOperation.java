/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.oncrpc.binding;
import java.util.List;
import net.hasor.meteor.protocol.oncrpc.codec.XdrCodec;

/** Program coordinates and the prepared XDR codecs for one operation. */
record OncRpcOperation(long program, long version, long procedure, List<XdrCodec<Object>> parameters, List<Integer> order, XdrCodec<Object> result, Class<?> returnType) {
    OncRpcOperation(long program, long version, long procedure, List<XdrCodec<Object>> parameters, List<Integer> order, //
            XdrCodec<Object> result, Class<?> returnType) {
        this.program = program;
        this.version = version;
        this.procedure = procedure;
        this.parameters = List.copyOf(parameters);
        this.order = List.copyOf(order);
        this.result = result;
        this.returnType = returnType;
    }
}