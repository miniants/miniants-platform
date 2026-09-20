-- 租户注册表。隔离策略由应用配置决定，不写在行上。无种子。

CREATE TABLE sys_tenant (
    id          BIGINT       NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    version     BIGINT       NOT NULL DEFAULT 1,
    deleted     TIMESTAMP    NULL DEFAULT NULL,
    create_id   BIGINT       NULL,
    create_by   VARCHAR(64)  NULL,
    create_time TIMESTAMP    NULL,
    update_by   VARCHAR(64)  NULL,
    update_time TIMESTAMP    NULL,
    CONSTRAINT pk_sys_tenant PRIMARY KEY (id),
    CONSTRAINT uk_sys_tenant_code UNIQUE (code)
);
