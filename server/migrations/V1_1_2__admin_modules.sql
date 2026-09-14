-- 原生产后台增量菜单与角色权限 V1.1.2
-- 页面继续使用原有 RuoYi 动态菜单、Element UI 和按钮权限模型。

INSERT INTO `sys_menu`
(`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
VALUES
(2200,'轻生活业务',0,1,'qinglife',NULL,1,0,'M','0','0',NULL,'peoples','system',NOW(),'轻友、期次与报名运营'),
(2201,'轻友档案',2200,1,'customer','life/customer/index',1,0,'C','0','0','life:customer:list','user','system',NOW(),'统一轻友主档'),
(2202,'轻友查询',2201,1,'#','',1,0,'F','0','0','life:customer:query','#','system',NOW(),''),
(2203,'轻友新增',2201,2,'#','',1,0,'F','0','0','life:customer:add','#','system',NOW(),''),
(2204,'轻友修改',2201,3,'#','',1,0,'F','0','0','life:customer:edit','#','system',NOW(),''),
(2205,'期次管理',2200,2,'session','life/session/index',1,0,'C','0','0','life:session:list','date','system',NOW(),'一期连续活动对应一个 Session'),
(2206,'期次查询',2205,1,'#','',1,0,'F','0','0','life:session:query','#','system',NOW(),''),
(2207,'期次新增',2205,2,'#','',1,0,'F','0','0','life:session:add','#','system',NOW(),''),
(2208,'期次修改',2205,3,'#','',1,0,'F','0','0','life:session:edit','#','system',NOW(),''),
(2209,'报名与付款',2200,3,'registration','life/registration/index',1,0,'C','0','0','life:registration:list','form','system',NOW(),'报名状态与线下付款状态独立'),
(2210,'报名查询',2209,1,'#','',1,0,'F','0','0','life:registration:query','#','system',NOW(),''),
(2211,'报名新增',2209,2,'#','',1,0,'F','0','0','life:registration:add','#','system',NOW(),''),
(2212,'报名修改',2209,3,'#','',1,0,'F','0','0','life:registration:edit','#','system',NOW(),''),
(2213,'人工付款登记',2209,4,'#','',1,0,'F','0','0','life:registration:payment','#','system',NOW(),'只登记线下付款，不发起在线支付')
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`), `parent_id`=VALUES(`parent_id`), `order_num`=VALUES(`order_num`),
  `path`=VALUES(`path`), `component`=VALUES(`component`), `menu_type`=VALUES(`menu_type`),
  `visible`=VALUES(`visible`), `status`=VALUES(`status`), `perms`=VALUES(`perms`),
  `icon`=VALUES(`icon`), `remark`=VALUES(`remark`);

-- 管理员、运营拥有当前三个模块的全部动作。
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id BETWEEN 2200 AND 2213
WHERE r.role_key IN ('ql_admin','ql_operator');

-- 带领人可查看全部业务数据，但不修改。
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (2200,2201,2202,2205,2206,2209,2210)
WHERE r.role_key='ql_leader';

-- 财务可查看全部业务数据，并登记线下付款。
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (2200,2201,2202,2205,2206,2209,2210,2213)
WHERE r.role_key='ql_finance';

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.1.2','原生产后台轻友、期次、报名与人工付款模块')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
