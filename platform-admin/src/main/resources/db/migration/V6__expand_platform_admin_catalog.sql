-- platform-admin 通用能力超集。只演进平台表，不包含具体业务字典或 OAuth 编码。

ALTER TABLE sys_role ADD COLUMN remark VARCHAR(500) NULL;
ALTER TABLE sys_role ADD COLUMN sort_no INT NOT NULL DEFAULT 0;

ALTER TABLE sys_dict ADD COLUMN parent_id BIGINT NULL;
ALTER TABLE sys_dict ADD COLUMN remark VARCHAR(500) NULL;
CREATE INDEX idx_sys_dict_parent_id ON sys_dict (parent_id);

-- config_key 仍是跨分类稳定标识，保留原有全局唯一约束；category 只用于展示和筛选。
ALTER TABLE sys_config ADD COLUMN category VARCHAR(64) NULL;
ALTER TABLE sys_config ADD COLUMN title VARCHAR(200) NULL;
ALTER TABLE sys_config ADD COLUMN sort_no INT NOT NULL DEFAULT 0;
CREATE INDEX idx_sys_config_category_sort ON sys_config (category, sort_no);

ALTER TABLE sys_oper_log ADD COLUMN trace_id VARCHAR(64) NULL;
ALTER TABLE sys_oper_log ADD COLUMN group_code VARCHAR(128) NULL;
ALTER TABLE sys_oper_log ADD COLUMN repeat_count INT NOT NULL DEFAULT 1;
ALTER TABLE sys_oper_log ADD COLUMN http_status INT NULL;
CREATE INDEX idx_sys_oper_log_trace_id ON sys_oper_log (trace_id);
CREATE INDEX idx_sys_oper_log_group_code ON sys_oper_log (group_code);
