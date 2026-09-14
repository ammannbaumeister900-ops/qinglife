-- Additive migration. UTC timestamps in this log; existing event timestamps are not rewritten.
CREATE TABLE IF NOT EXISTS ql_attendance_status_log (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  participation_day_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  previous_reason VARCHAR(500) NULL,
  change_reason VARCHAR(500) NULL,
  operator_id BIGINT NOT NULL,
  record_source VARCHAR(24) NOT NULL,
  changed_at DATETIME(3) NOT NULL,
  KEY ix_attendance_log(participation_day_id,changed_at),
  CONSTRAINT fk_attendance_log_day FOREIGN KEY(participation_day_id) REFERENCES ql_participation_day(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE ql_participation_day DROP CHECK ck_ql_participation_day_source;
ALTER TABLE ql_participation_day ADD CONSTRAINT ck_ql_participation_day_source
  CHECK(check_in_source IS NULL OR check_in_source IN ('mini_program','web_admin','mobile_h5','mobile_workspace','system'));

-- Historical evidence mapping: correct display of the verified 9/12 mobile event,
-- without rewriting the preserved baseline business row or guessing from staff identity.
CREATE TABLE IF NOT EXISTS ql_attendance_origin_correction (
  participation_day_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  original_checked_in_at DATETIME(3) NOT NULL,
  original_operator_id BIGINT NOT NULL,
  original_source VARCHAR(24) NOT NULL,
  corrected_source VARCHAR(24) NOT NULL,
  evidence VARCHAR(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT IGNORE INTO ql_attendance_origin_correction
  (participation_day_id,original_checked_in_at,original_operator_id,original_source,corrected_source,evidence)
SELECT id,checked_in_at,checked_in_by,check_in_source,'mobile_workspace','2026-09-12 P0 D-003/D-004 and read-only preflight: mobile arrival stored as web_admin in UTC'
FROM ql_participation_day
WHERE id='0c88fdae-636a-45f9-a7c1-d70f1bc8751b'
  AND checked_in_at='2026-09-12 04:28:32.759' AND checked_in_by=880000016 AND check_in_source='web_admin';
