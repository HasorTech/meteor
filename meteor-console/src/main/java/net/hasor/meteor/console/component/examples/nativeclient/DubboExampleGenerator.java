/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.ObjectUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.examples.ExampleConnections;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleTemplates;
import net.hasor.meteor.console.utils.JsonUtils;

/** 使用两个现有互通对端所使用的 Dubbo SDK。 */
public final class DubboExampleGenerator {
    public List<ExampleDocument> generate(ExampleTarget target, String address) {
        if (!StringUtils.equals(target.getEndpoint().getProtocolName(), "dubbo")) {
            return List.of();
        }
        ExampleConnections.validate(target);
        var binding = target.getEndpoint().getDescription();
        var operation = binding.getMethods().get(0);
        var method = target.getDescription().getMethods().stream().filter(item -> ObjectUtils.equals(item.getJavaSignature(), operation.get("javaSignature"))).findFirst().orElseThrow();
        var call = HttpExampleGenerator.call(target.getDescription(), binding, operation, "dubbo");
        JsonNode args = JsonUtils.valueToTree(call.get("args"));
        var parameters = new ArrayList<>(method.getParameters());
        parameters.sort(Comparator.comparingInt(item -> item.getArgIndex()));
        List<String> arguments = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (int index = 0; index < parameters.size(); index++) {
            String type = target.getDescription().getTypes().get(parameters.get(index).getType()).getJavaType();
            types.add(literal(type));
            arguments.add("java.combine(" + literal(type) + ", " + args.get(index) + ")");
        }
        Map<String, String> values = new LinkedHashMap<>();
        URI uri = URI.create(target.getAddress());
        values.put("authority", literal(StringUtils.isBlank(address) ? ExampleConnections.ADDRESS_PLACEHOLDER : uri.getRawAuthority()));
        values.put("address", literal(StringUtils.defaultIfBlank(address, ExampleConnections.ADDRESS_PLACEHOLDER)));
        values.put("service", literal(binding.getName()));
        values.put("group", literal(target.getService().getGroup()));
        values.put("version", literal(target.getService().getVersion()));
        values.put("method", literal(operation.get("name").toString()));
        values.put("arguments", String.join(", ", arguments));
        values.put("types", String.join(", ", types));
        values.put("jsonArguments", literal(args.toString()));
        List<ExampleDocument> documents = new ArrayList<>();
        documents.add(new ExampleDocument("javascript-direct", "javascript", "direct", ExampleTemplates.codeBlock("bash", "npm install dubbo2.js@2.3.9")
                + "\n\n" + ExampleTemplates.codeBlock("javascript", ExampleTemplates.render("dubbo/consumer.cjs.tpl", values))));
        documents.add(new ExampleDocument("go-direct", "go", "direct", ExampleTemplates.codeBlock("bash", "go get dubbo.apache.org/dubbo-go/v3@v3.3.2")
                + "\n\n" + ExampleTemplates.codeBlock("go", ExampleTemplates.render("dubbo/consumer.go.tpl", values))));
        return documents;
    }

    private static String literal(String value) {
        return JsonUtils.valueToTree(value).toString();
    }
}
