-- 资源 / 字典 / 配置 / 操作审计。无种子。现网教务表不改。

CREATE TABLE sys_resource (
    id          BIGINT       NOT NULL,
    parent_id   BIGINT       NULL,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 菜单 / 2 按钮',
    path        VARCHAR(255) NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version     BIGINT       NOT NULL DEFAULT 1,
    deleted     TIMESTAMP    NULL DEFAULT NULL,
    create_id   BIGINT       NULL,
    create_by   VARCHAR(64)  NULL,
    create_time TIMESTAMP    NULL,
    update_by   VARCHAR(64)  NULL,
    update_time TIMESTAMP    NULL,
    CONSTRAINT pk_sys_resource PRIMARY KEY (id),
    CONSTRAINT uk_sys_resource_code UNIQUE (code)
);

CREATE INDEX idx_sys_resource_parent_id ON sys_resource (parent_id);

CREATE TABLE sys_role_resource (
    id          BIGINT    NOT NULL,
    role_id     BIGINT    NOT NULL,
    resource_id BIGINT    NOT NULL,
    create_time TIMESTAMP NULL,
    CONSTRAINT pk_sys_role_resource PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_resource UNIQUE (role_id, resource_id)
);

CREATE INDEX idx_sys_role_resource_resource_id ON sys_role_resource (resource_id);

CREATE TABLE sys_dict (
    id          BIGINT       NOT NULL,
    dict_type   VARCHAR(64)  NOT NULL,
    dict_code   VARCHAR(64)  NOT NULL,
    dict_label  VARCHAR(100) NOT NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version     BIGINT       NOT NULL DEFAULT 1,
    deleted     TIMESTAMP    NULL DEFAULT NULL,
    create_id   BIGINT       NULL,
    create_by   VARCHAR(64)  NULL,
    create_time TIMESTAMP    NULL,
    update_by   VARCHAR(64)  NULL,
    update_time TIMESTAMP    NULL,
    CONSTRAINT pk_sys_dict PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_type_code UNIQUE (dict_type, dict_code)
);

CREATE TABLE sys_config (
    id           BIGINT        NOT NULL,
    config_key   VARCHAR(128)  NOT NULL,
    config_value VARCHAR(2000) NULL,
    remark       VARCHAR(500)  NULL,
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version      BIGINT        NOT NULL DEFAULT 1,
    deleted      TIMESTAMP     NULL DEFAULT NULL,
    create_id    BIGINT        NULL,
    create_by    VARCHAR(64)   NULL,
    create_time  TIMESTAMP     NULL,
    update_by    VARCHAR(64)   NULL,
    update_time  TIMESTAMP     NULL,
    CONSTRAINT pk_sys_config PRIMARY KEY (id),
    CONSTRAINT uk_sys_config_key UNIQUE (config_key)
);

CREATE TABLE sys_oper_log (
    id            BIGINT        NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    http_method   VARCHAR(16)   NULL,
    request_uri   VARCHAR(255)  NULL,
    request_param VARCHAR(2000) NULL,
    success       TINYINT       NOT NULL DEFAULT 1 COMMENT '1 成功 / 0 失败',
    error_message VARCHAR(500)  NULL,
    operator_id   BIGINT        NULL,
    operator_name VARCHAR(64)   NULL,
    cost_ms       INT           NULL,
    create_time   TIMESTAMP     NULL,
    CONSTRAINT pk_sys_oper_log PRIMARY KEY (id)
);

CREATE INDEX idx_sys_oper_log_create_time ON sys_oper_log (create_time);
CREATE INDEX idx_sys_oper_log_operator_id ON sys_oper_log (operator_id);
