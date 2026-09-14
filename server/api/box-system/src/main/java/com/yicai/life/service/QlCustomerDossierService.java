package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only projections for the unified customer dossier. */
@Service
@RequiredArgsConstructor
public class QlCustomerDossierService {
    private final JdbcTemplate db;

    public Map<String,Object> overview(String id) {
        if(db.queryForObject("SELECT COUNT(*) FROM ql_customer WHERE id=? AND deleted_at IS NULL",Integer.class,id)==0)
            throw new CustomException("轻友档案不存在",404);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("identity",db.queryForMap("SELECT c.first_source AS firstSource,ref.nickname AS referrerName FROM ql_customer c LEFT JOIN ql_customer ref ON ref.id=c.referrer_customer_id WHERE c.id=?",id));
        out.put("identifiers",db.queryForList("SELECT identifier_type AS type,display_hint AS hint,verification_status AS verificationStatus,DATE_FORMAT(valid_from,'%Y-%m-%d') AS validFrom,DATE_FORMAT(valid_to,'%Y-%m-%d') AS validTo FROM ql_customer_identifier WHERE customer_id=? AND identifier_type IN ('phone','wechat_id') ORDER BY is_primary DESC,created_at DESC",id));
        // A multi-day session counts once; registrations and payments are not attendance.
        out.put("participations",db.queryForList("SELECT s.id AS sessionId,s.session_number AS sessionNumber,s.name AS sessionName,DATE_FORMAT(MIN(a.activityDate),'%Y-%m-%d') AS firstDate,DATE_FORMAT(MAX(a.activityDate),'%Y-%m-%d') AS lastDate FROM (SELECT d.session_id AS sessionId,d.activity_date AS activityDate FROM ql_participation_day p JOIN ql_session_day d ON d.id=p.session_day_id WHERE p.customer_id=? AND p.attendance_status IN ('checked_in','late','left_early') UNION ALL SELECT p.session_id,DATE(COALESCE(p.checked_in_at,p.completed_at,s.start_date)) FROM ql_participation p JOIN ql_session s ON s.id=p.session_id WHERE p.customer_id=? AND p.attendance_status IN ('checked_in','late','left_early','completed') AND NOT EXISTS(SELECT 1 FROM ql_participation_day pd JOIN ql_session_day sd ON sd.id=pd.session_day_id WHERE pd.customer_id=p.customer_id AND sd.session_id=p.session_id)) a JOIN ql_session s ON s.id=a.sessionId GROUP BY s.id,s.session_number,s.name ORDER BY firstDate,s.session_number",id,id));
        out.put("activities",db.queryForList("SELECT r.id,r.session_id AS sessionId,s.session_number AS sessionNumber,s.name AS sessionName,DATE_FORMAT(s.start_date,'%Y-%m-%d') AS startDate,s.status AS sessionStatus,r.registration_status AS registrationStatus,r.payment_status AS paymentStatus,DATE_FORMAT(r.registered_at,'%Y-%m-%d %H:%i:%s') AS registeredAt FROM ql_registration r JOIN ql_session s ON s.id=r.session_id WHERE r.customer_id=? ORDER BY s.start_date DESC,r.registered_at DESC",id));
        out.put("passes",db.queryForList("SELECT a.id,a.pass_type AS passType,a.status,DATE_FORMAT(a.valid_from,'%Y-%m-%d') AS validFrom,DATE_FORMAT(a.valid_until,'%Y-%m-%d') AS validUntil,COALESCE(SUM(l.quantity_delta),0) AS balance,CASE WHEN a.status='active' AND (a.valid_from IS NULL OR a.valid_from<=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00'))) AND (a.valid_until IS NULL OR a.valid_until>=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00'))) THEN 1 ELSE 0 END AS usable FROM ql_pass_account a LEFT JOIN ql_pass_ledger l ON l.pass_account_id=a.id WHERE a.customer_id=? GROUP BY a.id,a.pass_type,a.status,a.valid_from,a.valid_until ORDER BY a.created_at DESC",id));
        out.put("payments",db.queryForList("SELECT t.id,t.amount,t.payment_method AS paymentMethod,t.status,DATE_FORMAT(t.transaction_at,'%Y-%m-%d %H:%i:%s') AS occurredAt,u.nick_name AS operatorName,s.session_number AS sessionNumber,t.remark FROM ql_transaction t LEFT JOIN sys_user u ON u.user_id=t.operator_id LEFT JOIN ql_session s ON s.id=t.session_id WHERE t.customer_id=? ORDER BY t.transaction_at DESC",id));
        out.put("ledger",db.queryForList("SELECT l.id,a.pass_type AS passType,l.entry_type AS entryType,l.quantity_delta AS quantityDelta,l.reason,DATE_FORMAT(l.occurred_at,'%Y-%m-%d %H:%i:%s') AS occurredAt,u.nick_name AS operatorName,s.session_number AS sessionNumber FROM ql_pass_ledger l JOIN ql_pass_account a ON a.id=l.pass_account_id LEFT JOIN sys_user u ON u.user_id=l.operator_id LEFT JOIN ql_session s ON s.id=l.session_id WHERE a.customer_id=? ORDER BY l.occurred_at DESC",id));
        out.put("contacts",db.queryForList("SELECT i.id,i.channel,i.content_type AS contentType,i.summary,DATE_FORMAT(i.occurred_at,'%Y-%m-%d %H:%i:%s') AS occurredAt,u.nick_name AS operatorName,s.session_number AS sessionNumber FROM ql_interaction i LEFT JOIN sys_user u ON u.user_id=i.operator_id LEFT JOIN ql_session s ON s.id=i.session_id WHERE i.customer_id=? AND i.confirmation_status='confirmed' AND i.sensitivity_level=0 AND i.visibility_scope='internal' ORDER BY i.occurred_at DESC",id));
        out.put("tasks",db.queryForList("SELECT t.id,t.title,t.status,DATE_FORMAT(t.due_at,'%Y-%m-%d %H:%i:%s') AS dueAt,u.nick_name AS assigneeName,CASE WHEN t.status IN ('pending','in_progress') AND t.due_at<CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00') THEN 1 ELSE 0 END AS overdue FROM ql_follow_up_task t LEFT JOIN sys_user u ON u.user_id=t.assignee_user_id WHERE t.customer_id=? ORDER BY CASE WHEN t.status IN ('pending','in_progress') THEN 0 ELSE 1 END,t.due_at IS NULL,t.due_at,t.created_at DESC",id));
        return out;
    }
}
