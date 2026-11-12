/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.test.component.protocols;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import net.hasor.meteor.console.component.protocols.ConsoleTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConsoleTypesTest {
    @Test
    public void protocolResourcesSupplyEditorHints() throws Exception {
        ConsoleTypes configuration = new ConsoleTypes();
        configuration.init();
        for (String protocol : new String[] { "rsf", "connect", "dubbo", "grpc", "grpc-web", "hessian", "hprose", "jsonrpc", "oncrpc", "oncrpc-udp", "soap", "thrift", "thrift-http", "triple", "xmlrpc" }) {
            var types = configuration.types(protocol);
            assertNotNull(types.get("java.lang.String"), protocol + ": " + types);
            assertEquals("textarea", types.get("java.lang.String").component(), protocol);
            assertEquals("", types.get("java.lang.String").defaultValue());
            assertEquals("json", types.get("@object").component());
            assertNull(types.get("@object").defaultValue());
            assertEquals("0", types.get("long").defaultValue());
            assertEquals("false", types.get("boolean").defaultValue());
            assertEquals("Time", types.get("java.time.LocalTime").type());
            assertEquals("DateTime", types.get("java.time.LocalDateTime").type());
            assertFalse(types.containsKey("example.CustomValue"));
        }
        assertTrue(configuration.types("unknown").isEmpty());
    }

    @Test
    public void mergedNodesOverrideTypesAndKeepDefaults() throws Exception {
        ConsoleTypes configuration = loadResources("/console-types/base.xml");
        assertEquals("1", configuration.types("custom").get("long").defaultValue());
        assertEquals("textarea", configuration.types("custom").get("java.lang.String").component());
        configuration = loadResources("/console-types/base.xml", "/console-types/override.xml");
        assertEquals("2", configuration.types("custom").get("long").defaultValue());
        assertEquals("integer", configuration.types("custom").get("long").type());
        assertEquals("", configuration.types("custom").get("java.lang.String").defaultValue());
    }

    private ConsoleTypes loadResources(String... paths) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        List<URL> resources = java.util.Arrays.stream(paths).map(path -> java.util.Objects.requireNonNull(getClass().getResource(path))).toList();
        ClassLoader loader = new ClassLoader(original) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if ("META-INF/meteor/console-types.xml".equals(name)) {
                    return Collections.enumeration(resources);
                }
                return super.getResources(name);
            }
        };
        try {
            Thread.currentThread().setContextClassLoader(loader);
            ConsoleTypes configuration = new ConsoleTypes();
            configuration.init();
            return configuration;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

}
