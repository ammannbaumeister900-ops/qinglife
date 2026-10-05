-- Additive: historical private experience records are never copied or exposed.
CREATE TABLE ql_camp_reflection (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 customer_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 phase varchar(16) NOT NULL,
 draft_note varchar(150) NOT NULL DEFAULT '',
 shared_note varchar(150) DEFAULT NULL,
 share_consent tinyint NOT NULL DEFAULT 0,
 consent_version varchar(32) DEFAULT NULL,
 consent_at datetime(3) DEFAULT NULL,
 status varchar(16) NOT NULL DEFAULT 'private',
 display_excerpt varchar(40) DEFAULT NULL,
 sort_order int NOT NULL DEFAULT 100,
 revision int NOT NULL DEFAULT 1,
 created_at datetime(3) NOT NULL,
 updated_at datetime(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_ql_reflection_phase(registration_id,phase),
 KEY idx_ql_reflection_public(session_id,share_consent,status,sort_order),
 CONSTRAINT fk_ql_reflection_registration FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 CONSTRAINT fk_ql_reflection_customer FOREIGN KEY(customer_id) REFERENCES ql_customer(id),
 CONSTRAINT fk_ql_reflection_session FOREIGN KEY(session_id) REFERENCES ql_session(id),
 CONSTRAINT ck_ql_reflection_phase CHECK(phase IN ('before','after')),
 CONSTRAINT ck_ql_reflection_status CHECK(status IN ('private','submitted','approved','published','hidden','rejected','withdrawn')),
 CONSTRAINT ck_ql_reflection_consent CHECK(share_consent IN (0,1) AND (status NOT IN ('submitted','approved','published','hidden','rejected') OR (share_consent=1 AND shared_note IS NOT NULL AND consent_at IS NOT NULL))),
 CONSTRAINT ck_ql_reflection_order CHECK(sort_order BETWEEN 0 AND 999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ql_camp_reflection_log (
 id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reflection_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 action varchar(16) NOT NULL,
 revision int NOT NULL,
 operator_id bigint DEFAULT NULL,
 created_at datetime(3) NOT NULL,
 PRIMARY KEY(id), KEY idx_ql_reflection_log(reflection_id,created_at),
 CONSTRAINT fk_ql_reflection_log FOREIGN KEY(reflection_id) REFERENCES ql_camp_reflection(id),
 CONSTRAINT fk_ql_reflection_operator FOREIGN KEY(operator_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark) VALUES
 (2410,'轻友心声',2200,4,'campReflection','life/campReflection/index',1,0,'C','0','0','life:reflection:list','message','system',NOW(),'仅管理主动授权的期次心声'),
 (2411,'心声审核',2410,1,'#','',1,0,'F','0','0','life:reflection:review','#','system',NOW(),'审核与摘句'),
 (2412,'心声展示',2410,2,'#','',1,0,'F','0','0','life:reflection:publish','#','system',NOW(),'公开展示、排序与下架');
-- Only content operations/admin receive these new, independent permissions.
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
 SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id IN (2410,2411,2412)
 WHERE r.role_key IN ('ql_admin','ql_operator');
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.8','按期次保存私人短笺与自愿分享副本，独立审核与撤回');
