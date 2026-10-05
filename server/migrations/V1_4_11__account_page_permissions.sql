-- Unconfigured accounts preserve role-based behavior. Grants here can only restrict it.
CREATE TABLE ql_admin_page_policy (
 user_id bigint NOT NULL PRIMARY KEY, updated_by bigint NOT NULL, updated_at datetime(3) NOT NULL,
 CONSTRAINT fk_admin_page_user FOREIGN KEY(user_id) REFERENCES sys_user(user_id),
 CONSTRAINT fk_admin_page_operator FOREIGN KEY(updated_by) REFERENCES sys_user(user_id)
) ENGINE=InnoDB;
CREATE TABLE ql_admin_page_grant (
 user_id bigint NOT NULL, menu_id bigint NOT NULL, PRIMARY KEY(user_id,menu_id),
 CONSTRAINT fk_admin_page_grant_policy FOREIGN KEY(user_id) REFERENCES ql_admin_page_policy(user_id),
 CONSTRAINT fk_admin_page_grant_menu FOREIGN KEY(menu_id) REFERENCES sys_menu(menu_id)
) ENGINE=InnoDB;
CREATE TABLE ql_admin_page_permission_log (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 user_id bigint NOT NULL, previous_inherit tinyint NOT NULL, previous_page_ids text NOT NULL,
 inherit_roles tinyint NOT NULL, page_ids text NOT NULL, operator_id bigint NOT NULL, created_at datetime(3) NOT NULL,
 KEY idx_admin_page_permission_history(user_id,created_at),
 CONSTRAINT fk_admin_page_log_user FOREIGN KEY(user_id) REFERENCES sys_user(user_id),
 CONSTRAINT fk_admin_page_log_operator FOREIGN KEY(operator_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
 VALUES (2420,'账号权限',2291,2,'accountPermissions','system/accountPermission/index',1,1,'C','0','0','system:accountPermission:list','lock','system',NOW(),'按后台账号限制角色已有页面'),
 (2421,'账号权限配置',2420,1,'#','',1,1,'F','0','0','system:accountPermission:edit','#','system',NOW(),'不增加角色操作及数据权限');
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id IN (2420,2421) WHERE r.role_key='ql_admin';
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.11','账号页面权限限制、审计及角色兼容') ON DUPLICATE KEY UPDATE description=VALUES(description);
