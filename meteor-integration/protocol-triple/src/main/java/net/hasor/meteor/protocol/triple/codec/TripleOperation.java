/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.triple.codec;
import net.hasor.meteor.protocol.grpc.binding.GrpcOperation;

/** Prepared method shape and optional native protobuf message binding. */
record TripleOperation(TripleMethodShape shape, GrpcOperation protobuf, String returnWireType) {
}
