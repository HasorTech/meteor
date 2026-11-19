/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.realmeteor.console;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.jdbc.JdbcRegistryConfiguration;
import org.h2.jdbcx.JdbcDataSource;

/** Entry point copied alone onto the generic client's JVM classpath. */
public final class GenericCallClient {
    private GenericCallClient() {
    }

    public static void main(String[] args) throws Exception {
        requireAbsent(args[5]);
        requireAbsent(args[6]);

        String protocol = args[0];
        String transport = args[1];
        ObjectMapper json = new ObjectMapper();
        MetConfiguration configuration = new MetConfiguration().setDataHome(Path.of(args[4])).setDefaultProtocol(protocol) //
                .addExtension(registry(args[2]));
        var connector = configuration.connector(transport).protocol(protocol);
        if (StringUtils.equals("http", transport)) {
            if (StringUtils.equals("grpc", protocol) || StringUtils.equals("triple", protocol)) {
                connector.option("httpVersion", 2);
            }
        }
        if (!StringUtils.equals("rsf", protocol)) {
            configuration.connector("tcp").protocol("rsf");
        }
        try (var context = configuration.buildContext()) {
            context.start();
            var response = context.getRsfClient().syncInvokeGeneric(args[3], protocol, "reflect", //
                    json.readValue(args[7], Object[].class), 5000);
            System.out.println("GENERIC_RESULT=" + json.writeValueAsString(response));
        }
    }

    static JdbcRegistryConfiguration registry(String url) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL(url);
        source.setUser("sa");
        JdbcRegistryConfiguration registry = new JdbcRegistryConfiguration().setDataSource(source);
        registry.setPublishHost("127.0.0.1");
        registry.setManagementToken("test-token");
        return registry;
    }

    private static void requireAbsent(String typeName) throws Exception {
        try {
            Class.forName(typeName, false, GenericCallClient.class.getClassLoader());
            throw new IllegalStateException("Business class is visible in client JVM: " + typeName);
        } catch (ClassNotFoundException expected) {
            // This process only has the runtime and the copied client entry point.
        }
    }
}
