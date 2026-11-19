/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.model.fo;
import java.net.URI;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;

/** 本地保存的连接定义，不是注册中心业务数据。 */
public final class ClusterProfile {
    private String id;
    private String name;
    private String type;
    private String address;
    private String database;
    private String username;
    private String password;
    private String managementToken;
    private String remoteClusterId;
    private String namespace;
    private String group;
    private String root;

    public String getId() {
        return this.id;
    }

    public void setId(String value) {
        this.id = value;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String value) {
        this.type = value;
    }

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String value) {
        this.address = value;
    }

    public String getDatabase() {
        return this.database;
    }

    public void setDatabase(String value) {
        this.database = value;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String value) {
        this.username = value;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String value) {
        this.password = value;
    }

    public String getManagementToken() {
        return this.managementToken;
    }

    public void setManagementToken(String value) {
        this.managementToken = value;
    }

    public String getRemoteClusterId() {
        return this.remoteClusterId;
    }

    public void setRemoteClusterId(String value) {
        this.remoteClusterId = value;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public void setNamespace(String value) {
        this.namespace = value;
    }

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String value) {
        this.group = value;
    }

    public String getRoot() {
        return this.root;
    }

    public void setRoot(String value) {
        this.root = value;
    }

    public void validate() {
        if (StringUtils.isBlank(this.name) || StringUtils.isBlank(this.address)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CONNECTION_NAME_AND_ADDRESS_ARE);
        }
        if (this.name.length() > 100 || this.address.length() > 2048) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_CONNECTION_NAME_OR_ADDRESS_IS);
        }
        if (this.type == null) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_SELECT_A_REGISTRY_TYPE);
        }
        if (this.address.matches("(?i).*([?;&](password|pwd)=|://[^/]+@).*")) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_ENTER_CREDENTIALS_IN_THE_PASSWORD_FIELD);
        }
        if (StringUtils.equals("remote", this.type)) {
            URI uri;
            try {
                uri = URI.create(this.address);
            } catch (IllegalArgumentException failure) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_INVALID_REMOTE_ADDRESS);
            }
            if (!StringUtils.equalsIgnoreCase(uri.getScheme(), "rsf") || uri.getHost() == null || uri.getPort() <= 0 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_REMOTE_ADDRESS_MUST_USE_RSF);
            }
            if (StringUtils.isBlank(this.managementToken)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_REMOTE_CONNECTIONS_REQUIRE_A_MANAGEMENT_TOKEN);
            }
            if (this.managementToken.length() > 4096) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_MANAGEMENT_TOKEN_IS_TOO_LONG);
            }
            this.username = null;
            this.password = null;
            this.database = null;
            this.namespace = null;
            this.group = null;
            this.root = null;
        } else if (StringUtils.equals("jdbc", this.type)) {
            String prefix = switch (this.database == null ? "" : this.database) {
                case "h2" -> "jdbc:h2:";
                case "mysql" -> "jdbc:mysql:";
                case "postgresql" -> "jdbc:postgresql:";
                case "oracle" -> "jdbc:oracle:";
                case "sqlserver" -> "jdbc:sqlserver:";
                default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_SELECT_A_DATABASE_TYPE);
            };
            if (!this.address.startsWith(prefix)) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_THE_JDBC_URL_DOES_NOT_MATCH);
            }
        } else if (!StringUtils.equals("nacos", this.type) && !StringUtils.equals("zookeeper", this.type)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_UNSUPPORTED_REGISTRY_TYPE);
        }
        if (!StringUtils.equals("remote", this.type)) {
            this.managementToken = null;
            this.remoteClusterId = null;
        }
    }
}
