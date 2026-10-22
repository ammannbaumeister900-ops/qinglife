-- M2 phase 3: immutable operator verification and revocation; reuse existing consume/void ledger.
CREATE TABLE ql_pass_operator_verification (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_revision INT NOT NULL,
 batch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
 CONSTRAINT ck_m2_verification_revision CHECK(registration_revision>=0),
 CONSTRAINT ck_m2_verification_basis CHECK(CHAR_LENGTH(TRIM(basis))>0),
 CONSTRAINT ck_m2_verification_scope CHECK(
  (scope='self' AND holder_customer_id=beneficiary_customer_id) OR
  (scope='same_order' AND holder_customer_id<>beneficiary_customer_id)),
 CONSTRAINT fk_m2_verification_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id),
 CONSTRAINT fk_m2_verification_reg FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 CONSTRAINT fk_m2_verification_batch FOREIGN KEY(batch_id) REFERENCES ql_registration_batch(id),
 CONSTRAINT fk_m2_verification_account FOREIGN KEY(pass_account_id) REFERENCES ql_pass_account(id),
 CONSTRAINT fk_m2_verification_holder FOREIGN KEY(holder_customer_id) REFERENCES ql_customer(id),
 CONSTRAINT fk_m2_verification_beneficiary FOREIGN KEY(beneficiary_customer_id) REFERENCES ql_customer(id),
 CONSTRAINT fk_m2_verification_operator FOREIGN KEY(operator_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_pass_verification_revocation (
 verification_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(500) NOT NULL,
 operator_id BIGINT NOT NULL,
 revoked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_m2_verification_revocation_op(operation_id),
 CONSTRAINT fk_m2_verification_revocation FOREIGN KEY(verification_id) REFERENCES ql_pass_operator_verification(id),
 CONSTRAINT fk_m2_verification_revocation_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id),
 CONSTRAINT fk_m2_verification_revocation_operator FOREIGN KEY(operator_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ql_settlement_log
 ADD COLUMN pass_verification_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD CONSTRAINT fk_m2_settlement_verification FOREIGN KEY(pass_verification_id) REFERENCES ql_pass_operator_verification(id),
 ADD UNIQUE KEY uq_m2_settlement_verification(pass_verification_id);

-- No automatic grant. Existing payment, edit, adjust and revocation rights cannot substitute these actions.
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
VALUES
 (2502,'运营卡次核实',2209,22,'#','',1,0,'F','0','0','life:pass:verify','#','system',NOW(),'本人/同单代用依据核实及未使用核实的撤销'),
 (2503,'逐人卡次扣减',2209,23,'#','',1,0,'F','0','0','life:pass:consume','#','system',NOW(),'须同时具有核实及报名结算权限'),
 (2504,'原账户返卡',2209,24,'#','',1,0,'F','0','0','life:pass:restore','#','system',NOW(),'独立返还权限，不自动取消报名');
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.24','Participant pass verification consumption and original-account return; gate remains off');
