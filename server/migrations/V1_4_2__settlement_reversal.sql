-- Keep each confirmed settlement round addressable and make revocation auditable.
-- Existing records are deliberately not backfilled: older settlements and card
-- debits cannot be reliably paired with a single immutable round.

ALTER TABLE ql_registration_batch
  ADD COLUMN current_settlement_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '当前生效结算轮次；历史未关联记录保持NULL' AFTER settlement_confirmed_at,
  ADD KEY idx_ql_reg_batch_current_settlement (current_settlement_id),
  ADD CONSTRAINT fk_ql_reg_batch_current_settlement
    FOREIGN KEY (current_settlement_id) REFERENCES ql_settlement_log(id);

ALTER TABLE ql_registration
  ADD COLUMN current_settlement_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '当前生效结算轮次；历史未关联记录保持NULL' AFTER settlement_confirmed_at,
  ADD KEY idx_ql_registration_current_settlement (current_settlement_id),
  ADD CONSTRAINT fk_ql_registration_current_settlement
    FOREIGN KEY (current_settlement_id) REFERENCES ql_settlement_log(id);

-- A pass debit is tied to the exact settlement log row, allowing later rounds
-- on the same registration or batch without changing the original ledger row.
ALTER TABLE ql_pass_ledger
  ADD COLUMN settlement_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '产生本流水的结算轮次；旧记录不作推断回填' AFTER registration_batch_id,
  DROP INDEX uq_ql_pass_consume_registration,
  DROP INDEX uq_ql_pass_consume_batch,
  ADD KEY idx_ql_pass_ledger_settlement (settlement_id),
  ADD UNIQUE KEY uq_ql_pass_settlement_entry (settlement_id, entry_type),
  ADD CONSTRAINT fk_ql_pass_ledger_settlement
    FOREIGN KEY (settlement_id) REFERENCES ql_settlement_log(id);

CREATE TABLE ql_settlement_reversal (
  settlement_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  registration_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  registration_batch_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  original_final_amount decimal(12,2) NOT NULL,
  original_settlement_type varchar(16) NOT NULL,
  original_pass_account_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  original_pass_ledger_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  original_pass_units smallint unsigned DEFAULT NULL,
  pass_balance_after int DEFAULT NULL COMMENT '退回流水写入后的账户余额快照',
  reason varchar(500) NOT NULL,
  operator_id bigint NOT NULL,
  reversed_at datetime(3) NOT NULL,
  PRIMARY KEY (settlement_id),
  KEY idx_ql_settlement_reversal_registration (registration_id, reversed_at),
  KEY idx_ql_settlement_reversal_batch (registration_batch_id, reversed_at),
  CONSTRAINT fk_ql_settlement_reversal_settlement
    FOREIGN KEY (settlement_id) REFERENCES ql_settlement_log(id),
  CONSTRAINT fk_ql_settlement_reversal_registration
    FOREIGN KEY (registration_id) REFERENCES ql_registration(id),
  CONSTRAINT fk_ql_settlement_reversal_batch
    FOREIGN KEY (registration_batch_id) REFERENCES ql_registration_batch(id),
  CONSTRAINT fk_ql_settlement_reversal_account
    FOREIGN KEY (original_pass_account_id) REFERENCES ql_pass_account(id),
  CONSTRAINT fk_ql_settlement_reversal_ledger
    FOREIGN KEY (original_pass_ledger_id) REFERENCES ql_pass_ledger(id),
  CONSTRAINT fk_ql_settlement_reversal_operator
    FOREIGN KEY (operator_id) REFERENCES sys_user(user_id),
  CONSTRAINT ck_ql_settlement_reversal_owner
    CHECK ((registration_id IS NULL) <> (registration_batch_id IS NULL)),
  CONSTRAINT ck_ql_settlement_reversal_amount CHECK (original_final_amount >= 0),
  CONSTRAINT ck_ql_settlement_reversal_type
    CHECK (original_settlement_type IN ('money','pass','mixed','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='最终结算撤销审计；settlement_id主键保证每轮最多撤销一次';

-- 2400 is unused in the migration/bootstrap/seed SQL inventory. Keep this as a
-- plain INSERT so an unexpected target-schema collision aborts instead of replacing a menu.
INSERT INTO sys_menu
  (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
VALUES
  (2400,'撤销结算',2209,5,'#','',1,0,'F','0','0','life:registration:settlement:revoke','#','system',NOW(),'独立撤销权限；仅显式授予轻生活管理员')
;

-- ql_admin gets the button explicitly; ordinary business roles retain their existing menus only.
INSERT INTO sys_role_menu(role_id,menu_id)
VALUES ((SELECT role_id FROM sys_role WHERE role_key='ql_admin'),2400);

INSERT INTO ql_schema_migration(version,description)
VALUES ('1.4.2','Round-addressable settlement revocation and immutable reversal audit')
ON DUPLICATE KEY UPDATE description=VALUES(description);