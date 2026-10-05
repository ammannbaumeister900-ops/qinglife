-- Original submission time remains the immutable anchor of the 48-hour amendment window.
-- Legacy records keep a NULL snapshot: their original basic profile cannot be reconstructed.
ALTER TABLE ql_customer ADD COLUMN province varchar(100) DEFAULT NULL AFTER city;
ALTER TABLE ql_friend_assessment
  ADD COLUMN profile_snapshot json DEFAULT NULL,
  ADD COLUMN revision int unsigned NOT NULL DEFAULT 1;

CREATE TABLE ql_friend_assessment_edit_log (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  assessment_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  revision_before int unsigned NOT NULL,
  client_request_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  previous_values json NOT NULL,
  edited_at datetime(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_assessment_edit_request (assessment_id,client_request_id),
  UNIQUE KEY uq_assessment_edit_revision (assessment_id,revision_before),
  CONSTRAINT fk_assessment_edit_record FOREIGN KEY (assessment_id) REFERENCES ql_friend_assessment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登记更正前值与幂等记录，敏感档案访问边界内保存';
