---
title: "接入 JDBC 注册中心"
description: "消费者通过 JDBC 注册中心获取已登记的提供者地址。"
mdx:
  format: mdx
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# 接入 JDBC 注册中心

消费者通过 JDBC 注册中心获取已登记的提供者地址。

## 接入配置

以 MySQL 为例，配置数据库地址、用户名和密码。

在消费者的 `buildContext()` 前配置注册中心，订阅和调用代码省略。


```java
import net.hasor.meteor.MetContext;
import net.hasor.meteor.bootstrap.MetConfiguration;
import com.mysql.cj.jdbc.MysqlDataSource;
import net.hasor.meteor.registry.jdbc.JdbcRegistryConfiguration;

public class ConsumerMain {
    public static void main(String[] args) throws Exception {
        // 配置应用和接入协议
        MetConfiguration config = new MetConfiguration();
        config.setApplicationName("echo-consumer");

        // 接入注册中心
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://db.example.com:3306/meteor");
        dataSource.setUser("your-username");
        dataSource.setPassword("your-password");

        JdbcRegistryConfiguration registry = new JdbcRegistryConfiguration();
        registry.setDataSource(dataSource);
        config.addExtension(registry);

        config.connector("tcp").protocol("rsf");

        try (MetContext context = config.buildContext()) {
            ...
        }
    }
}
```


## 注意事项

- 注册中心不会自动建表，接入前执行下方对应的初始化脚本；运行账号需具有这些表的读写权限。
- 双方的服务分组、名称和版本须一致。
- 默认租约为 15000 ms，可用 `setLeaseMillis` 调整；数据库中租约过期的实例不再作为可用目标。数据源由应用管理，关闭 `MetContext` 不会关闭数据源。
- 发现为空时，核对注册中心配置、服务坐标和提供者是否运行。

## 数据库脚本

先准备数据库或 Schema，再执行对应脚本创建注册中心表和初始数据；脚本用于首次初始化，不用于已有表的升级。

<details>
<summary>查看数据库脚本</summary>

<Tabs groupId="registry-database">
<TabItem value="oracle" label="Oracle">

```sql
-- Registry schema. Run this script against a new empty database.
-- expires_at stores UTC epoch milliseconds.

CREATE TABLE meteor_applications
(
    application_id VARCHAR2(64) NOT NULL,
    search_text    NCLOB NOT NULL,
    revision       VARCHAR2(36) NOT NULL,
    body           CLOB  NOT NULL,
    PRIMARY KEY (application_id)
);

CREATE TABLE meteor_instances
(
    instance_id      VARCHAR2(64) NOT NULL,
    application_id   VARCHAR2(64) NOT NULL,
    unit_name        VARCHAR2(256),
    search_text      NCLOB NOT NULL,
    revision         VARCHAR2(36) NOT NULL,
    body             CLOB  NOT NULL,
    -- Keep the latest registration session after runtime departure.
    session_id       VARCHAR2(36) NOT NULL,
    expires_at       NUMBER(19) NULL,
    runtime_revision VARCHAR2(36) NULL,
    runtime_body     CLOB NULL,
    rule_controls    CLOB NULL,
    PRIMARY KEY (instance_id)
);

CREATE TABLE meteor_services
(
    service_id    VARCHAR2(64) NOT NULL,
    search_text   NCLOB NOT NULL,
    revision      VARCHAR2(36) NOT NULL,
    rule_controls CLOB,
    body          CLOB  NOT NULL,
    PRIMARY KEY (service_id)
);

CREATE TABLE meteor_instance_services
(
    instance_id     VARCHAR2(64) NOT NULL,
    service_id      VARCHAR2(64) NOT NULL,
    member_role     VARCHAR2(16) NOT NULL,
    session_id      VARCHAR2(36) NOT NULL,
    search_text     NCLOB NOT NULL,
    search_role     NCLOB,
    search_protocol NCLOB,
    search_address  NCLOB,
    revision        VARCHAR2(36) NOT NULL,
    rule_controls   CLOB,
    body            CLOB  NOT NULL,
    PRIMARY KEY (instance_id, service_id)
);

CREATE TABLE meteor_service_methods
(
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    method_path VARCHAR(129) NOT NULL,
    revision    VARCHAR(36)  NOT NULL,
    body        CLOB         NOT NULL
);

CREATE INDEX met_methods_path ON meteor_service_methods (method_path);

CREATE TABLE meteor_governance
(
    instance_id   VARCHAR2(64) NOT NULL,
    service_id    VARCHAR2(64) NOT NULL,
    item_name     VARCHAR2(64) NOT NULL,
    revision      VARCHAR2(36) NOT NULL,
    session_id    VARCHAR2(36),
    rule_controls CLOB,
    body          CLOB,
    PRIMARY KEY (instance_id, service_id, item_name)
);

-- Global state also holds rule switches when no state override exists.
INSERT INTO meteor_governance(instance_id, service_id, item_name, revision, body)
VALUES ('global', 'global', 'state', '00000000-0000-0000-0000-000000000000', NULL);

CREATE INDEX met_governance_session ON meteor_governance (session_id);

CREATE INDEX met_instances_application ON meteor_instances (application_id, instance_id);

CREATE INDEX met_instances_unit ON meteor_instances (application_id, unit_name, instance_id);

CREATE INDEX met_instances_session ON meteor_instances (session_id, expires_at);

CREATE INDEX met_iservices_session ON meteor_instance_services (session_id);

CREATE INDEX met_iservices_members ON meteor_instance_services (service_id, member_role, instance_id);
```

</TabItem>
<TabItem value="dm" label="达梦 DM8">

```sql
-- DM8 registry schema. Run this script in an empty schema.
-- Data types: https://eco.dameng.com/document/dm/zh-cn/sql-dev/dmpl-sql-datatype
-- expires_at stores UTC epoch milliseconds.

CREATE TABLE meteor_applications
(
    application_id VARCHAR(64) NOT NULL,
    search_text    CLOB        NOT NULL,
    revision       VARCHAR(36) NOT NULL,
    body           CLOB        NOT NULL,
    PRIMARY KEY (application_id)
);

CREATE TABLE meteor_instances
(
    instance_id      VARCHAR(64) NOT NULL,
    application_id   VARCHAR(64) NOT NULL,
    unit_name        VARCHAR(256),
    search_text      CLOB        NOT NULL,
    revision         VARCHAR(36) NOT NULL,
    body             CLOB        NOT NULL,
    -- Keep the latest registration session after runtime departure.
    session_id       VARCHAR(36) NOT NULL,
    expires_at       BIGINT NULL,
    runtime_revision VARCHAR(36) NULL,
    runtime_body     CLOB NULL,
    rule_controls    CLOB NULL,
    PRIMARY KEY (instance_id)
);

CREATE TABLE meteor_services
(
    service_id    VARCHAR(64) NOT NULL,
    search_text   CLOB        NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    rule_controls CLOB,
    body          CLOB        NOT NULL,
    PRIMARY KEY (service_id)
);

CREATE TABLE meteor_instance_services
(
    instance_id     VARCHAR(64) NOT NULL,
    service_id      VARCHAR(64) NOT NULL,
    member_role     VARCHAR(16) NOT NULL,
    session_id      VARCHAR(36) NOT NULL,
    search_text     CLOB        NOT NULL,
    search_role     CLOB,
    search_protocol CLOB,
    search_address  CLOB,
    revision        VARCHAR(36) NOT NULL,
    rule_controls   CLOB,
    body            CLOB        NOT NULL,
    PRIMARY KEY (instance_id, service_id)
);

CREATE TABLE meteor_service_methods
(
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    method_path VARCHAR(129) NOT NULL,
    revision    VARCHAR(36)  NOT NULL,
    body        CLOB         NOT NULL
);

CREATE INDEX met_methods_path ON meteor_service_methods (method_path);

CREATE TABLE meteor_governance
(
    instance_id   VARCHAR(64) NOT NULL,
    service_id    VARCHAR(64) NOT NULL,
    item_name     VARCHAR(64) NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    session_id    VARCHAR(36),
    rule_controls CLOB,
    body          CLOB,
    PRIMARY KEY (instance_id, service_id, item_name)
);

-- Global state also holds rule switches when no state override exists.
INSERT INTO meteor_governance(instance_id, service_id, item_name, revision, body)
VALUES ('global', 'global', 'state', '00000000-0000-0000-0000-000000000000', NULL);

CREATE INDEX met_governance_session ON meteor_governance (session_id);

CREATE INDEX met_instances_application ON meteor_instances (application_id, instance_id);

CREATE INDEX met_instances_unit ON meteor_instances (application_id, unit_name, instance_id);

CREATE INDEX met_instances_session ON meteor_instances (session_id, expires_at);

CREATE INDEX met_iservices_session ON meteor_instance_services (session_id);

CREATE INDEX met_iservices_members ON meteor_instance_services (service_id, member_role, instance_id);

COMMIT;
```

</TabItem>
<TabItem value="mysql" label="MySQL">

```sql
-- Registry schema. Run this script against a new empty database.
-- expires_at stores UTC epoch milliseconds.

CREATE TABLE meteor_applications
(
    application_id VARCHAR(64) NOT NULL,
    search_text    LONGTEXT    NOT NULL,
    revision       VARCHAR(36) NOT NULL,
    body           LONGTEXT    NOT NULL,
    PRIMARY KEY (application_id)
);

CREATE TABLE meteor_instances
(
    instance_id      VARCHAR(64) NOT NULL,
    application_id   VARCHAR(64) NOT NULL,
    unit_name        VARCHAR(256),
    search_text      LONGTEXT    NOT NULL,
    revision         VARCHAR(36) NOT NULL,
    body             LONGTEXT    NOT NULL,
    -- Keep the latest registration session after runtime departure.
    session_id       VARCHAR(36) NOT NULL,
    expires_at       BIGINT NULL,
    runtime_revision VARCHAR(36) NULL,
    runtime_body     LONGTEXT NULL,
    rule_controls    LONGTEXT NULL,
    PRIMARY KEY (instance_id)
);

CREATE TABLE meteor_services
(
    service_id    VARCHAR(64) NOT NULL,
    search_text   LONGTEXT    NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    rule_controls LONGTEXT,
    body          LONGTEXT    NOT NULL,
    PRIMARY KEY (service_id)
);

CREATE TABLE meteor_instance_services
(
    instance_id     VARCHAR(64) NOT NULL,
    service_id      VARCHAR(64) NOT NULL,
    member_role     VARCHAR(16) NOT NULL,
    session_id      VARCHAR(36) NOT NULL,
    search_text     LONGTEXT    NOT NULL,
    search_role     LONGTEXT,
    search_protocol LONGTEXT,
    search_address  LONGTEXT,
    revision        VARCHAR(36) NOT NULL,
    rule_controls   LONGTEXT,
    body            LONGTEXT    NOT NULL,
    PRIMARY KEY (instance_id, service_id)
);

CREATE TABLE meteor_service_methods
(
    id          VARCHAR(36)                                        NOT NULL PRIMARY KEY,
    method_path VARCHAR(129) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision    VARCHAR(36)                                        NOT NULL,
    body        LONGTEXT                                           NOT NULL
);

CREATE INDEX met_methods_path ON meteor_service_methods (method_path);

CREATE TABLE meteor_governance
(
    instance_id   VARCHAR(64) NOT NULL,
    service_id    VARCHAR(64) NOT NULL,
    item_name     VARCHAR(64) NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    session_id    VARCHAR(36),
    rule_controls LONGTEXT,
    body          LONGTEXT,
    PRIMARY KEY (instance_id, service_id, item_name)
);

-- Global state also holds rule switches when no state override exists.
INSERT INTO meteor_governance(instance_id, service_id, item_name, revision, body)
VALUES ('global', 'global', 'state', '00000000-0000-0000-0000-000000000000', NULL);

CREATE INDEX met_governance_session ON meteor_governance (session_id);

CREATE INDEX met_instances_application ON meteor_instances (application_id, instance_id);

CREATE INDEX met_instances_unit ON meteor_instances (application_id, unit_name, instance_id);

CREATE INDEX met_instances_session ON meteor_instances (session_id, expires_at);

CREATE INDEX met_iservices_session ON meteor_instance_services (session_id);

CREATE INDEX met_iservices_members ON meteor_instance_services (service_id, member_role, instance_id);
```

</TabItem>
<TabItem value="sqlserver" label="SQL Server">

```sql
-- Registry schema. Run this script against a new empty database.
-- expires_at stores UTC epoch milliseconds.

CREATE TABLE meteor_applications
(
    application_id VARCHAR(64) NOT NULL,
    search_text    NVARCHAR(MAX) NOT NULL,
    revision       VARCHAR(36) NOT NULL,
    body           NVARCHAR(MAX) NOT NULL,
    PRIMARY KEY (application_id)
);

CREATE TABLE meteor_instances
(
    instance_id      VARCHAR(64) NOT NULL,
    application_id   VARCHAR(64) NOT NULL,
    unit_name        VARCHAR(256),
    search_text      NVARCHAR(MAX) NOT NULL,
    revision         VARCHAR(36) NOT NULL,
    body             NVARCHAR(MAX) NOT NULL,
    -- Keep the latest registration session after runtime departure.
    session_id       VARCHAR(36) NOT NULL,
    expires_at       BIGINT NULL,
    runtime_revision VARCHAR(36) NULL,
    runtime_body     NVARCHAR(MAX) NULL,
    rule_controls    NVARCHAR(MAX) NULL,
    PRIMARY KEY (instance_id)
);

CREATE TABLE meteor_services
(
    service_id    VARCHAR(64) NOT NULL,
    search_text   NVARCHAR(MAX) NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    rule_controls NVARCHAR(MAX),
    body          NVARCHAR(MAX) NOT NULL,
    PRIMARY KEY (service_id)
);

CREATE TABLE meteor_instance_services
(
    instance_id     VARCHAR(64) NOT NULL,
    service_id      VARCHAR(64) NOT NULL,
    member_role     VARCHAR(16) NOT NULL,
    session_id      VARCHAR(36) NOT NULL,
    search_text     NVARCHAR(MAX) NOT NULL,
    search_role     NVARCHAR(MAX),
    search_protocol NVARCHAR(MAX),
    search_address  NVARCHAR(MAX),
    revision        VARCHAR(36) NOT NULL,
    rule_controls   NVARCHAR(MAX),
    body            NVARCHAR(MAX) NOT NULL,
    PRIMARY KEY (instance_id, service_id)
);

CREATE TABLE meteor_service_methods
(
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    method_path VARCHAR(129) NOT NULL,
    revision    VARCHAR(36)  NOT NULL,
    body        NVARCHAR(MAX) NOT NULL
);

CREATE INDEX met_methods_path ON meteor_service_methods (method_path);

CREATE TABLE meteor_governance
(
    instance_id   VARCHAR(64) NOT NULL,
    service_id    VARCHAR(64) NOT NULL,
    item_name     VARCHAR(64) NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    session_id    VARCHAR(36),
    rule_controls NVARCHAR(MAX),
    body          NVARCHAR(MAX),
    PRIMARY KEY (instance_id, service_id, item_name)
);

-- Global state also holds rule switches when no state override exists.
INSERT INTO meteor_governance(instance_id, service_id, item_name, revision, body)
VALUES ('global', 'global', 'state', '00000000-0000-0000-0000-000000000000', NULL);

CREATE INDEX met_governance_session ON meteor_governance (session_id);

CREATE INDEX met_instances_application ON meteor_instances (application_id, instance_id);

CREATE INDEX met_instances_unit ON meteor_instances (application_id, unit_name, instance_id);

CREATE INDEX met_instances_session ON meteor_instances (session_id, expires_at);

CREATE INDEX met_iservices_session ON meteor_instance_services (session_id);

CREATE INDEX met_iservices_members ON meteor_instance_services (service_id, member_role, instance_id);
```

</TabItem>
<TabItem value="postgresql" label="PostgreSQL">

```sql
-- Registry schema. Run this script against a new empty database.
-- expires_at stores UTC epoch milliseconds.

CREATE TABLE meteor_applications
(
    application_id VARCHAR(64) NOT NULL,
    search_text    TEXT        NOT NULL,
    revision       VARCHAR(36) NOT NULL,
    body           TEXT        NOT NULL,
    PRIMARY KEY (application_id)
);

CREATE TABLE meteor_instances
(
    instance_id      VARCHAR(64) NOT NULL,
    application_id   VARCHAR(64) NOT NULL,
    unit_name        VARCHAR(256),
    search_text      TEXT        NOT NULL,
    revision         VARCHAR(36) NOT NULL,
    body             TEXT        NOT NULL,
    -- Keep the latest registration session after runtime departure.
    session_id       VARCHAR(36) NOT NULL,
    expires_at       BIGINT NULL,
    runtime_revision VARCHAR(36) NULL,
    runtime_body     TEXT NULL,
    rule_controls    TEXT NULL,
    PRIMARY KEY (instance_id)
);

CREATE TABLE meteor_services
(
    service_id    VARCHAR(64) NOT NULL,
    search_text   TEXT        NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    rule_controls TEXT,
    body          TEXT        NOT NULL,
    PRIMARY KEY (service_id)
);

CREATE TABLE meteor_instance_services
(
    instance_id     VARCHAR(64) NOT NULL,
    service_id      VARCHAR(64) NOT NULL,
    member_role     VARCHAR(16) NOT NULL,
    session_id      VARCHAR(36) NOT NULL,
    search_text     TEXT        NOT NULL,
    search_role     TEXT,
    search_protocol TEXT,
    search_address  TEXT,
    revision        VARCHAR(36) NOT NULL,
    rule_controls   TEXT,
    body            TEXT        NOT NULL,
    PRIMARY KEY (instance_id, service_id)
);

CREATE TABLE meteor_service_methods
(
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    method_path VARCHAR(129) NOT NULL,
    revision    VARCHAR(36)  NOT NULL,
    body        TEXT         NOT NULL
);

CREATE INDEX met_methods_path ON meteor_service_methods (method_path varchar_pattern_ops);

CREATE TABLE meteor_governance
(
    instance_id   VARCHAR(64) NOT NULL,
    service_id    VARCHAR(64) NOT NULL,
    item_name     VARCHAR(64) NOT NULL,
    revision      VARCHAR(36) NOT NULL,
    session_id    VARCHAR(36),
    rule_controls TEXT,
    body          TEXT,
    PRIMARY KEY (instance_id, service_id, item_name)
);

-- Global state also holds rule switches when no state override exists.
INSERT INTO meteor_governance(instance_id, service_id, item_name, revision, body)
VALUES ('global', 'global', 'state', '00000000-0000-0000-0000-000000000000', NULL);

CREATE INDEX met_governance_session ON meteor_governance (session_id);

CREATE INDEX met_instances_application ON meteor_instances (application_id, instance_id);

CREATE INDEX met_instances_unit ON meteor_instances (application_id, unit_name, instance_id);

CREATE INDEX met_instances_session ON meteor_instances (session_id, expires_at);

CREATE INDEX met_iservices_session ON meteor_instance_services (session_id);

CREATE INDEX met_iservices_members ON meteor_instance_services (service_id, member_role, instance_id);
```

</TabItem>
</Tabs>

</details>
