CREATE TABLE IF NOT EXISTS ql_staff_access (
 app_user_id BIGINT NOT NULL PRIMARY KEY, sys_user_id BIGINT NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1, can_operate TINYINT NOT NULL DEFAULT 0,
 can_payment TINYINT NOT NULL DEFAULT 0, updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_staff_system_user (sys_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ql_staff_interview (
 id CHAR(36) NOT NULL PRIMARY KEY, customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operator_id BIGINT NOT NULL, operator_name VARCHAR(100) NOT NULL,
 content TEXT NOT NULL, request_id VARCHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_staff_interview_request(operator_id,request_id),
 KEY ix_staff_interview_customer(customer_id,created_at),
 FOREIGN KEY(customer_id) REFERENCES ql_customer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ql_staff_interview_image (
 id CHAR(36) NOT NULL PRIMARY KEY, interview_id CHAR(36) NOT NULL,
 mime_type VARCHAR(30) NOT NULL, image_data MEDIUMBLOB NOT NULL,
 FOREIGN KEY(interview_id) REFERENCES ql_staff_interview(id),
 KEY ix_staff_interview_image(interview_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ql_customer_staff_detail (
 customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 birth_month CHAR(7) DEFAULT NULL, referral_source VARCHAR(200) DEFAULT NULL,
 FOREIGN KEY(customer_id) REFERENCES ql_customer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES(2290,'工作人员配置',0,8,'staffWorkspace','life/staff/index',1,1,'C','0','0','life:staff:manage','peoples','admin',NOW());
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,2290 FROM sys_role WHERE role_key='admin';
CREATE TABLE IF NOT EXISTS ql_staff_reschedule_request (
 id CHAR(36) NOT NULL PRIMARY KEY,
 registration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(500) NOT NULL, operator_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'pending',
 resolution VARCHAR(500), handled_by BIGINT, handled_at DATETIME(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 FOREIGN KEY(target_session_id) REFERENCES ql_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
