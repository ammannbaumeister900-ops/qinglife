-- P0 handoff: truthful contact/participant identity, quote/final settlement split,
-- session presentation, featured readings and configurable support contact.

ALTER TABLE ql_registration_batch
  ADD COLUMN contact_name varchar(50) DEFAULT NULL COMMENT '本次报名主要联系人快照' AFTER submitted_by_customer_id,
  ADD COLUMN contact_phone varchar(32) DEFAULT NULL COMMENT '本次报名主要联系人电话，不作为参与人身份标识' AFTER contact_name,
  ADD COLUMN quoted_amount decimal(12,2) DEFAULT NULL COMMENT '系统按公开价计算的参考金额' AFTER participant_count,
  ADD COLUMN final_amount decimal(12,2) DEFAULT NULL COMMENT '工作人员确认的最终现金结算金额' AFTER payable_amount,
  ADD COLUMN settlement_status varchar(16) NOT NULL DEFAULT 'pending' COMMENT '最终结算确认状态' AFTER final_amount,
  ADD COLUMN settlement_type varchar(16) DEFAULT NULL COMMENT 'money/pass/mixed/other' AFTER settlement_status,
  ADD COLUMN pass_units smallint unsigned DEFAULT NULL COMMENT '本次确认使用的卡次' AFTER settlement_type,
  ADD COLUMN settlement_note varchar(500) DEFAULT NULL AFTER pass_units,
  ADD COLUMN settlement_confirmed_by bigint DEFAULT NULL AFTER settlement_note,
  ADD COLUMN settlement_confirmed_at datetime(3) DEFAULT NULL AFTER settlement_confirmed_by,
  ADD CONSTRAINT ck_ql_reg_batch_quote CHECK (quoted_amount IS NULL OR quoted_amount >= 0),
  ADD CONSTRAINT ck_ql_reg_batch_final CHECK (final_amount IS NULL OR final_amount >= 0),
  ADD CONSTRAINT ck_ql_reg_batch_settlement_status CHECK (settlement_status IN ('pending','confirmed')),
  ADD CONSTRAINT ck_ql_reg_batch_settlement_type CHECK (settlement_type IS NULL OR settlement_type IN ('money','pass','mixed','other')),
  ADD CONSTRAINT fk_ql_reg_batch_settlement_operator FOREIGN KEY (settlement_confirmed_by) REFERENCES sys_user(user_id);

ALTER TABLE ql_registration
  ADD COLUMN final_amount decimal(12,2) DEFAULT NULL COMMENT '无报名批次时工作人员确认的最终现金结算金额' AFTER unit_price,
  ADD COLUMN settlement_status varchar(16) NOT NULL DEFAULT 'pending' AFTER final_amount,
  ADD COLUMN settlement_type varchar(16) DEFAULT NULL AFTER settlement_status,
  ADD COLUMN pass_units smallint unsigned DEFAULT NULL AFTER settlement_type,
  ADD COLUMN settlement_note varchar(500) DEFAULT NULL AFTER pass_units,
  ADD COLUMN settlement_confirmed_by bigint DEFAULT NULL AFTER settlement_note,
  ADD COLUMN settlement_confirmed_at datetime(3) DEFAULT NULL AFTER settlement_confirmed_by,
  ADD CONSTRAINT ck_ql_registration_final CHECK (final_amount IS NULL OR final_amount >= 0),
  ADD CONSTRAINT ck_ql_registration_settlement_status CHECK (settlement_status IN ('pending','confirmed')),
  ADD CONSTRAINT ck_ql_registration_settlement_type CHECK (settlement_type IS NULL OR settlement_type IN ('money','pass','mixed','other')),
  ADD CONSTRAINT fk_ql_registration_settlement_operator FOREIGN KEY (settlement_confirmed_by) REFERENCES sys_user(user_id);

-- Historical payment status is retained, but an old quote is not evidence of cash received.
-- Confirm only rows with an actual paid transaction; waived or unverified rows need staff review.
UPDATE ql_registration_batch b
LEFT JOIN (
  SELECT registration_batch_id, SUM(amount) AS received_amount
  FROM ql_transaction
  WHERE registration_batch_id IS NOT NULL AND transaction_type = 'session' AND status = 'paid'
  GROUP BY registration_batch_id
) t ON t.registration_batch_id = b.id
SET b.quoted_amount = b.payable_amount,
    b.final_amount = CASE WHEN b.payment_status = 'paid' THEN t.received_amount ELSE NULL END,
    b.settlement_status = CASE WHEN b.payment_status = 'paid' AND t.received_amount IS NOT NULL THEN 'confirmed' ELSE 'pending' END,
    b.settlement_type = CASE WHEN b.payment_status = 'paid' AND t.received_amount IS NOT NULL THEN 'money' ELSE NULL END;

UPDATE ql_registration r
LEFT JOIN (
  SELECT registration_id, SUM(amount) AS received_amount
  FROM ql_transaction
  WHERE registration_id IS NOT NULL AND transaction_type = 'session' AND status = 'paid'
  GROUP BY registration_id
) t ON t.registration_id = r.id
SET r.final_amount = CASE WHEN r.batch_id IS NULL AND r.payment_status = 'paid' THEN t.received_amount ELSE NULL END,
    r.settlement_status = CASE WHEN r.batch_id IS NULL AND r.payment_status = 'paid' AND t.received_amount IS NOT NULL THEN 'confirmed' ELSE 'pending' END,
    r.settlement_type = CASE WHEN r.batch_id IS NULL AND r.payment_status = 'paid' AND t.received_amount IS NOT NULL THEN 'money' ELSE NULL END;

CREATE TABLE ql_settlement_log (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  registration_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  registration_batch_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  quoted_amount decimal(12,2) DEFAULT NULL,
  final_amount decimal(12,2) NOT NULL,
  settlement_type varchar(16) NOT NULL,
  pass_units smallint unsigned DEFAULT NULL,
  note varchar(500) DEFAULT NULL,
  operator_id bigint NOT NULL,
  confirmed_at datetime(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ql_settlement_registration (registration_id,confirmed_at),
  KEY idx_ql_settlement_batch (registration_batch_id,confirmed_at),
  CONSTRAINT fk_ql_settlement_registration FOREIGN KEY (registration_id) REFERENCES ql_registration(id),
  CONSTRAINT fk_ql_settlement_batch FOREIGN KEY (registration_batch_id) REFERENCES ql_registration_batch(id),
  CONSTRAINT fk_ql_settlement_operator FOREIGN KEY (operator_id) REFERENCES sys_user(user_id),
  CONSTRAINT ck_ql_settlement_owner CHECK ((registration_id IS NULL) <> (registration_batch_id IS NULL)),
  CONSTRAINT ck_ql_settlement_amount CHECK (final_amount >= 0),
  CONSTRAINT ck_ql_settlement_type CHECK (settlement_type IN ('money','pass','mixed','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作人员最终结算确认的不可变历史';

ALTER TABLE ql_session
  ADD COLUMN theme varchar(200) DEFAULT NULL COMMENT '本期主题，作为已发生期次的历史展示快照' AFTER name,
  ADD COLUMN cover_url varchar(500) DEFAULT NULL COMMENT '本期正式展示封面' AFTER theme;

ALTER TABLE essay
  ADD COLUMN home_featured tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否在小程序首页精选展示' AFTER status,
  ADD KEY idx_essay_home_featured (home_featured,status,order_num);

INSERT INTO sys_config
  (config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '轻生活联系人姓名','qinglife.contact.name','桃子','N','system',NOW(),'小程序“联系轻生活”展示姓名'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='qinglife.contact.name');

INSERT INTO sys_config
  (config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '轻生活联系人微信号','qinglife.contact.wechat','','N','system',NOW(),'发布前由运营填写；小程序支持一键复制'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='qinglife.contact.wechat');

INSERT INTO ql_schema_migration (version, description)
VALUES ('1.4.0', 'P0 contact participant identity, quote settlement split, session visuals and featured readings');
