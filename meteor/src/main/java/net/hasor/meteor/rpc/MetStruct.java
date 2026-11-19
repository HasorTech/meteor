/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import java.util.*;
import net.hasor.meteor.serialize.StructuredValue;

/** Named aggregate value of a published contract, independent of a local Java class. */
final class MetStruct extends AbstractMap<String, Object> implements StructuredValue {
    private final String              typeName;
    private final Map<String, Object> fields;

    MetStruct(String typeName, Map<String, Object> fields) {
        this.typeName = typeName;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    @Override
    public String typeName() {
        return this.typeName;
    }

    @Override
    public Map<String, Object> fields() {
        return this.fields;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return this.fields.entrySet();
    }
}
