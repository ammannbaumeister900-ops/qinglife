-- Complete legacy Qinglife business navigation for fresh databases.
-- The legacy bootstrap intentionally contains DDL only. Later migrations update
-- these menu ids, so fresh acceptance databases must seed the non-sensitive
-- navigation reference rows before those updates run.

INSERT INTO `sys_menu`
(`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
VALUES
  (2054,'用户中心',0,1,'customerCenter',NULL,1,0,'M','0','0',NULL,'peoples','system',NOW(),'轻友档案与服务记录'),
  (2055,'内容发布',0,3,'contentPublishing',NULL,1,0,'M','0','0',NULL,'documentation','system',NOW(),'轻读文章与内容配置'),
  (2056,'轻友动态',0,4,'customerMoments',NULL,1,0,'M','0','0',NULL,'message','system',NOW(),'历史动态查看与管理'),
  (2057,'其他配置',0,5,'otherSettings',NULL,1,0,'M','0','0',NULL,'setting','system',NOW(),'联系信息与消息配置'),
  (2000,'用户收藏文章',2055,2,'collect','life/collect/index',1,0,'C','0','0','life:collect:list','star','system',NOW(),'用户收藏文章'),
  (2006,'联系信息',2057,1,'contact','life/contact/index',1,0,'C','0','0','life:contact:list','phone','system',NOW(),'客服联系信息'),
  (2012,'文章管理',2055,1,'essay','life/essay/index',1,0,'C','0','0','life:essay:list','education','system',NOW(),'轻读文章管理'),
  (2018,'文章标签',2055,3,'label','life/label/index',1,0,'C','0','0','life:label:list','form','system',NOW(),'轻读文章标签'),
  (2024,'动态标签',2056,3,'tag','life/tag/index',1,0,'C','0','0','life:tag:list','star','system',NOW(),'历史动态标签'),
  (2030,'动态评论',2056,2,'userComment','life/userComment/index',1,0,'C','0','0','life:userComment:list','message','system',NOW(),'历史动态评论'),
  (2042,'用户消息',2057,2,'userMessage','life/userMessage/index',1,0,'C','0','0','life:userMessage:list','message','system',NOW(),'历史用户消息'),
  (2048,'用户动态',2056,1,'userPublish','life/userPublish/index',1,0,'C','0','0','life:userPublish:list','list','system',NOW(),'历史用户动态'),
  (2070,'动态模板',2056,4,'publishTemplate','life/publishTemplate/index',1,0,'C','0','0','life:publishTemplate:list','edit','system',NOW(),'历史动态模板'),
  (2076,'动态模板项目',2056,5,'publishTemplateProject','life/publishTemplateProject/index',1,0,'C','0','0','life:publishTemplateProject:list','list','system',NOW(),'历史动态模板项目')
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`), `parent_id`=VALUES(`parent_id`),
  `order_num`=VALUES(`order_num`), `path`=VALUES(`path`),
  `component`=VALUES(`component`), `menu_type`=VALUES(`menu_type`),
  `visible`=VALUES(`visible`), `status`=VALUES(`status`),
  `perms`=VALUES(`perms`), `icon`=VALUES(`icon`), `remark`=VALUES(`remark`);

-- Existing upgraded databases already have the internal roles when this new
-- migration is introduced. Fresh databases receive the same grants again from
-- V1_1_3 after the roles are created.
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id,m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.menu_id IN
  (2054,2055,2056,2057,2000,2006,2012,2018,2024,2030,2042,2048,2070,2076)
WHERE r.role_key IN ('ql_admin','ql_operator');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.1.0.1','新环境完整后台业务导航基线，不包含账号或业务数据')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
