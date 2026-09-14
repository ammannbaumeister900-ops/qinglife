-- 轻生活 V1.2 小程序支撑能力增量迁移
-- 前提：已执行 V1_1_0__qinglife_core.sql
-- 原则：保留 lifestyle 旧表，新增 ql_ 业务事实；旧文章/收藏/动态继续兼容。
-- MySQL DDL 会隐式提交；必须先在克隆库验证并在生产执行前创建可恢复备份。

ALTER TABLE `ql_customer_identifier`
  DROP CHECK `ck_ql_identifier_type`,
  ADD UNIQUE KEY `uq_ql_identifier_legacy_app_user` (`legacy_app_user_id`),
  ADD CONSTRAINT `ck_ql_identifier_type`
    CHECK (`identifier_type` IN ('phone','wechat_openid','wechat_unionid','wechat_id','id_card','passport','legacy_name','other'));

ALTER TABLE `ql_registration_status_log`
  MODIFY COLUMN `operator_id` bigint DEFAULT NULL,
  ADD COLUMN `actor_customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `operator_id`,
  ADD KEY `idx_ql_reg_status_customer` (`actor_customer_id`,`changed_at`),
  ADD CONSTRAINT `fk_ql_reg_status_customer`
    FOREIGN KEY (`actor_customer_id`) REFERENCES `ql_customer` (`id`);

ALTER TABLE `ql_session`
  ADD COLUMN `intro` varchar(1000) DEFAULT NULL AFTER `name`,
  ADD COLUMN `public_venue` varchar(200) DEFAULT NULL AFTER `standard_price`,
  ADD COLUMN `registration_confirm_mode` varchar(16) NOT NULL DEFAULT 'manual' AFTER `status`,
  ADD COLUMN `registration_open_at` datetime(3) DEFAULT NULL AFTER `registration_confirm_mode`,
  ADD COLUMN `registration_close_at` datetime(3) DEFAULT NULL AFTER `registration_open_at`,
  ADD COLUMN `leader_name` varchar(100) DEFAULT NULL AFTER `registration_close_at`,
  ADD COLUMN `cancel_policy` varchar(1000) DEFAULT NULL AFTER `leader_name`,
  ADD CONSTRAINT `ck_ql_session_confirm_mode`
    CHECK (`registration_confirm_mode` IN ('manual','auto'));

CREATE TABLE `ql_session_day` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `day_no` tinyint unsigned NOT NULL,
  `activity_date` date NOT NULL,
  `start_time` time DEFAULT NULL,
  `end_time` time DEFAULT NULL,
  `theme` varchar(200) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'scheduled',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_session_day_no` (`session_id`,`day_no`),
  KEY `idx_ql_session_day_date` (`activity_date`),
  CONSTRAINT `fk_ql_session_day_session` FOREIGN KEY (`session_id`) REFERENCES `ql_session` (`id`),
  CONSTRAINT `ck_ql_session_day_no` CHECK (`day_no` BETWEEN 1 AND 31),
  CONSTRAINT `ck_ql_session_day_status` CHECK (`status` IN ('scheduled','open','closed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='期次逐日安排；三日营通常每期三条';

ALTER TABLE `ql_registration_batch`
  ADD COLUMN `order_no` varchar(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `id`,
  ADD COLUMN `payable_amount` decimal(12,2) NOT NULL DEFAULT 0.00 AFTER `participant_count`,
  ADD COLUMN `payment_status` varchar(24) NOT NULL DEFAULT 'unpaid' AFTER `payable_amount`,
  ADD COLUMN `remark` varchar(500) DEFAULT NULL AFTER `submitted_at`,
  ADD UNIQUE KEY `uq_ql_reg_batch_order_no` (`order_no`),
  ADD CONSTRAINT `ck_ql_reg_batch_amount` CHECK (`payable_amount` >= 0),
  ADD CONSTRAINT `ck_ql_reg_batch_payment`
    CHECK (`payment_status` IN ('unpaid','pending_confirmation','paid','refunded','waived'));

ALTER TABLE `ql_registration`
  ADD COLUMN `motivation` varchar(200) DEFAULT NULL AFTER `registration_source`,
  ADD COLUMN `relation_snapshot` varchar(32) DEFAULT NULL AFTER `motivation`,
  ADD COLUMN `is_minor_snapshot` tinyint(1) NOT NULL DEFAULT 0 AFTER `relation_snapshot`;

ALTER TABLE `ql_transaction`
  ADD COLUMN `registration_batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `session_id`,
  ADD KEY `idx_ql_transaction_batch` (`registration_batch_id`,`status`),
  ADD CONSTRAINT `fk_ql_transaction_batch`
    FOREIGN KEY (`registration_batch_id`) REFERENCES `ql_registration_batch` (`id`);

CREATE TABLE `ql_participation_day` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `registration_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_day_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `attendance_status` varchar(24) NOT NULL DEFAULT 'not_arrived',
  `check_in_source` varchar(24) DEFAULT NULL,
  `checked_in_at` datetime(3) DEFAULT NULL,
  `checked_in_by` bigint DEFAULT NULL,
  `change_reason` varchar(500) DEFAULT NULL,
  `revision` int NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_participation_day` (`registration_id`,`session_day_id`),
  KEY `idx_ql_participation_day_customer` (`customer_id`,`session_day_id`),
  CONSTRAINT `fk_ql_participation_day_registration` FOREIGN KEY (`registration_id`) REFERENCES `ql_registration` (`id`),
  CONSTRAINT `fk_ql_participation_day_session_day` FOREIGN KEY (`session_day_id`) REFERENCES `ql_session_day` (`id`),
  CONSTRAINT `fk_ql_participation_day_customer` FOREIGN KEY (`customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `fk_ql_participation_day_operator` FOREIGN KEY (`checked_in_by`) REFERENCES `sys_user` (`user_id`),
  CONSTRAINT `ck_ql_participation_day_status`
    CHECK (`attendance_status` IN ('not_arrived','checked_in','late','absent','left_early','cancelled')),
  CONSTRAINT `ck_ql_participation_day_source`
    CHECK (`check_in_source` IS NULL OR `check_in_source` IN ('mini_program','web_admin','mobile_h5','system'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每位参与人每个活动日的签到事实';

CREATE TABLE `ql_daily_record` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `record_date` date NOT NULL,
  `record_stage` varchar(24) NOT NULL,
  `plan_day` smallint unsigned NOT NULL DEFAULT 0,
  `choice_value` varchar(16) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `visibility_scope` varchar(16) NOT NULL DEFAULT 'private',
  `revision` int NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_daily_record_day` (`customer_id`,`record_date`,`record_stage`,`plan_day`),
  KEY `idx_ql_daily_record_customer_time` (`customer_id`,`record_date`),
  CONSTRAINT `fk_ql_daily_record_customer` FOREIGN KEY (`customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `fk_ql_daily_record_session` FOREIGN KEY (`session_id`) REFERENCES `ql_session` (`id`),
  CONSTRAINT `ck_ql_daily_record_stage` CHECK (`record_stage` IN ('refeed','habit','general')),
  CONSTRAINT `ck_ql_daily_record_choice` CHECK (`choice_value` IN ('done','light','rest')),
  CONSTRAINT `ck_ql_daily_record_visibility` CHECK (`visibility_scope` = 'private')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轻友私人每日记录；不得直接作为公开动态';

CREATE TABLE `ql_habit_plan` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `plan_length` smallint unsigned NOT NULL,
  `current_day` smallint unsigned NOT NULL DEFAULT 1,
  `status` varchar(16) NOT NULL DEFAULT 'active',
  `started_at` date NOT NULL,
  `paused_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ql_habit_plan_customer` (`customer_id`,`status`),
  CONSTRAINT `fk_ql_habit_plan_customer` FOREIGN KEY (`customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `fk_ql_habit_plan_session` FOREIGN KEY (`session_id`) REFERENCES `ql_session` (`id`),
  CONSTRAINT `ck_ql_habit_plan_length` CHECK (`plan_length` IN (14,21)),
  CONSTRAINT `ck_ql_habit_plan_status` CHECK (`status` IN ('active','paused','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='14或21日轻行动计划实例';

CREATE TABLE `ql_habit_day_record` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `habit_plan_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_day` smallint unsigned NOT NULL,
  `daily_record_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `completed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_habit_day` (`habit_plan_id`,`plan_day`),
  CONSTRAINT `fk_ql_habit_day_plan` FOREIGN KEY (`habit_plan_id`) REFERENCES `ql_habit_plan` (`id`),
  CONSTRAINT `fk_ql_habit_day_record` FOREIGN KEY (`daily_record_id`) REFERENCES `ql_daily_record` (`id`),
  CONSTRAINT `ck_ql_habit_day_status` CHECK (`status` IN ('pending','completed','skipped'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划逐日完成记录';

CREATE TABLE `ql_publication_link` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `daily_record_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `legacy_publish_id` bigint NOT NULL,
  `public_confirmed_at` datetime(3) NOT NULL,
  `moderation_status` varchar(16) NOT NULL DEFAULT 'published',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_publication_legacy` (`legacy_publish_id`),
  KEY `idx_ql_publication_customer` (`customer_id`,`created_at`),
  CONSTRAINT `fk_ql_publication_customer` FOREIGN KEY (`customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `fk_ql_publication_daily_record` FOREIGN KEY (`daily_record_id`) REFERENCES `ql_daily_record` (`id`),
  CONSTRAINT `ck_ql_publication_moderation` CHECK (`moderation_status` IN ('published','hidden','removed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私人记录与旧公开动态表之间的安全关联';

CREATE TABLE `ql_post_report` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `legacy_publish_id` bigint NOT NULL,
  `reporter_customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason_code` varchar(32) NOT NULL DEFAULT 'other',
  `reason_note` varchar(500) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `handled_by` bigint DEFAULT NULL,
  `handled_at` datetime(3) DEFAULT NULL,
  `handle_result` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_post_report_once` (`legacy_publish_id`,`reporter_customer_id`),
  KEY `idx_ql_post_report_status` (`status`,`created_at`),
  CONSTRAINT `fk_ql_post_report_customer` FOREIGN KEY (`reporter_customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `fk_ql_post_report_operator` FOREIGN KEY (`handled_by`) REFERENCES `sys_user` (`user_id`),
  CONSTRAINT `ck_ql_post_report_status` CHECK (`status` IN ('pending','processing','resolved','rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='旧动态与新版动态共用的举报处理记录';

CREATE TABLE `ql_subscription_preference` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `customer_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `template_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) NOT NULL,
  `granted_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `source` varchar(24) NOT NULL DEFAULT 'mini_program',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ql_subscription_customer_template` (`customer_id`,`template_key`),
  CONSTRAINT `fk_ql_subscription_customer` FOREIGN KEY (`customer_id`) REFERENCES `ql_customer` (`id`),
  CONSTRAINT `ck_ql_subscription_status` CHECK (`status` IN ('enabled','disabled','rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信订阅偏好；实际发送结果应另记消息日志';

-- 保持两级导航：活动签到直接位于“活动运营”下。
INSERT INTO `sys_menu`
(`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
VALUES
(2214,'活动签到',2200,3,'attendance','life/attendance/index',1,0,'C','0','0','life:attendance:list','checkbox','system',NOW(),'逐活动日签到'),
(2215,'签到查询',2214,1,'#','',1,0,'F','0','0','life:attendance:list','#','system',NOW(),''),
(2216,'签到修改',2214,2,'#','',1,0,'F','0','0','life:attendance:edit','#','system',NOW(),'后台修改需保留操作人和原因'),
(2217,'内容举报',2056,6,'report','life/report/index',1,0,'C','0','0','life:report:list','warning','system',NOW(),'轻友动态举报处理'),
(2218,'举报查询',2217,1,'#','',1,0,'F','0','0','life:report:list','#','system',NOW(),''),
(2219,'举报处理',2217,2,'#','',1,0,'F','0','0','life:report:edit','#','system',NOW(),'保留处理人、时间和结果')
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`), `parent_id`=VALUES(`parent_id`), `order_num`=VALUES(`order_num`),
  `path`=VALUES(`path`), `component`=VALUES(`component`), `menu_type`=VALUES(`menu_type`),
  `visible`=VALUES(`visible`), `status`=VALUES(`status`), `perms`=VALUES(`perms`),
  `icon`=VALUES(`icon`), `remark`=VALUES(`remark`);

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.menu_id IN (2214,2215,2216)
WHERE r.role_key IN ('ql_admin','ql_operator');

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.menu_id IN (2214,2215)
WHERE r.role_key IN ('ql_leader','ql_finance');

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.menu_id IN (2217,2218,2219)
WHERE r.role_key IN ('ql_admin','ql_operator');

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM `sys_role` r
JOIN `sys_menu` m ON m.menu_id IN (2217,2218)
WHERE r.role_key IN ('ql_leader','ql_finance');

INSERT INTO `ql_schema_migration` (`version`, `description`)
VALUES ('1.2.0', 'miniapp backend support: order, session day, attendance, private record and moderation');
