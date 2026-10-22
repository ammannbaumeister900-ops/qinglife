-- Additive only: old invitations remain registration links; old assessments stay unbound.
ALTER TABLE ql_invitation
  ADD COLUMN purpose varchar(24) NOT NULL DEFAULT 'registration',
  ADD COLUMN created_by_sys_user_id bigint DEFAULT NULL,
  ADD CONSTRAINT ck_ql_invitation_purpose CHECK (purpose IN ('registration','assessment'));
ALTER TABLE ql_friend_assessment
  ADD COLUMN session_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  ADD COLUMN invitation_code char(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  ADD KEY idx_ql_assessment_session (session_id,submitted_at),
  ADD CONSTRAINT fk_ql_assessment_session FOREIGN KEY (session_id) REFERENCES ql_session(id),
  ADD CONSTRAINT fk_ql_assessment_invitation FOREIGN KEY (invitation_code) REFERENCES ql_invitation(code),
  ADD CONSTRAINT ck_ql_assessment_context CHECK ((session_id IS NULL AND invitation_code IS NULL) OR (session_id IS NOT NULL AND invitation_code IS NOT NULL));
INSERT INTO ql_schema_migration(version,description) VALUES ('1.4.12','期次登记分享与本人历次档案关联');
