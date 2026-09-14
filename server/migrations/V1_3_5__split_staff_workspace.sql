-- 轻生活后台工作人员功能拆分 V1.3.5
-- 内部账号、服务回访、改期审核分别进入对应业务目录。

INSERT INTO `sys_menu` (
  `menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,
  `is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,
  `create_by`,`create_time`,`remark`
)
VALUES
  (2292,'服务记录',2054,2,'service-record','life/serviceRecord/index',1,1,'C','0','0','life:serviceRecord:list','documentation','admin',NOW(),'工作人员对轻友的服务回访记录'),
  (2293,'改期审核',2200,4,'reschedule-review','life/rescheduleReview/index',1,1,'C','0','0','life:reschedule:list','date','admin',NOW(),'审核工作人员提交的改期申请'),
  (2294,'改期处理',2293,1,'#','',1,1,'F','1','0','life:reschedule:edit','#','admin',NOW(),'填写改期申请的人工处理结果')
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`), `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`), `path`=VALUES(`path`),
  `component`=VALUES(`component`), `menu_type`=VALUES(`menu_type`),
  `visible`=VALUES(`visible`), `status`=VALUES(`status`),
  `perms`=VALUES(`perms`), `icon`=VALUES(`icon`), `remark`=VALUES(`remark`);

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT `role_id`,2292 FROM `sys_role`
WHERE `role_key` IN ('admin','ql_admin','ql_operator','ql_leader');
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT `role_id`,2293 FROM `sys_role`
WHERE `role_key` IN ('admin','ql_admin','ql_operator');
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT `role_id`,2294 FROM `sys_role`
WHERE `role_key` IN ('admin','ql_admin','ql_operator');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.3.5','工作人员功能拆分为内部账号管理、服务记录与改期审核')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
