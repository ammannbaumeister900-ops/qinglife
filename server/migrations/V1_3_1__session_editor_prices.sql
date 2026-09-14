-- Additive upgrade. Existing session prices and order totals are not changed.
ALTER TABLE ql_session
  ADD COLUMN province varchar(64) DEFAULT NULL COMMENT '省份',
  ADD COLUMN returning_price decimal(10,2) NOT NULL DEFAULT 2500.00 COMMENT '本人完成过至少一期的老轻友价格',
  ALTER COLUMN standard_price SET DEFAULT 3800.00;
ALTER TABLE ql_registration
  ADD COLUMN unit_price decimal(10,2) DEFAULT NULL COMMENT '报名时每位参与人的价格快照';
-- Old mini-program orders used one unit price for all participants.
UPDATE ql_registration r JOIN ql_registration_batch b ON b.id=r.batch_id
SET r.unit_price=b.payable_amount/b.participant_count
WHERE b.source='mini_program' AND b.participant_count>0 AND r.unit_price IS NULL;
