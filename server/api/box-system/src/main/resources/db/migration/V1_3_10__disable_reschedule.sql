-- V1.3.10: 本期停用改期；按退款/撤销付款后重新报名处理。
-- 只停用入口和权限，历史申请记录保留用于审计。
UPDATE sys_menu SET status='1', visible='1', remark='本期停用；请先退款或撤销付款，再另外报名' WHERE menu_id IN (2293,2294);
INSERT INTO ql_schema_migration(version,description) VALUES('1.3.10','停用改期审核；改为退款后重新报名')
ON DUPLICATE KEY UPDATE description=VALUES(description);
