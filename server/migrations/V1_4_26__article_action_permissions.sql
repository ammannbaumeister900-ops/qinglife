-- Define missing article actions for explicit role assignment. No role grants.
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT 2510,'文章查询',p.menu_id,10,'#','',1,0,'F','0','0','life:essay:query','#','system',NOW(),'内容角色须另行明确授权'
FROM sys_menu p WHERE p.perms='life:essay:list' AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='life:essay:query');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT 2511,'文章新增',p.menu_id,11,'#','',1,0,'F','0','0','life:essay:add','#','system',NOW(),'内容角色须另行明确授权'
FROM sys_menu p WHERE p.perms='life:essay:list' AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='life:essay:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT 2512,'文章编辑',p.menu_id,12,'#','',1,0,'F','0','0','life:essay:edit','#','system',NOW(),'内容角色须另行明确授权'
FROM sys_menu p WHERE p.perms='life:essay:list' AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='life:essay:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT 2513,'文章删除',p.menu_id,13,'#','',1,0,'F','0','0','life:essay:remove','#','system',NOW(),'内容角色须另行明确授权'
FROM sys_menu p WHERE p.perms='life:essay:list' AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='life:essay:remove');
