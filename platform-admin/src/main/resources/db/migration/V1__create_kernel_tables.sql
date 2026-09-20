-- 开源基座内核表。命名见 doc/00-architecture/51-platform-kernel.md。
-- 无种子数据。现网教务表不改。

CREATE TABLE sys_user (
    id           BIGINT       NOT NULL,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    display_name VARCHAR(100) NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    sys_admin    TINYINT      NOT NULL DEFAULT 0,
    version      BIGINT       NOT NULL DEFAULT 1,
    deleted      TIMESTAMP    NULL DEFAULT NULL,
    create_id    BIGINT       NULL,
    create_by    VARCHAR(64)  NULL,
    create_time  TIMESTAMP    NULL,
    update_by    VARCHAR(64)  NULL,
    update_time  TIMESTAMP    NULL,
    CONSTRAINT pk_sys_user PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE sys_role (
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
    CONSTRAINT pk_sys_role PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (code)
);

CREATE TABLE sys_user_role (
    id          BIGINT    NOT NULL,
    user_id     BIGINT    NOT NULL,
    role_id     BIGINT    NOT NULL,
    create_time TIMESTAMP NULL,
    CONSTRAINT pk_sys_user_role PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_sys_user_role_role_id ON sys_user_role (role_id);

CREATE TABLE sys_oauth_client (
    id                 BIGINT       NOT NULL,
    client_id          VARCHAR(64)  NOT NULL,
    client_secret      VARCHAR(256) NOT NULL,
    client_name        VARCHAR(100) NULL,
    grant_types        VARCHAR(255) NOT NULL,
    scopes             VARCHAR(255) NOT NULL,
    access_token_ttl   INT          NOT NULL,
    refresh_token_ttl  INT          NOT NULL,
    status             TINYINT      NOT NULL DEFAULT 1,
    remark             VARCHAR(500) NULL,
    version            BIGINT       NOT NULL DEFAULT 1,
    deleted            TIMESTAMP    NULL DEFAULT NULL,
    create_id          BIGINT       NULL,
    create_by          VARCHAR(64)  NULL,
    create_time        TIMESTAMP    NULL,
    update_by          VARCHAR(64)  NULL,
    update_time        TIMESTAMP    NULL,
    CONSTRAINT pk_sys_oauth_client PRIMARY KEY (id),
    CONSTRAINT uk_sys_oauth_client_client_id UNIQUE (client_id)
);
