package com.yicai.life.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QlAttendanceAudit {
    private final JdbcTemplate db;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String id, Map<String,Object> before, String status, String reason,
                       long operatorId, String source) {
        db.update("INSERT INTO ql_attendance_status_log(id,participation_day_id,from_status,to_status,previous_reason,change_reason,operator_id,record_source,changed_at) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",
                UUID.randomUUID().toString(), id, before == null ? null : before.get("attendance_status"),
                status, before == null ? null : before.get("change_reason"), reason, operatorId, source);
    }

    public List<Map<String,Object>> history(String id) {
        return db.queryForList("SELECT l.id,l.from_status AS fromStatus,l.to_status AS toStatus,l.previous_reason AS previousReason,l.change_reason AS changeReason,l.record_source AS source,u.nick_name AS operatorName,DATE_FORMAT(CONVERT_TZ(l.changed_at,'+00:00','+08:00'),'%Y-%m-%d %H:%i:%s') AS changedAt FROM ql_attendance_status_log l LEFT JOIN sys_user u ON u.user_id=l.operator_id WHERE l.participation_day_id=? ORDER BY l.changed_at DESC,l.id", id);
    }
}
