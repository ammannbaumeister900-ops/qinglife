-- Preserve all historical anonymous shares and private drafts. New clients choose attribution explicitly.
ALTER TABLE ql_camp_reflection
 ADD COLUMN draft_anonymous tinyint NOT NULL DEFAULT 1 AFTER draft_note,
 ADD COLUMN share_anonymous tinyint NOT NULL DEFAULT 1 AFTER share_consent,
 ADD COLUMN shared_author varchar(50) DEFAULT NULL AFTER share_anonymous,
 ADD CONSTRAINT ck_ql_reflection_attribution CHECK (draft_anonymous IN (0,1) AND share_anonymous IN (0,1) AND (share_anonymous=1 OR (shared_author IS NOT NULL AND CHAR_LENGTH(TRIM(shared_author))>0)));
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.9','心声默认以小名投稿，支持匿名，历史分享保持匿名');
