-- 三张扩展表，把内核管理面补成现网教务的超集。无种子。现网教务表不改。
--
-- 都是「一对一、可为空」：不用这些能力的项目一行都不会有，主表保持干净。

-- 前端路由字段。放扩展表而不是主表：这些形状来自具体前端框架的路由约定，
-- 主表进了这些列，换前端框架就要改内核建表脚本。
CREATE TABLE sys_resource_meta (
    resource_id   BIGINT       NOT NULL,
    route_name    VARCHAR(100) NULL COMMENT '前端路由 name，与主表 name（展示名）不是一回事',
    component     VARCHAR(255) NULL,
    redirect      VARCHAR(255) NULL,
    icon          VARCHAR(64)  NULL,
    link          VARCHAR(512) NULL COMMENT '外链地址；非空即为外链',
    hide          TINYINT      NOT NULL DEFAULT 0,
    keep_alive    TINYINT      NOT NULL DEFAULT 0,
    affix         TINYINT      NOT NULL DEFAULT 0,
    iframe        TINYINT      NOT NULL DEFAULT 0,
    create_time   TIMESTAMP    NULL,
    update_time   TIMESTAMP    NULL,
    CONSTRAINT pk_sys_resource_meta PRIMARY KEY (resource_id)
);

-- 数据范围。现网存在 sys_role 的 ds_type / ds_scope 两列上，这里拆成扩展表：
-- 单租户小项目根本不需要数据范围，不该为此在角色主表上背两列。
--
-- scope_type 取值与现网一致（含跳号的 4），迁移时可直接搬，不用做值映射。
CREATE TABLE sys_role_data_scope (
    role_id     BIGINT       NOT NULL,
    scope_type  TINYINT      NOT NULL DEFAULT 2 COMMENT '0 全部 / 1 自定义 / 2 用户已绑组织 / 4 仅本人',
    scope_value VARCHAR(512) NULL COMMENT 'scope_type=1 时的组织 ID，逗号分隔',
    create_time TIMESTAMP    NULL,
    update_time TIMESTAMP    NULL,
    CONSTRAINT pk_sys_role_data_scope PRIMARY KEY (role_id)
);

-- 用户资料。主表只留登录必需的列，联系方式与展示信息放这里。
-- 不含 salt：内核口令一律 bcrypt，盐在哈希串里。
CREATE TABLE sys_user_profile (
    user_id        BIGINT       NOT NULL,
    real_name      VARCHAR(100) NULL,
    nick_name      VARCHAR(64)  NULL,
    avatar         VARCHAR(255) NULL,
    sex            VARCHAR(8)   NULL,
    phone          VARCHAR(32)  NULL,
    phone_verified TINYINT      NOT NULL DEFAULT 0,
    email          VARCHAR(128) NULL,
    email_verified TINYINT      NOT NULL DEFAULT 0,
    create_time    TIMESTAMP    NULL,
    update_time    TIMESTAMP    NULL,
    CONSTRAINT pk_sys_user_profile PRIMARY KEY (user_id)
);

CREATE INDEX idx_sys_user_profile_phone ON sys_user_profile (phone);
