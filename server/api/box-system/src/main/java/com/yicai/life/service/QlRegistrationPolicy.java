package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Shared seat gate. Caller holds a transaction; lock session before orders and registrations. */
@Service
@RequiredArgsConstructor
public class QlRegistrationPolicy {
    private final JdbcTemplate db;
    private final QlSessionAdmissionPolicy admissionPolicy;
    public Map<String,Object> lockSession(String id) {
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM ql_session WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new CustomException("期次不存在",400);
        return rows.get(0);
    }
    public Map<String,Object> lockRegistration(String id) {
        List<Map<String,Object>> snapshot=db.queryForList("SELECT session_id FROM ql_registration WHERE id=?",id);
        if(snapshot.isEmpty())throw new CustomException("报名不存在",404);
        lockSession(String.valueOf(snapshot.get(0).get("session_id")));
        return db.queryForMap("SELECT * FROM ql_registration WHERE id=? FOR UPDATE",id);
    }
    public void admit(String sessionId,int additional,boolean newRegistration) {
        Map<String,Object> session=lockSession(sessionId);
        String status=String.valueOf(session.get("status"));
        if(newRegistration ? !"open".equals(status) : !Arrays.asList("open","in_progress").contains(status))
            throw new CustomException("当前期次不接受此报名操作",400);
        if(newRegistration) {
            admissionPolicy.requireNewRegistrationOpen(session);
        }
        int occupied=db.queryForList("SELECT id FROM ql_registration WHERE session_id=? AND registration_status IN ('pending','confirmed') ORDER BY id FOR UPDATE",sessionId).size();
        if(additional>0 && occupied+additional>((Number)session.get("capacity")).intValue())
            throw new CustomException("剩余名额不足",400);
    }
    public void transition(String id,String from,String to,String payment,String batchId) {
        if(Objects.equals(from,to)) return;
        if("cancelled".equals(from)) throw new CustomException("已取消报名不能直接恢复",400);
        if(!"unpaid".equals(payment)) throw new CustomException("请先撤销付款再更改报名状态",400);
        if(db.queryForObject("SELECT COUNT(*) FROM ql_participation_day WHERE registration_id=? AND attendance_status IN ('checked_in','late','left_early')",Integer.class,id)>0
                || db.queryForObject("SELECT COUNT(*) FROM ql_participation WHERE registration_id=? AND attendance_status IN ('checked_in','late','left_early','completed')",Integer.class,id)>0)
            throw new CustomException("已有到场记录，不能直接更改报名状态",400);
        if(batchId!=null && !batchId.isEmpty() && !"confirmed".equals(to))
            throw new CustomException("多人订单不支持单独取消或候补，请核对整单处理",400);
        if(!Arrays.asList("pending","confirmed","waitlisted").contains(from)
                || !Arrays.asList("pending","confirmed","waitlisted","cancelled").contains(to))
            throw new CustomException("报名状态转换无效",400);
    }
}
