/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.settings;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import net.hasor.cobble.ClassUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.core.Init;
import net.hasor.core.Singleton;
import net.hasor.meteor.connector.ProtocolProvider;
import net.hasor.meteor.connector.transport.TransportFactory;
import net.hasor.meteor.console.model.vo.ConsoleCapability;

/** 只检查当前进程的 SPI 和 JDBC 驱动；不建立远程连接，也不代表远端服务的协议支持。 */
@Singleton
public final class ConsoleCapabilities {
    private List<String>            debugProtocols = List.of();
    private List<ConsoleCapability> jdbcDatabases  = List.of();

    @Init
    public void init() {
        ClassLoader loader = ConsoleCapabilities.class.getClassLoader();
        Set<String> transports = new TreeSet<>();
        for (TransportFactory<?> transport : ServiceLoader.load(TransportFactory.class, loader)) {
            transports.add(transport.name());
        }
        Set<String> protocols = new TreeSet<>();
        for (ProtocolProvider<?> protocol : ServiceLoader.load(ProtocolProvider.class, loader)) {
            if (transports.contains(protocol.transportType())) {
                protocols.add(protocol.name());
            }
        }
        this.debugProtocols = List.copyOf(protocols);

        // 顺序决定连接页面的数据库选项顺序，优先展示常用的外部注册中心数据库。
        List<ConsoleCapability> databases = new ArrayList<>();
        databases.add(database("postgresql", "PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://localhost/meteor", loader));
        databases.add(database("mysql", "MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/meteor", loader));
        databases.add(database("h2", "H2", "org.h2.Driver", "jdbc:h2:mem:meteor", loader));
        databases.add(database("oracle", "Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:xe", loader));
        databases.add(database("sqlserver", "SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://localhost", loader));
        this.jdbcDatabases = List.copyOf(databases);
    }

    public List<String> debugProtocols() {
        return this.debugProtocols;
    }

    public List<ConsoleCapability> jdbcDatabases() {
        return this.jdbcDatabases;
    }

    public void requireDebugProtocol(String protocol) {
        if (!this.debugProtocols.contains(protocol)) {
            throw new UnsupportedOperationException("当前控制台未安装 " + protocol + " 协议插件，无法调试");
        }
    }

    public void requireDatabase(String database) {
        for (ConsoleCapability capability : this.jdbcDatabases) {
            if (StringUtils.equals(capability.getKey(), database)) {
                if (!capability.isAvailable()) {
                    throw new UnsupportedOperationException(capability.getReason());
                }
                return;
            }
        }
        throw new IllegalArgumentException("不支持的数据库类型：" + database);
    }

    private static ConsoleCapability database(String key, String label, String driver, String url, ClassLoader loader) {
        try {
            Class<?> type = ClassUtils.getClass(loader, driver, true);
            boolean available = Driver.class.isAssignableFrom(type) && DriverManager.getDriver(url) != null;
            return new ConsoleCapability(key, label, available, available ? "" : "当前控制台未安装 " + label + " 驱动");
        } catch (ClassNotFoundException | LinkageError | SQLException failure) {
            return new ConsoleCapability(key, label, false, "当前控制台未安装可用的 " + label + " 驱动");
        }
    }
}
