/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.thrift.codec;

/** The outbound Thrift method and the names used for endpoint selection and multiplexing. */
final class ThriftCallBinding {
    final ThriftMethodBinding method;
    final String              serviceAlias;
    final String              contractName;
    final Class<?>            returnType;

    ThriftCallBinding(ThriftMethodBinding method, String serviceAlias, String contractName, Class<?> returnType) {
        this.method = method;
        this.serviceAlias = serviceAlias;
        this.contractName = contractName;
        this.returnType = returnType;
    }
}
