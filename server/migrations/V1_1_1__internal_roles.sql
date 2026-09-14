-- 轻生活内部角色默认值 V1.1.1
-- 所有内部角色的数据范围均为全部数据（data_scope=1）。
-- 角色仍通过菜单/按钮权限区分可执行动作，但不限制可查看的期次或业务行。

UPDATE `sys_config`
SET `config_value`='true', `update_by`='system', `update_time`=NOW()
WHERE `config_key`='sys.account.captchaOnOff';

INSERT INTO `sys_role`
  (`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`remark`)
SELECT '轻生活管理员','ql_admin',10,'1',1,1,'0','0','system',NOW(),'全量数据；系统配置与全部业务动作'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_key`='ql_admin');

INSERT INTO `sys_role`
  (`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`remark`)
SELECT '轻生活运营','ql_operator',20,'1',1,1,'0','0','system',NOW(),'全量数据；日常报名、轻友、回访与付款登记'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_key`='ql_operator');

INSERT INTO `sys_role`
  (`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`remark`)
SELECT '轻生活带领人','ql_leader',30,'1',1,1,'0','0','system',NOW(),'全量数据；活动履约与服务记录'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_key`='ql_leader');

INSERT INTO `sys_role`
  (`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`remark`)
SELECT '轻生活财务','ql_finance',40,'1',1,1,'0','0','system',NOW(),'全量数据；交易核对与退款动作按按钮权限控制'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_key`='ql_finance');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.1.1','内部角色全量数据范围默认值')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
