-- M2 phase 2: person-only refund requests, reservations, real payments and attribution.
-- No existing cash, settlement, registration status or role grants are rewritten.
CREATE TABLE ql_refund_request (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 registration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 requested_amount DECIMAL(12,2) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'requested',
 revision INT NOT NULL DEFAULT 0,
 reason VARCHAR(500) NOT NULL,
 requested_by BIGINT NOT NULL,
 requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 closed_at DATETIME(3) NULL,
 UNIQUE KEY uq_m2_refund_request_op(operation_id),
 CONSTRAINT ck_m2_refund_request_amount CHECK(requested_amount>0),
 CONSTRAINT ck_m2_refund_request_status CHECK(status IN ('requested','processing','completed','cancelled','failed','reconciliation_required')),
 CONSTRAINT fk_m2_refund_request_reg FOREIGN KEY(registration_id) REFERENCES ql_registration(id),
 CONSTRAINT fk_m2_refund_request_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id),
 CONSTRAINT fk_m2_refund_request_operator FOREIGN KEY(requested_by) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_refund_reservation (
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reserved_amount DECIMAL(12,2) NOT NULL,
 PRIMARY KEY(request_id,allocation_id),
 CONSTRAINT ck_m2_reservation_amount CHECK(reserved_amount>0),
 CONSTRAINT fk_m2_reservation_request FOREIGN KEY(request_id) REFERENCES ql_refund_request(id),
 CONSTRAINT fk_m2_reservation_allocation FOREIGN KEY(allocation_id) REFERENCES ql_receipt_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_refund_payment (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actual_amount DECIMAL(12,2) NOT NULL,
 payment_method VARCHAR(24) NOT NULL,
 external_reference VARCHAR(100) NULL,
 evidence VARCHAR(500) NOT NULL,
 occurred_at DATETIME(3) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'recorded_unreconciled',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_m2_refund_payment_op(operation_id),
 UNIQUE KEY uq_m2_refund_payment_request(request_id),
 UNIQUE KEY uq_m2_refund_external(payment_method,external_reference),
 CONSTRAINT ck_m2_refund_payment_amount CHECK(actual_amount>0),
 CONSTRAINT ck_m2_refund_payment_status CHECK(status IN ('recorded_unreconciled','applied')),
 CONSTRAINT fk_m2_refund_payment_req FOREIGN KEY(request_id) REFERENCES ql_refund_request(id),
 CONSTRAINT fk_m2_refund_payment_op FOREIGN KEY(operation_id) REFERENCES ql_finance_operation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ql_refund_attribution (
 payment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 PRIMARY KEY(payment_id,allocation_id),
 CONSTRAINT ck_m2_refund_attribution_amount CHECK(amount>0),
 CONSTRAINT fk_m2_refund_attribution_payment FOREIGN KEY(payment_id) REFERENCES ql_refund_payment(id),
 CONSTRAINT fk_m2_refund_attribution_allocation FOREIGN KEY(allocation_id) REFERENCES ql_receipt_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Actions exist independently of payment/edit; no ordinary role is granted them automatically.
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
VALUES
 (2500,'逐人退款',2209,20,'#','',1,0,'F','0','0','life:registration:refund','#','system',NOW(),'独立退款权限，须另行明确授予'),
 (2501,'逐人取消报名',2209,21,'#','',1,0,'F','0','0','life:registration:cancel','#','system',NOW(),'取消不退款，独立动作权限');
INSERT INTO ql_schema_migration(version,description) VALUES('1.4.23','Participant cash refunds and independent cancellation; write gate remains off');
