-- Optional opening attachment is private and belongs to exactly one pass account.
ALTER TABLE ql_pass_account
  ADD COLUMN image_mime varchar(30) DEFAULT NULL,
  ADD COLUMN image_data mediumblob DEFAULT NULL,
  ADD CONSTRAINT ck_ql_pass_image_pair CHECK ((image_mime IS NULL AND image_data IS NULL) OR (image_mime IS NOT NULL AND image_mime IN ('image/jpeg','image/png','image/webp') AND image_data IS NOT NULL));

-- Date-only changes must not create fake units or relax the nonzero ledger invariant.
CREATE TABLE ql_pass_validity_log (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  pass_account_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  previous_valid_from date DEFAULT NULL, previous_valid_until date DEFAULT NULL,
  valid_from date DEFAULT NULL, valid_until date DEFAULT NULL,
  reason varchar(500) NOT NULL, operator_id bigint NOT NULL,
  created_at datetime(3) NOT NULL,
  KEY idx_pass_validity_history (pass_account_id,created_at),
  CONSTRAINT fk_pass_validity_account FOREIGN KEY (pass_account_id) REFERENCES ql_pass_account(id),
  CONSTRAINT fk_pass_validity_operator FOREIGN KEY (operator_id) REFERENCES sys_user(user_id),
  CONSTRAINT ck_pass_validity_dates CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_until>=valid_from),
  CONSTRAINT ck_pass_validity_reason CHECK (CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.10','卡次开户选填备注及私有图片、有效期调整记录') ON DUPLICATE KEY UPDATE description=VALUES(description);
