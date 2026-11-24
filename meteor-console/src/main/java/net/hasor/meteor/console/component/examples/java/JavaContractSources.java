/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.java;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.registry.admin.description.MetFieldDescription;
import net.hasor.meteor.registry.admin.description.MetMethodDescription;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;
import net.hasor.meteor.registry.admin.description.MetTypeDescription;

/** 根据公开的 Java 契约生成接口声明和调用参数。 */
final class JavaContractSources {
    private static final Set<String>           KEYWORDS   = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_", "record",
            "var", "yield", "sealed", "permits");
    private static final Set<String>           PRIMITIVES = Set.of("boolean", "byte", "short", "int", "long", "float", "double", "char", "void");
    private final        MetServiceDescription description;
    private final        String                serviceType;
    private final        boolean               runnableExample;
    private final        Map<String, String>   typeNames  = new LinkedHashMap<>();

    private JavaContractSources(MetServiceDescription description, boolean runnableExample) {
        if (description == null || description.getMethods() == null || description.getMethods().isEmpty()) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_PROVIDER_DOES_NOT_HAVE_A);
        }

        this.description = description;
        this.serviceType = sourceType(description.getJavaType(), description.getExtensions());
        this.runnableExample = runnableExample;
    }

    static JavaContractSources forExample(MetServiceDescription description) {
        return new JavaContractSources(description, true);
    }

    static JavaContractSources forInterface(MetServiceDescription description) {
        return new JavaContractSources(description, false);
    }

    String exampleType() {
        String simpleName = this.serviceType.substring(this.serviceType.lastIndexOf('.') + 1);
        if (Set.of("ExampleMain", "MetConfiguration", "MetService", "MetMessage", "MetMethod", "MetParam",
                "MetReturn", "String", "Throwable").contains(simpleName)) {
            return simpleName + "Api";
        }
        return simpleName;
    }

    String methodSignature() {
        return this.description.getMethods().get(0).getJavaSignature();
    }

    String exampleInterface(String name, boolean preserveErasure) {
        return this.interfaceSource(name, List.of(this.description.getMethods().get(0)), preserveErasure);
    }

    String fullInterface() {
        String name = this.serviceType.substring(this.serviceType.lastIndexOf('.') + 1);
        return this.interfaceSource(name, this.description.getMethods(), false);
    }

    private String interfaceSource(String name, List<MetMethodDescription> methods, boolean preserveErasure) {
        StringBuilder declaration = new StringBuilder();
        if (this.description.isMessage()) {
            declaration.append("@MetMessage\n");
        }
        declaration.append("public interface ").append(name).append(" {\n");
        for (MetMethodDescription method : methods) {
            declaration.append(this.exampleMethod(method, preserveErasure, true));
        }
        return declaration.append("}\n").toString();
    }

    String sdkInterface(String name) {
        return "public interface " + name + " {\n"
                + this.exampleMethod(this.description.getMethods().get(0), true, false) + "}\n";
    }

    private String exampleMethod(MetMethodDescription method, boolean preserveErasure, boolean annotations) {
        StringBuilder source = new StringBuilder();
        if (annotations && method.isMessage() && !this.description.isMessage()) {
            source.append("    @MetMessage\n");
        }
        Long id = method.getMethodId();
        List<String> attributes = annotationAttributes(id, method.getName(), method.getJavaName());
        if (annotations && !attributes.isEmpty()) {
            source.append("    @MetMethod(").append(String.join(", ", attributes)).append(")\n");
        }
        String returnType = this.returnType(method);
        if (annotations && method.getReturns().getName() != null) {
            source.append("    @MetReturn(name = ").append(JavaExampleGenerator.literal(method.getReturns().getName())).append(")\n");
        }
        StringJoiner arguments = new StringJoiner(", ");
        List<MetFieldDescription> parameters = parameters(method);
        List<String> wireTypes = List.of();
        String signature = method.getJavaSignature();
        String prefix = method.getJavaName() + "(";
        if (preserveErasure && signature != null && signature.startsWith(prefix) && signature.endsWith(")") && !parameters.isEmpty()) {
            wireTypes = Arrays.asList(signature.substring(prefix.length(), signature.length() - 1).split(","));
        }
        for (MetFieldDescription parameter : parameters) {
            List<String> values = annotationAttributes(parameter.getFieldId(), parameter.getName(), null);
            String type = this.valueType(parameter.getType());
            int index = parameter.getArgIndex();
            if (index < wireTypes.size()) {
                String wireType = wireTypes.get(index);
                MetTypeDescription resolved = this.description.getTypes().get(parameter.getType());
                if (!StringUtils.equals(wireType, resolved.getJavaType())) {
                    // 继承的泛型参数在发布接口上可能擦除为更宽的类型。
                    type = this.erasedType(wireType);
                }
            }
            String argument = type + " arg" + index;
            if (annotations && !values.isEmpty()) {
                argument = "@MetParam(" + String.join(", ", values) + ") " + argument;
            }
            arguments.add(argument);
        }
        source.append("    ").append(returnType).append(' ').append(identifier(method.getJavaName()))
                .append('(').append(arguments).append(')');
        if (method.getExceptions() != null && !method.getExceptions().isEmpty()) {
            StringJoiner exceptions = new StringJoiner(", ");
            for (String exception : method.getExceptions()) {
                exceptions.add(this.type(exception));
            }
            source.append(" throws ").append(exceptions);
        }
        return source.append(";\n").toString();
    }

    private String erasedType(String binaryName) {
        int array = binaryName.indexOf('[');
        String component = binaryName;
        String suffix = "";
        if (array >= 0) {
            component = binaryName.substring(0, array);
            suffix = binaryName.substring(array);
        }
        for (MetTypeDescription type : this.description.getTypes().values()) {
            if (StringUtils.equals(type.getJavaType(), component)) {
                return sourceType(component, type.getExtensions()) + suffix;
            }
        }
        return sourceType(component, Map.of()) + suffix;
    }

    private static List<String> annotationAttributes(Long id, String name, String defaultName) {
        List<String> attributes = new ArrayList<>();
        if (id != null && id != 0L) {
            String number = id.toString();
            if (id < Integer.MIN_VALUE || id > Integer.MAX_VALUE) {
                number += "L";
            }
            attributes.add("id = " + number);
        }
        if (StringUtils.isNotBlank(name) && !StringUtils.equals(name, defaultName)) {
            attributes.add("name = " + JavaExampleGenerator.literal(name));
        }
        return attributes;
    }

    String invocationStatements(String variable) {
        identifier(variable);
        MetMethodDescription method = this.description.getMethods().get(0);
        String call = variable + "." + identifier(method.getJavaName()) + "(" + this.invocationArguments() + ")";
        if (StringUtils.equals("void", this.returnType(method))) {
            return call + ";";
        }
        return "System.out.println((Object) " + call + ");";
    }

    String invocationArguments() {
        MetMethodDescription method = this.description.getMethods().get(0);
        StringJoiner arguments = new StringJoiner(", ");
        for (MetFieldDescription parameter : parameters(method)) {
            String type = this.type(parameter.getType());
            String sample = this.sample(parameter.getType());
            if (StringUtils.equals("null", sample)) {
                sample = "(" + type + ") " + sample;
            }
            arguments.add(sample);
        }
        return arguments.toString();
    }

    private String returnType(MetMethodDescription method) {
        if (method.getReturns() == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_METHOD_IS_MISSING_ITS_RETURN_TYPE, method.getJavaName());
        }
        return this.type(method.getReturns().getType());
    }

    private String valueType(String reference) {
        String type = this.type(reference);
        if (StringUtils.equals("void", type)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_JAVA_PARAMETERS_AND_PROPERTIES_CANNOT_HAVE);
        }
        return type;
    }

    private String type(String reference) {
        String cached = this.typeNames.get(reference);
        if (cached != null) {
            return cached;
        }
        MetTypeDescription type = this.description.getTypes().get(reference);
        if (type == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_JAVA_CONTRACT_IS_MISSING_A_TYPE, reference);
        }
        if (StringUtils.equals("array", type.getKind())) {
            String result = this.type(type.getElementType()) + "[]";
            this.typeNames.put(reference, result);
            return result;
        }
        String raw = sourceType(type.getJavaType(), type.getExtensions());
        if (!StringUtils.equals("scalar", type.getKind()) && !StringUtils.equals("void", type.getKind()) && !StringUtils.equals("any", type.getKind()) && !StringUtils.equals("object", type.getKind()) && !StringUtils.equals("enum", type.getKind()) && !StringUtils.equals("collection", type.getKind()) && !StringUtils.equals("map", type.getKind())) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_RUNNABLE_EXAMPLE_CANNOT_BE_GENERATED, reference, type.getKind());
        }
        if (!PRIMITIVES.contains(raw)) {
            qualifiedName(raw);
        }
        if (this.runnableExample && raw.startsWith("java.io.")
                && (raw.endsWith("Stream") || raw.endsWith("Reader") || raw.endsWith("Writer"))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_A_STANDALONE_INVOCATION_EXAMPLE_CANNOT_BE, reference);
        }
        List<String> arguments = type.getArguments();
        if (this.runnableExample && arguments != null && !arguments.isEmpty() && !raw.startsWith("java.")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_DESCRIPTION_LACKS_THE_ORIGINAL_DECLARATION, reference);
        }
        // 先缓存原始类型，让 DTO 的自引用和相互引用在后续属性展开中保持有限。
        this.typeNames.put(reference, raw);
        if (arguments != null && !arguments.isEmpty()) {
            StringJoiner parameters = new StringJoiner(", ", raw + "<", ">");
            for (String argument : arguments) {
                String parameter = this.valueType(argument);
                if (PRIMITIVES.contains(parameter)) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_JAVA_GENERIC_ARGUMENTS_CANNOT_BE_PRIMITIVE, reference);
                }
                parameters.add(parameter);
            }
            raw = parameters.toString();
            this.typeNames.put(reference, raw);
        }
        return raw;
    }

    private String sample(String reference) {
        String name = this.type(reference);
        MetTypeDescription type = this.description.getTypes().get(reference);
        switch (name) {
            case "boolean":
            case "java.lang.Boolean":
                return "true";
            case "byte":
            case "java.lang.Byte":
                return "(byte) 1";
            case "short":
            case "java.lang.Short":
                return "(short) 1";
            case "int":
            case "java.lang.Integer":
                return "1";
            case "long":
            case "java.lang.Long":
                return "1L";
            case "float":
            case "java.lang.Float":
                return "1.0F";
            case "double":
            case "java.lang.Double":
                return "1.0D";
            case "char":
            case "java.lang.Character":
                return "'a'";
            case "java.lang.String":
            case "java.lang.CharSequence":
                return "\"example\"";
            case "java.math.BigDecimal":
                return "java.math.BigDecimal.ONE";
            case "java.math.BigInteger":
                return "java.math.BigInteger.ONE";
            case "java.util.UUID":
                return "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000001\")";
            case "java.util.Date":
                return "new java.util.Date(0L)";
            default:
                break;
        }
        if (StringUtils.equals("enum", type.getKind())) {
            if (type.getValues() == null || type.getValues().isEmpty()) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_COULD_NOT_GENERATE_AN_ARGUMENT_FOR, name);
            }
            return name + "." + identifier(type.getValues().get(0).getJavaName());
        }
        if (StringUtils.equals("array", type.getKind())) {
            if (name.indexOf('<') >= 0) {
                return "null";
            }
            int length = 0;
            Object configuredLength = type.getExtensions().get("exampleArrayLength");
            if (configuredLength != null) {
                if (!(configuredLength instanceof Integer arrayLength) || arrayLength < 0) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_SAMPLE_ARRAY_LENGTH, reference);
                }
                length = arrayLength;
            }
            int dimension = name.indexOf('[');
            return "new " + name.substring(0, dimension) + "[" + length + "]" + name.substring(dimension + 2);
        }
        if (StringUtils.equals("object", type.getKind())) {
            if (Boolean.TRUE.equals(type.getExtensions().get("javaDefaultConstructor"))) {
                return "new " + name + "()";
            }
            return "(" + name + ") null /* 按业务构造对象 */";
        }
        String raw = type.getJavaType();
        switch (raw) {
            case "java.util.Collection":
            case "java.util.List":
            case "java.util.ArrayList":
                return this.collectionSample("java.util.ArrayList", type);
            case "java.util.Set":
            case "java.util.HashSet":
                return this.collectionSample("java.util.HashSet", type);
            case "java.util.SortedSet":
            case "java.util.NavigableSet":
            case "java.util.TreeSet":
                return this.collectionSample("java.util.TreeSet", type);
            case "java.util.Map":
            case "java.util.HashMap":
                return this.collectionSample("java.util.HashMap", type);
            case "java.util.SortedMap":
            case "java.util.NavigableMap":
            case "java.util.TreeMap":
                return this.collectionSample("java.util.TreeMap", type);
            case "java.util.LinkedHashMap":
                return this.collectionSample("java.util.LinkedHashMap", type);
            case "java.util.Queue":
            case "java.util.Deque":
            case "java.util.LinkedList":
                return this.collectionSample("java.util.LinkedList", type);
            case "java.util.Optional":
                return "java.util.Optional.empty()";
            default:
                return "null";
        }
    }

    private String collectionSample(String concrete, MetTypeDescription type) {
        StringJoiner arguments = new StringJoiner(", ", "<", ">");
        for (String argument : type.getArguments()) {
            arguments.add(this.type(argument));
        }
        if (type.getArguments().isEmpty()) {
            return "new " + concrete + "()";
        }
        return "new " + concrete + arguments + "()";
    }

    private static List<MetFieldDescription> parameters(MetMethodDescription method) {
        List<MetFieldDescription> parameters = method.getParameters();
        if (parameters == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_METHOD_IS_MISSING_PARAMETER_DECLARATIONS, method.getJavaName());
        }
        for (int index = 0; index < parameters.size(); index++) {
            Integer position = parameters.get(index).getArgIndex();
            if (position == null || position != index) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_JAVA_PARAMETER_POSITIONS_ARE_MISSING_OR, method.getJavaName());
            }
        }
        return parameters;
    }

    private static void qualifiedName(String name) {
        if (name == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_JAVA_TYPE_NAME);
        }
        for (String part : name.split("\\.", -1)) {
            identifier(part);
        }
    }

    private static String sourceType(String binaryName, Map<String, Object> extensions) {
        Object configured = extensions.get("javaSourceType");
        String sourceName = binaryName;
        if (configured instanceof String value && StringUtils.isNotBlank(value)) {
            sourceName = value;
        } else if (binaryName != null && binaryName.indexOf('$') >= 0) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CONTRACT_LACKS_A_JAVA_SOURCE, binaryName);
        }
        if (sourceName == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_MISSING_JAVA_TYPE_NAME);
        }
        if (!PRIMITIVES.contains(sourceName)) {
            qualifiedName(sourceName);
        }
        return sourceName;
    }

    private static String identifier(String name) {
        if (name == null || name.isEmpty() || KEYWORDS.contains(name) || !Character.isJavaIdentifierStart(name.charAt(0))) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_JAVA_IDENTIFIER, name);
        }
        for (int index = 1; index < name.length(); index++) {
            if (!Character.isJavaIdentifierPart(name.charAt(index))) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_JAVA_IDENTIFIER, name);
            }
        }
        return name;
    }

}
