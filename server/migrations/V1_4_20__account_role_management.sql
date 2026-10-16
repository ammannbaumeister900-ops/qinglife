-- Register pages only; do not grant new permissions to existing roles.
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 2430,'后台账号',2291,1,'/system/user','system/user/index',1,1,'C','0','0','system:user:list','user','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:user:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 2440,'角色授权',2291,2,'/system/role','system/role/index',1,1,'C','0','0','system:role:list','peoples','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:role:list');
UPDATE sys_menu SET menu_name='后台账号',parent_id=2291,order_num=1,path='/system/user',component='system/user/index',visible='0',status='0' WHERE perms='system:user:list';
UPDATE sys_menu SET menu_name='角色授权',parent_id=2291,order_num=2,path='/system/role',component='system/role/index',visible='0',status='0' WHERE perms='system:role:list';
UPDATE sys_menu SET menu_name='微信工作台绑定',order_num=3 WHERE menu_id=2290;
UPDATE sys_menu SET order_num=4 WHERE menu_id=2420;
SET @account_menu=(SELECT menu_id FROM sys_menu WHERE perms='system:user:list' LIMIT 1);
SET @role_menu=(SELECT menu_id FROM sys_menu WHERE perms='system:role:list' LIMIT 1);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time)
SELECT seed.id,seed.name,@account_menu,seed.sort,'#','F','0','0',seed.perm,'system',NOW() FROM (
 SELECT 2431 id,'账号查询' name,1 sort,'system:user:query' perm UNION ALL
 SELECT 2432,'账号新增',2,'system:user:add' UNION ALL
 SELECT 2433,'账号修改与分配角色',3,'system:user:edit' UNION ALL
 SELECT 2434,'账号删除',4,'system:user:remove' UNION ALL
 SELECT 2435,'重置密码',5,'system:user:resetPwd'
) seed WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=seed.perm);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time)
SELECT seed.id,seed.name,@role_menu,seed.sort,'#','F','0','0',seed.perm,'system',NOW() FROM (
 SELECT 2441 id,'角色查询' name,1 sort,'system:role:query' perm UNION ALL
 SELECT 2442,'角色新增',2,'system:role:add' UNION ALL
 SELECT 2443,'角色修改与授权',3,'system:role:edit' UNION ALL
 SELECT 2444,'角色删除',4,'system:role:remove'
) seed WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=seed.perm);
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.20','注册后台账号与角色授权页面，不增加现有角色权限')
ON DUPLICATE KEY UPDATE description=VALUES(description);