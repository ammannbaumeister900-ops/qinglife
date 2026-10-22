-- Stable story-tag identity; reuse existing article, label and configuration tables.
-- This migration never publishes or selects existing articles for the homepage.
INSERT INTO label(name,status,level,remark)
SELECT '轻友故事',1,0,'经轻友同意公开、由运营编辑发布的故事文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='qinglife.reading.story_label_id')
AND NOT EXISTS (SELECT 1 FROM label WHERE name='轻友故事');

INSERT INTO sys_config(config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '轻友故事标签ID','qinglife.reading.story_label_id',CAST(MIN(id) AS CHAR),'Y','migration',NOW(),'使用固定标签ID识别轻友故事；修改标签名称不影响筛选'
FROM label WHERE name='轻友故事'
HAVING MIN(id) IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='qinglife.reading.story_label_id');

INSERT INTO ql_schema_migration(version,description)
VALUES ('1.4.6','真实轻读列表与固定轻友故事标签')
ON DUPLICATE KEY UPDATE description=VALUES(description);