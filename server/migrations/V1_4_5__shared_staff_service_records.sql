-- 全体员工共享现有服务回访记录与附件 V1.4.5。
-- 仅补齐读取入口；不增加授权管理、导出、收款或结算动作。
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id,m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.perms='life:serviceRecord:list'
  OR m.menu_id IN (SELECT parent_id FROM `sys_menu` WHERE perms='life:serviceRecord:list')
WHERE r.role_key IN ('ql_admin','ql_operator','ql_leader','ql_finance');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.4.5','全部员工可查看现有服务回访记录和附件')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
