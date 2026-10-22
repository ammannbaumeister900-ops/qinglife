-- Additive per-publication operational evidence; historical rows remain NULL.
ALTER TABLE ql_camp_reflection_log ADD COLUMN publication_review JSON NULL;
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.14','心声发布核对结论与业务依据复用既有操作日志');
