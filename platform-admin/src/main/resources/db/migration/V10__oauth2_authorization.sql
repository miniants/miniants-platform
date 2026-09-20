-- SAS 授权持久化（Spring Authorization Server JDBC 标准表）
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes TEXT NULL,
    attributes TEXT NULL,
    state VARCHAR(500) NULL,
    authorization_code_value TEXT NULL,
    authorization_code_issued_at TIMESTAMP NULL,
    authorization_code_expires_at TIMESTAMP NULL,
    authorization_code_metadata TEXT NULL,
    access_token_value TEXT NULL,
    access_token_issued_at TIMESTAMP NULL,
    access_token_expires_at TIMESTAMP NULL,
    access_token_metadata TEXT NULL,
    access_token_type VARCHAR(100) NULL,
    access_token_scopes TEXT NULL,
    refresh_token_value TEXT NULL,
    refresh_token_issued_at TIMESTAMP NULL,
    refresh_token_expires_at TIMESTAMP NULL,
    refresh_token_metadata TEXT NULL,
    oidc_id_token_value TEXT NULL,
    oidc_id_token_issued_at TIMESTAMP NULL,
    oidc_id_token_expires_at TIMESTAMP NULL,
    oidc_id_token_metadata TEXT NULL,
    user_code_value TEXT NULL,
    user_code_issued_at TIMESTAMP NULL,
    user_code_expires_at TIMESTAMP NULL,
    user_code_metadata TEXT NULL,
    device_code_value TEXT NULL,
    device_code_issued_at TIMESTAMP NULL,
    device_code_expires_at TIMESTAMP NULL,
    device_code_metadata TEXT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities TEXT NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
