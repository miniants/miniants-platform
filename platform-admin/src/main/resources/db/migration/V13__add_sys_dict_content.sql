-- 字典扩展内容（打印模板、业务附加值）。采用方已有列时跳过本脚本或保持原类型。
ALTER TABLE sys_dict ADD COLUMN content VARCHAR(4000) NULL;
