-- 轻生活后台两级导航调整 V1.1.3
-- 保留旧页面和权限记录，仅调整可见目录；轻友档案作为用户中心唯一入口。

-- 原生产后台的四个业务目录直接作为一级菜单。
UPDATE `sys_menu` SET `menu_name`='用户中心', `order_num`=1, `path`='customerCenter', `icon`='peoples'
WHERE `menu_id`=2054;
UPDATE `sys_menu` SET `menu_name`='内容发布', `order_num`=3, `path`='contentPublishing', `icon`='documentation'
WHERE `menu_id`=2055;
UPDATE `sys_menu` SET `menu_name`='轻友动态', `order_num`=4, `path`='customerMoments', `icon`='message'
WHERE `menu_id`=2056;
UPDATE `sys_menu` SET `menu_name`='其他配置', `order_num`=5, `path`='otherSettings', `icon`='setting'
WHERE `menu_id`=2057;

-- 将原“轻生活业务”改为活动运营，只承载期次与报名。
UPDATE `sys_menu`
SET `menu_name`='活动运营', `order_num`=2, `path`='activityOperations', `icon`='date',
    `remark`='期次、报名与线下付款运营'
WHERE `menu_id`=2200;
UPDATE `sys_menu` SET `parent_id`=2200, `order_num`=1 WHERE `menu_id`=2205;
UPDATE `sys_menu` SET `parent_id`=2200, `order_num`=2 WHERE `menu_id`=2209;

-- 轻友档案并入用户中心，原注册用户信息入口隐藏，避免同一用户出现两套入口。
UPDATE `sys_menu` SET `parent_id`=2054, `order_num`=1, `path`='customer', `menu_name`='轻友档案'
WHERE `menu_id`=2201;
UPDATE `sys_menu` SET `visible`='1' WHERE `menu_id`=2036;

-- 轻生活内部角色补齐五个一级业务目录及可见页面权限。
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.menu_id IN (
  2054,2201,2202,2203,2204,
  2200,2205,2206,2207,2208,2209,2210,2211,2212,2213,
  2055,2012,2000,2018,
  2056,2048,2030,2024,2070,2076,
  2057,2006,2042
)
WHERE r.role_key IN ('ql_admin','ql_operator');

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.1.3','后台导航扁平化为两级并合并轻友档案与注册用户入口')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
