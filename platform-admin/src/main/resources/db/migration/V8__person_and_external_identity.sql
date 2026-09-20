-- 自然人与外部身份。H2 / MySQL 兼容；无种子。见 doc/00-architecture/58-platform-login.md。

CREATE TABLE sys_person (
    id               BIGINT       NOT NULL,
    real_name        VARCHAR(100) NULL,
    id_doc_type      VARCHAR(32)  NOT NULL DEFAULT 'id_card',
    id_lookup_digest VARCHAR(128) NULL,
    id_cipher        VARCHAR(512) NULL,
    verified_at      TIMESTAMP    NULL,
    version          BIGINT       NOT NULL DEFAULT 1,
    deleted          TIMESTAMP    NULL DEFAULT NULL,
    create_id        BIGINT       NULL,
    create_by        VARCHAR(64)  NULL,
    create_time      TIMESTAMP    NULL,
    update_by        VARCHAR(64)  NULL,
    update_time      TIMESTAMP    NULL,
    CONSTRAINT pk_sys_person PRIMARY KEY (id)
);

CREATE INDEX idx_sys_person_lookup ON sys_person (id_lookup_digest);

ALTER TABLE sys_user ADD COLUMN person_id BIGINT NULL;

CREATE INDEX idx_sys_user_person_id ON sys_user (person_id);

CREATE TABLE sys_external_identity (
    id          BIGINT       NOT NULL,
    provider    VARCHAR(64)  NOT NULL,
    subject     VARCHAR(128) NOT NULL,
    person_id   BIGINT       NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 1,
    deleted     TIMESTAMP    NULL DEFAULT NULL,
    create_id   BIGINT       NULL,
    create_by   VARCHAR(64)  NULL,
    create_time TIMESTAMP    NULL,
    update_by   VARCHAR(64)  NULL,
    update_time TIMESTAMP    NULL,
    CONSTRAINT pk_sys_external_identity PRIMARY KEY (id),
    CONSTRAINT uk_sys_external_identity UNIQUE (provider, subject)
);

CREATE INDEX idx_sys_external_identity_person ON sys_external_identity (person_id);
