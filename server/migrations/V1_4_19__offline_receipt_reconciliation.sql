-- Preserve original quotations and existing receipt history. No historical amounts are rewritten.
ALTER TABLE ql_transaction
  ADD COLUMN expected_amount decimal(12,2) DEFAULT NULL,
  ADD COLUMN final_settlement_confirmed tinyint(1) NOT NULL DEFAULT 0,
  ADD COLUMN difference_reason varchar(200) DEFAULT NULL,
  ADD COLUMN partial_payment tinyint(1) NOT NULL DEFAULT 0;
CREATE TABLE ql_offline_receipt_revision (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  transaction_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  revision int NOT NULL,
  before_value json NOT NULL,
  after_value json NOT NULL,
  reason varchar(200) NOT NULL,
  operator_id bigint NOT NULL,
  changed_at datetime(3) NOT NULL,
  UNIQUE KEY uk_receipt_revision (transaction_id,revision),
  CONSTRAINT fk_receipt_revision_transaction FOREIGN KEY (transaction_id) REFERENCES ql_transaction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
