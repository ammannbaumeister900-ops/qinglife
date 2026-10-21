-- Additive relaxation: a friend's own active registration may bind an assessment to its period without
-- a staff invitation. An invitation still always carries its period; existing rows already satisfy this.
ALTER TABLE ql_friend_assessment
  DROP CHECK ck_ql_assessment_context,
  ADD CONSTRAINT ck_ql_assessment_context CHECK (invitation_code IS NULL OR session_id IS NOT NULL);
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.27','轻友登记可凭本人有效报名关联期次');
