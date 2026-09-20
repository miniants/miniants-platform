ALTER TABLE sys_oper_log ADD COLUMN event_type VARCHAR(32) NULL;

CREATE INDEX idx_sys_oper_log_event_type ON sys_oper_log (event_type);
