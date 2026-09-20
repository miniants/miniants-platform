-- 证件摘要唯一：并发 verifyOrCreate 依赖数据库约束；NULL 摘要多行仍合法（未验真占位）。
DROP INDEX IF EXISTS idx_sys_person_lookup;
ALTER TABLE sys_person ADD CONSTRAINT uk_sys_person_lookup UNIQUE (id_lookup_digest);
