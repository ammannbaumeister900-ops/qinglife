-- Preserve submitted originals and legacy excerpts; new reviews can edit the full public text.
ALTER TABLE ql_camp_reflection ADD COLUMN display_note varchar(150) DEFAULT NULL AFTER display_excerpt;
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.13','心声完整展示内容与一次审核展示');
