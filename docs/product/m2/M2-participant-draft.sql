-- DRAFT ONLY: standalone isolated verification. NOT a released migration.
-- Approval must precede copying to server/migrations. Historical facts are not rewritten.
ALTER TABLE ql_registration_batch
 ADD COLUMN settlement_mode VARCHAR(16) NOT NULL DEFAULT 'legacy_batch',
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

CREATE TABLE ql_refund_request (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 registration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 requested_amount DECIMAL(12,2) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'requested',
 revision INT NOT NULL DEFAULT 0,
 reason VARCHAR(500) NOT NULL,
 requested_by BIGINT NOT NULL,
 requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 closed_at DATETIME(3) NULL,
 UNIQUE KEY uq_m2_refund_request_op(operation_id),
 CONSTRAINT ck_m2_refund_request_amount CHECK(requested_amount>0),
 CONSTRAINT ck_m2_refund_request_status CHECK(status IN ('requested','processing','completed','cancelled','failed','reconciliation_required')),
 CONSTRAINT fk_m2_refund_request_reg FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 CONSTRAINT fk_m2_refund_request_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id),
 CONSTRAINT fk_m2_refund_request_operator FOREIGN KEY(requested_by) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_refund_reservation (
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reserved_amount DECIMAL(12,2) NOT NULL,
 PRIMARY KEY(request_id,allocation_id),
 CONSTRAINT ck_m2_reservation_amount CHECK(reserved_amount>0),
 CONSTRAINT fk_m2_reservation_request FOREIGN KEY(request_id) REFERENCES ql_refund_request(id),
 CONSTRAINT fk_m2_reservation_allocation FOREIGN KEY(allocation_id) REFERENCES ql_receipt_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_refund_payment (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actual_amount DECIMAL(12,2) NOT NULL,
 payment_method VARCHAR(24) NOT NULL,
 external_reference VARCHAR(100) NULL,
 evidence VARCHAR(500) NOT NULL,
 occurred_at DATETIME(3) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'recorded_unreconciled',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_m2_refund_payment_op(operation_id),
 UNIQUE KEY uq_m2_refund_external(payment_method,external_reference),
 CONSTRAINT ck_m2_refund_payment_amount CHECK(actual_amount>0),
 CONSTRAINT ck_m2_refund_payment_status CHECK(status IN ('recorded_unreconciled','applied')),
 CONSTRAINT fk_m2_refund_payment_req FOREIGN KEY(request_id) REFERENCES ql_refund_request(id),
 CONSTRAINT fk_m2_refund_payment_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_refund_attribution (
 payment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 PRIMARY KEY(payment_id,allocation_id),
 CONSTRAINT ck_m2_refund_attribution_amount CHECK(amount>0),
 CONSTRAINT fk_m2_refund_attribution_payment FOREIGN KEY(payment_id) REFERENCES ql_refund_payment(id),
 CONSTRAINT fk_m2_refund_attribution_allocation FOREIGN KEY(allocation_id) REFERENCES ql_receipt_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_pass_operator_verification (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 batch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 pass_account_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 holder_customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 beneficiary_customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 units SMALLINT UNSIGNED NOT NULL,
 scope VARCHAR(16) NOT NULL,
 basis VARCHAR(600) NOT NULL,
 operator_id BIGINT NOT NULL,
 verified_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_m2_verification_operation(operation_id),
 CONSTRAINT ck_m2_verification_units CHECK(units>0),
 CONSTRAINT ck_m2_verification_basis CHECK(CHAR_LENGTH(TRIM(basis))>0),
 CONSTRAINT ck_m2_verification_scope CHECK(
  (scope='self' AND holder_customer_id=beneficiary_customer_id) OR
  (scope='same_order' AND batch_id IS NOT NULL AND holder_customer_id<>beneficiary_customer_id)),
 CONSTRAINT fk_m2_verification_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id),
 CONSTRAINT fk_m2_verification_reg FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 CONSTRAINT fk_m2_verification_batch FOREIGN KEY(batch_id) REFERENCES ql_registration_batch(id),
 CONSTRAINT fk_m2_verification_account FOREIGN KEY(pass_account_id) REFERENCES ql_pass_account(id),
 CONSTRAINT fk_m2_verification_holder FOREIGN KEY(holder_customer_id) REFERENCES ql_customer(id),
 CONSTRAINT fk_m2_verification_beneficiary FOREIGN KEY(beneficiary_customer_id) REFERENCES ql_customer(id),
 CONSTRAINT fk_m2_verification_operator FOREIGN KEY(operator_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ql_settlement_log
 ADD COLUMN pass_verification_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD CONSTRAINT fk_m2_settlement_verification FOREIGN KEY(pass_verification_id) REFERENCES ql_pass_operator_verification(id),
 ADD UNIQUE KEY uq_m2_settlement_verification(pass_verification_id);

INSERT INTO sys_config(config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '按人资金写入门禁','qinglife.finance.participant_write_enabled','false','N','system',NOW(),'M2草案：默认关闭，需独立审批启用'
WHERE NOT EXISTS(SELECT 1 FROM sys_config WHERE config_key='qinglife.finance.participant_write_enabled');