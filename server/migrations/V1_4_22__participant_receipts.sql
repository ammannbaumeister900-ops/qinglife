-- M2 phase 1: additive participant receipt allocation. Existing orders remain legacy_batch.
ALTER TABLE ql_registration_batch
 ADD COLUMN settlement_mode VARCHAR(16) NOT NULL DEFAULT 'legacy_batch',
 ADD COLUMN finance_revision INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_m2_batch_mode CHECK(settlement_mode IN ('legacy_batch','participant'));

CREATE TABLE ql_finance_operation (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 action VARCHAR(32) NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operator_id BIGINT NOT NULL,
 result_json JSON NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 CONSTRAINT fk_m2_op_operator FOREIGN KEY(operator_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_receipt_allocation (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 line_no INT UNSIGNED NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 entry_type VARCHAR(16) NOT NULL,
 original_allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 reason VARCHAR(500) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_m2_allocation_op_line(operation_id,line_no),
 KEY idx_m2_allocation_receipt(receipt_id,registration_id),
 CONSTRAINT ck_m2_allocation_amount CHECK(amount>0),
 CONSTRAINT ck_m2_allocation_kind CHECK(
  (entry_type='allocate' AND original_allocation_id IS NULL) OR
  (entry_type='reverse' AND original_allocation_id IS NOT NULL)),
 CONSTRAINT fk_m2_allocation_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id),
 CONSTRAINT fk_m2_allocation_receipt FOREIGN KEY(receipt_id) REFERENCES ql_transaction(id),
 CONSTRAINT fk_m2_allocation_registration FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 CONSTRAINT fk_m2_allocation_original FOREIGN KEY(original_allocation_id) REFERENCES ql_receipt_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_participant_settlement_funding (
 settlement_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(settlement_id,allocation_id),
 UNIQUE KEY uq_m2_funding_allocation(allocation_id),
 CONSTRAINT fk_m2_funding_settlement FOREIGN KEY(settlement_id) REFERENCES ql_settlement_log(id),
 CONSTRAINT fk_m2_funding_allocation FOREIGN KEY(allocation_id) REFERENCES ql_receipt_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


INSERT INTO sys_config(config_name,config_key,config_value,config_type) VALUES('逐人资金写入','qinglife.finance.participant_write_enabled','false','N');
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.22','Participant cash allocation and independent settlement; writes disabled');
