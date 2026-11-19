/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.soap.codec;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.connector.ProtocolOperationBinder.Target;
import net.hasor.meteor.protocol.soap.definition.SoapDefinitionResource;
import net.hasor.meteor.protocol.soap.definition.SoapOperation;
import net.hasor.meteor.protocol.soap.definition.SoapPublishedResource;

public final class SoapCallBinding {
    final         SoapDefinitionResource       resource;
    final         SoapOperation                operation;
    final         String                       path;
    final         boolean                      get;
    final         Type[]                       inputTypes;
    final         Function<Object[], Object[]> parameters;
    final         Type[]                       outputTypes;
    final         Function<Object[], Object>   result;
    private final SoapMessageCodec             codec;

    SoapCallBinding(SoapDefinitionResource resource, SoapOperation operation, String path, boolean get, //
            Type[] inputTypes, Function<Object[], Object[]> parameters, Type[] outputTypes, //
            Function<Object[], Object> result, SoapMessageCodec codec) {
        this.resource = resource;
        this.operation = operation;
        this.path = path;
        this.get = get;
        this.inputTypes = inputTypes;
        this.parameters = parameters;
        this.outputTypes = outputTypes;
        this.result = result;
        this.codec = codec;
    }

    public SoapMessageCodec codec(SoapMessageCodec fallback) {
        return this.codec == null ? fallback.forVersion(this.resource.definition().version()) : this.codec;
    }

    public static SoapCallBinding published(JsonNode description, Target bound) {
        JsonNode method = null;
        for (JsonNode item : description.path("methods")) {
            if (StringUtils.equals(bound.methodName(), item.path("name").asText())) {
                method = item;
                break;
            }
        }
        if (method == null) {
            throw new IllegalArgumentException("No published WSDL operation: " + bound.methodName());
        }
        String style = description.path("parameterStyle").asText("auto");
        SoapPublishedResource resource = new SoapPublishedResource(description.path("documents"), style);
        SoapOperation operation = resource.definition().operation(bound.methodName());
        if (operation == null) {
            throw new IllegalArgumentException("WSDL does not contain operation: " + bound.methodName());
        }
        SoapLiteralType.Compiler types = new SoapLiteralType.Compiler(description.path("types"));
        Type[] input = new Type[operation.inputBody().parts().size()];
        int[] order = new int[input.length];
        for (int index = 0; index < input.length; index++) {
            JsonNode part = method.path("parameters").path(index);
            input[index] = types.compile(part.path("bindingType").asText(), operation.inputBody().encoded());
            order[index] = part.path("argIndex").asInt(-1);
            if (order[index] < 0 || order[index] >= input.length) {
                throw new IllegalArgumentException("Invalid SOAP parameter index: " + order[index]);
            }
        }
        Type[] output = new Type[operation.outputBody().parts().size()];
        for (int index = 0; index < output.length; index++) {
            JsonNode part = method.path("returns").path(index);
            output[index] = types.compile(part.path("bindingType").asText(), operation.outputBody().encoded());
        }
        String base = bound.requestPath();
        String path = description.path("requestPaths").path(base).asText(base);
        if (!path.startsWith("/") || path.startsWith("//") || !StringUtils.equals(path, URI.create(path).getRawPath())) {
            throw new IllegalArgumentException("Invalid published SOAP mount path");
        }
        return new SoapCallBinding(resource, operation, path, method.path("get").asBoolean(), input, args -> {
            Object[] ordered = new Object[input.length];
            for (int index = 0; index < ordered.length; index++) {
                ordered[index] = args[order[index]];
            }
            return ordered;
        }, output, values -> join(operation, values), null);
    }

    private static Object join(SoapOperation operation, Object[] values) {
        if (values.length <= 1) {
            return values.length == 0 ? null : values[0];
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index++) {
            result.put(operation.outputBody().parts().get(index).name(), values[index]);
        }
        return result;
    }
}
