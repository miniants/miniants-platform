-- JWT 包装密钥来源与库内密钥。wrap_key 仅 database 模式使用，管理接口不得回显。

CREATE TABLE sys_jwt_wrap_settings (
    id           BIGINT       NOT NULL,
    wrap_source  VARCHAR(16)  NOT NULL DEFAULT 'env',
    wrap_key     VARCHAR(128) NULL,
    version      BIGINT       NOT NULL DEFAULT 1,
    update_by    VARCHAR(64)  NULL,
    update_time  TIMESTAMP    NULL,
    CONSTRAINT pk_sys_jwt_wrap_settings PRIMARY KEY (id)
);
