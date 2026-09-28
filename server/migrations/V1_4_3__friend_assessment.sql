-- 轻友登记与历次档案 V1.4.3
-- 复用 ql_customer 唯一轻友主档；每次提交追加一条不可覆盖的 assessment。

ALTER TABLE `ql_customer`
  ADD COLUMN `height_cm` decimal(5,1) DEFAULT NULL AFTER `city`;

CREATE TABLE IF NOT EXISTS `ql_friend_assessment` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `form_version` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `weight_kg` decimal(5,1) NOT NULL,
  `clean_body_goals` json NOT NULL,
  `diet_preference` varchar(16) NOT NULL,
  `water_intake_ml` int unsigned NOT NULL,
  `wake_time` time NOT NULL,
  `sleep_time` time NOT NULL,
  `bowel_status` varchar(16) NOT NULL,
  `energy_status` varchar(16) NOT NULL,
  `exercise_status` varchar(16) NOT NULL,
  `emotional_status` json NOT NULL,
  `health_conditions` json NOT NULL,
  `other_health_condition` varchar(500) DEFAULT NULL,
  `medications` text,
  `pregnancy_status` varchar(8) DEFAULT NULL,
  `referral_source` varchar(200) NOT NULL,
  `retraining_reason` text,
  `source` varchar(32) NOT NULL DEFAULT 'mini_program',
  `client_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `request_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `submitted_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_friend_assessment_request` (`customer_id`,`client_request_id`),
  KEY `idx_ql_friend_assessment_customer_time` (`customer_id`,`submitted_at`),
  KEY `idx_ql_friend_assessment_time` (`submitted_at`),
  KEY `idx_ql_friend_assessment_diet` (`diet_preference`,`submitted_at`),
  KEY `idx_ql_friend_assessment_energy` (`energy_status`,`submitted_at`),
  KEY `idx_ql_friend_assessment_exercise` (`exercise_status`,`submitted_at`),
  CONSTRAINT `fk_ql_friend_assessment_customer` FOREIGN KEY (`customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `ck_ql_friend_assessment_weight` CHECK (`weight_kg` BETWEEN 20 AND 300),
  CONSTRAINT `ck_ql_friend_assessment_water` CHECK (`water_intake_ml` BETWEEN 0 AND 10000),
  CONSTRAINT `ck_ql_friend_assessment_diet` CHECK (`diet_preference` IN ('全素','以素为主','以荤为主','荤素各半')),
  CONSTRAINT `ck_ql_friend_assessment_bowel` CHECK (`bowel_status` IN ('2-3次/天','1次/天','便秘')),
  CONSTRAINT `ck_ql_friend_assessment_energy` CHECK (`energy_status` IN ('很好','一般','差')),
  CONSTRAINT `ck_ql_friend_assessment_exercise` CHECK (`exercise_status` IN ('不运动','偶尔运动','经常运动')),
  CONSTRAINT `ck_ql_friend_assessment_pregnancy` CHECK (`pregnancy_status` IS NULL OR `pregnancy_status` IN ('否','是')),
  CONSTRAINT `ck_ql_friend_assessment_source` CHECK (`source` IN ('mini_program','wjx_import','admin_import'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轻友历次登记；每次提交只追加不覆盖';

ALTER TABLE `ql_consent_record` DROP CHECK `ck_ql_consent_type`;
ALTER TABLE `ql_consent_record`
  ADD CONSTRAINT `ck_ql_consent_type` CHECK (`consent_type` IN ('service_required','operations_contact','ai_processing','anonymized_research','brand_reuse','sensitive_profile'));

UPDATE `sys_menu` SET `menu_name`='轻友列表', `remark`='轻友主档、历次登记与筛选'
WHERE `menu_id`=2201;

INSERT INTO `sys_menu`
(`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
VALUES
(2230,'数据统计',2054,2,'friendStatistics','life/friendStatistics/index',1,0,'C','0','0','life:assessment:statistics','chart','system',NOW(),'轻友登记基础统计'),
(2231,'登记导出',2201,4,'#','',1,0,'F','0','0','life:assessment:export','#','system',NOW(),'按当前筛选导出轻友与登记记录'),
(2232,'敏感信息查看',2201,5,'#','',1,0,'F','0','0','life:assessment:sensitive','#','system',NOW(),'查看完整手机号和健康敏感字段')
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`),`parent_id`=VALUES(`parent_id`),`order_num`=VALUES(`order_num`),
  `path`=VALUES(`path`),`component`=VALUES(`component`),`menu_type`=VALUES(`menu_type`),
  `visible`=VALUES(`visible`),`status`=VALUES(`status`),`perms`=VALUES(`perms`),
  `icon`=VALUES(`icon`),`remark`=VALUES(`remark`);

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id,m.menu_id FROM `sys_role` r JOIN `sys_menu` m ON m.menu_id IN (2230,2231,2232)
WHERE r.role_key='ql_admin';

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id,m.menu_id FROM `sys_role` r JOIN `sys_menu` m ON m.menu_id=2230
WHERE r.role_key='ql_operator';

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id,m.menu_id FROM `sys_role` r JOIN `sys_menu` m ON m.menu_id=2230
WHERE r.role_key='ql_leader';

INSERT INTO `ql_schema_migration` (`version`,`description`)
VALUES ('1.4.3','轻友登记、历次档案、统计与导出')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
