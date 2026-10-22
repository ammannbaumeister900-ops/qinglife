-- 全体员工共享轻友敏感资料 V1.4.4。
-- 为已执行 V1.4.3 的环境追加授权；不修改历史迁移，不增加导出或结算权限。
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id,m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.perms='life:assessment:sensitive' AND m.menu_type='F'
WHERE r.role_key IN ('ql_admin','ql_operator','ql_leader','ql_finance');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.4.4','全部员工可查看轻友完整手机号、健康、用药与孕期信息')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
