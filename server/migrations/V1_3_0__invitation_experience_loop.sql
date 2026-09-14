-- V1.3: invite attribution and participant-owned experience records. No legacy tables modified.
CREATE TABLE IF NOT EXISTS ql_invitation (
  code char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_customer_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  source varchar(24) NOT NULL,
  created_at datetime(3) NOT NULL,
  PRIMARY KEY (code),
  CONSTRAINT fk_ql_invitation_session FOREIGN KEY (session_id) REFERENCES ql_session(id),
  CONSTRAINT fk_ql_invitation_owner FOREIGN KEY (owner_customer_id) REFERENCES ql_customer(id),
  CONSTRAINT ck_ql_invitation_source CHECK (source IN ('staff','referral'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ql_experience_record (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  registration_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  customer_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  phase varchar(16) NOT NULL,
  node_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  energy tinyint DEFAULT NULL,
  relaxation tinyint DEFAULT NULL,
  note varchar(1000) DEFAULT NULL,
  visibility_scope varchar(16) NOT NULL DEFAULT 'private',
  created_at datetime(3) NOT NULL,
  updated_at datetime(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_ql_experience_node (registration_id,phase,node_key),
  CONSTRAINT fk_ql_experience_registration FOREIGN KEY (registration_id) REFERENCES ql_registration(id),
  CONSTRAINT fk_ql_experience_customer FOREIGN KEY (customer_id) REFERENCES ql_customer(id),
  CONSTRAINT fk_ql_experience_session FOREIGN KEY (session_id) REFERENCES ql_session(id),
  CONSTRAINT ck_ql_experience_phase CHECK (phase IN ('before','during','after')),
  CONSTRAINT ck_ql_experience_visibility CHECK (visibility_scope='private'),
  CONSTRAINT ck_ql_experience_energy CHECK (energy BETWEEN 1 AND 5),
  CONSTRAINT ck_ql_experience_relaxation CHECK (relaxation BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE ql_registration_batch ADD COLUMN request_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
INSERT INTO ql_schema_migration (version, description)
VALUES ('1.3.0', 'invitation attribution and private experience lifecycle');
