-- 轻生活后台权限管理导航 V1.3.4
-- 工作人员授权归入“权限管理”；回访与改期业务数据及接口保持不变。

INSERT INTO `sys_menu` (
  `menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,
  `is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,
  `create_by`,`create_time`,`remark`
)
VALUES (
  2291,'权限管理',0,6,'permissionManagement',NULL,
  1,1,'M','0','0','', 'lock',
  'admin',NOW(),'内部账号与业务权限配置'
)
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`), `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`), `path`=VALUES(`path`),
  `component`=VALUES(`component`), `menu_type`=VALUES(`menu_type`),
  `visible`=VALUES(`visible`), `status`=VALUES(`status`),
  `perms`=VALUES(`perms`), `icon`=VALUES(`icon`), `remark`=VALUES(`remark`);

UPDATE `sys_menu`
SET `menu_name`='内部账号管理', `parent_id`=2291, `order_num`=1,
    `path`='internalAccounts', `component`='life/staff/index',
    `menu_type`='C', `visible`='0', `status`='0',
    `perms`='life:staff:manage', `icon`='peoples',
    `remark`='绑定小程序工作人员账号并配置业务操作权限'
WHERE `menu_id`=2290;

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT `role_id`, 2291 FROM `sys_role` WHERE `role_key` IN ('admin','ql_admin');
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT `role_id`, 2290 FROM `sys_role` WHERE `role_key` IN ('admin','ql_admin');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.3.4','新增权限管理目录并将工作人员配置调整为内部账号管理')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
