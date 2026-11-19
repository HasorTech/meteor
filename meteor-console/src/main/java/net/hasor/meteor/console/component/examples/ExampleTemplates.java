/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.examples;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** 模板仅从固定类路径目录读取；替换一次，数据中的占位符不会被再次解释。 */
public final class ExampleTemplates {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}");

    private ExampleTemplates() {
    }

    public static String version() {
        try (InputStream input = ExampleTemplates.class.getResourceAsStream("/META-INF/invocation-examples/version.properties")) {
            if (input == null) {
                throw ErrorMessageException.state(null, ConsoleMessageKey.ERROR_MISSING_EXAMPLE_DEPENDENCY_VERSIONS);
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("version");
        } catch (IOException failure) {
            throw ErrorMessageException.state(failure, ConsoleMessageKey.ERROR_COULD_NOT_LOAD_EXAMPLE_DEPENDENCY_VERSIONS);
        }
    }

    public static String render(String resource, Map<String, String> values) {
        if (!resource.matches("[A-Za-z0-9_./-]+") || resource.contains("..")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_EXAMPLE_TEMPLATE_PATH);
        }
        String path = "/META-INF/invocation-examples/" + resource;
        try (InputStream input = ExampleTemplates.class.getResourceAsStream(path)) {
            if (input == null) {
                throw ErrorMessageException.state(null, ConsoleMessageKey.ERROR_MISSING_EXAMPLE_TEMPLATE, resource);
            }
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = TOKEN.matcher(source);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String value = values.get(matcher.group(1));
                if (value == null) {
                    throw ErrorMessageException.state(null, ConsoleMessageKey.ERROR_MISSING_TEMPLATE_PARAMETER, matcher.group(1));
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(result);
            return result.toString();
        } catch (IOException failure) {
            throw ErrorMessageException.state(failure, ConsoleMessageKey.ERROR_COULD_NOT_READ_THE_EXAMPLE_TEMPLATE, resource);
        }
    }

    public static String codeBlock(String language, String code) {
        // 元数据可能包含反引号；围栏始终比正文中最长的连续反引号多一个。
        int length = 3;
        Matcher matcher = Pattern.compile("`+").matcher(code);
        while (matcher.find()) {
            length = Math.max(length, matcher.end() - matcher.start() + 1);
        }
        String fence = "`".repeat(length);
        return fence + language + "\n" + code.stripTrailing() + "\n" + fence;
    }
}
