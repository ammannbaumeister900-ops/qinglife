-- Additive options and personnel identity. Existing sessions keep their values.
ALTER TABLE ql_session ADD COLUMN guide_user_ids JSON NULL AFTER leader_name;
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
SELECT '期次名称','ql_session_name','0','system',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='ql_session_name');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
SELECT '期次公开地点','ql_session_venue','0','system',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='ql_session_venue');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 1,'生机清体营','生机清体营','ql_session_name','Y','0','system',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='ql_session_name' AND dict_value='生机清体营');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 1,'上海市安亭驿岛酒店','上海市安亭驿岛酒店','ql_session_venue','Y','0','system',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='ql_session_venue' AND dict_value='上海市安亭驿岛酒店');
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.15','期次选项字典及导游人员ID');
