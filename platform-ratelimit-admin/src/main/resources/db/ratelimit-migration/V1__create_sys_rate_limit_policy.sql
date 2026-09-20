-- 限流策略主表与发布修订历史（独立 Flyway：ratelimit_schema_history）

CREATE TABLE sys_rate_limit_policy (
    id                   BIGINT        NOT NULL,
    policy_code          VARCHAR(128)  NOT NULL,
    algorithm            VARCHAR(32)   NOT NULL,
    limit_count          BIGINT        NOT NULL,
    period_ms            BIGINT        NOT NULL,
    burst                BIGINT        NOT NULL,
    store_failure_policy VARCHAR(16)   NOT NULL,
    enabled              TINYINT       NOT NULL DEFAULT 1,
    version              BIGINT        NOT NULL DEFAULT 1,
    remark               VARCHAR(512)  NULL,
    create_by            VARCHAR(64)   NULL,
    create_time          TIMESTAMP     NULL,
    update_by            VARCHAR(64)   NULL,
    update_time          TIMESTAMP     NULL,
    CONSTRAINT pk_sys_rate_limit_policy PRIMARY KEY (id),
    CONSTRAINT uk_sys_rate_limit_policy_code UNIQUE (policy_code)
);

CREATE TABLE sys_rate_limit_policy_revision (
    revision      BIGINT        NOT NULL AUTO_INCREMENT,
    snapshot_json LONGTEXT      NOT NULL,
    created_at    TIMESTAMP     NULL,
    created_by    VARCHAR(64)   NULL,
    CONSTRAINT pk_sys_rate_limit_policy_revision PRIMARY KEY (revision)
);
