-- P0 card-pass management and truthful settlement result.
-- Balances continue to be derived from immutable ledger entries.

ALTER TABLE ql_pass_ledger
  ADD COLUMN registration_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER transaction_id,
  ADD COLUMN registration_batch_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER registration_id,
  ADD COLUMN balance_after int DEFAULT NULL COMMENT '该账户写入本流水后的余额快照' AFTER quantity_delta,
  ADD KEY idx_ql_pass_ledger_registration (registration_id),
  ADD KEY idx_ql_pass_ledger_batch (registration_batch_id),
  ADD UNIQUE KEY uq_ql_pass_consume_registration (registration_id, entry_type),
  ADD UNIQUE KEY uq_ql_pass_consume_batch (registration_batch_id, entry_type),
  ADD CONSTRAINT fk_ql_pass_ledger_registration FOREIGN KEY (registration_id) REFERENCES ql_registration(id),
  ADD CONSTRAINT fk_ql_pass_ledger_batch FOREIGN KEY (registration_batch_id) REFERENCES ql_registration_batch(id),
  ADD CONSTRAINT ck_ql_pass_ledger_owner CHECK (registration_id IS NULL OR registration_batch_id IS NULL);

ALTER TABLE ql_registration
  ADD COLUMN pass_account_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER pass_units,
  ADD CONSTRAINT fk_ql_registration_pass_account FOREIGN KEY (pass_account_id) REFERENCES ql_pass_account(id);

ALTER TABLE ql_registration_batch
  ADD COLUMN pass_account_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER pass_units,
  ADD CONSTRAINT fk_ql_reg_batch_pass_account FOREIGN KEY (pass_account_id) REFERENCES ql_pass_account(id);

ALTER TABLE ql_settlement_log
  ADD COLUMN pass_account_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER pass_units,
  ADD COLUMN pass_balance_after int DEFAULT NULL AFTER pass_account_id,
  ADD CONSTRAINT fk_ql_settlement_pass_account FOREIGN KEY (pass_account_id) REFERENCES ql_pass_account(id);

INSERT INTO sys_menu
  (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
VALUES
  (2295,'卡次管理',2200,4,'pass','life/pass/index',1,0,'C','0','0','life:pass:list','money','system',NOW(),'卡次开户、余额与不可变流水管理'),
  (2296,'卡次查询',2295,1,'#','',1,0,'F','0','0','life:pass:query','#','system',NOW(),''),
  (2297,'卡次开户',2295,2,'#','',1,0,'F','0','0','life:pass:add','#','system',NOW(),''),
  (2298,'卡次调整',2295,3,'#','',1,0,'F','0','0','life:pass:adjust','#','system',NOW(),'通过流水增减，不直接覆盖余额')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),parent_id=VALUES(parent_id),order_num=VALUES(order_num),
  path=VALUES(path),component=VALUES(component),menu_type=VALUES(menu_type),visible=VALUES(visible),
  status=VALUES(status),perms=VALUES(perms),icon=VALUES(icon),remark=VALUES(remark);

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id BETWEEN 2295 AND 2298
WHERE r.role_key IN ('admin','ql_admin','ql_operator');

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id IN (2295,2296)
WHERE r.role_key IN ('ql_leader','ql_finance');

INSERT INTO ql_schema_migration(version,description)
VALUES ('1.4.1','P0 card-pass management, atomic consumption and customer-visible result')
ON DUPLICATE KEY UPDATE description=VALUES(description);
