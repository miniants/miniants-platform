-- JWT 签发/验签密钥库。内核迁移历史表 platform_schema_history；业务库走 platform.flyway。
-- 私钥只存 AES-GCM 密文（private_key_cipher），不落明文。摘旧不物理删除，保留密文作回滚底仓。
-- is_active=1 全表最多一行（应用事务保证；idx 便于查签发钥）。

CREATE TABLE sys_jwt_keystore (
    id                  BIGINT       NOT NULL,
    kid                 VARCHAR(64)  NOT NULL,
    algorithm           VARCHAR(16)  NOT NULL DEFAULT 'RS256',
    public_key          TEXT         NOT NULL,
    private_key_cipher  TEXT         NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'verify-only',
    is_active           TINYINT      NOT NULL DEFAULT 0,
    activated_at        TIMESTAMP    NULL,
    retired_at          TIMESTAMP    NULL,
    version             BIGINT       NOT NULL DEFAULT 1,
    deleted             TIMESTAMP    NULL DEFAULT NULL,
    create_id           BIGINT       NULL,
    create_by           VARCHAR(64)  NULL,
    create_time         TIMESTAMP    NULL,
    update_by           VARCHAR(64)  NULL,
    update_time         TIMESTAMP    NULL,
    CONSTRAINT pk_sys_jwt_keystore PRIMARY KEY (id),
    CONSTRAINT uk_sys_jwt_keystore_kid UNIQUE (kid)
);

CREATE INDEX idx_sys_jwt_keystore_is_active ON sys_jwt_keystore (is_active);
CREATE INDEX idx_sys_jwt_keystore_status ON sys_jwt_keystore (status);
