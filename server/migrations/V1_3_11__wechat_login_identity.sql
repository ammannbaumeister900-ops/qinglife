-- Run against an isolated copy first. Existing duplicate non-NULL open_id values
-- make this statement fail; never auto-merge accounts or discard historical records.
-- Preflight: SELECT BINARY open_id, COUNT(*) FROM app_user_info
--            WHERE open_id IS NOT NULL GROUP BY BINARY open_id HAVING COUNT(*) > 1;
-- Preserve the original 50-character nullable column; WeChat IDs are case-sensitive.
-- Required before deploying QlWechatLoginService. MySQL 8 atomic DDL fails closed.
ALTER TABLE app_user_info
  MODIFY COLUMN open_id VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'openid',
  ADD UNIQUE KEY uq_app_user_open_id (open_id);
