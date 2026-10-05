package com.yicai.life.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlFriendAssessmentBo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QlFriendAssessmentService {
    private static final String APP_TOKEN_PREFIX="appToken:";
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    // DATETIME values are written in the JDBC business zone, regardless of the DB session zone.
    private static final String BUSINESS_NOW_SQL="CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00')";
    private final RedisCache redis;
    private final QlCustomerIdentityService identities;
    private final JdbcTemplate db;

    public Map<String,Object> myProfile(String token) {
        String id=customerId(token);
        Map<String,Object> row=db.queryForMap("SELECT c.id,c.real_name AS name,c.nickname,c.birth_date AS birthDate,c.height_cm AS heightCm,c.city,c.province,"+
                "(SELECT identifier_value FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL ORDER BY i.is_primary DESC,i.created_at DESC LIMIT 1) AS phone,"+
                "(SELECT weight_kg FROM ql_friend_assessment a WHERE a.customer_id=c.id ORDER BY a.submitted_at DESC,a.id DESC LIMIT 1) AS weightKg,"+
                "(SELECT COUNT(*) FROM ql_friend_assessment a WHERE a.customer_id=c.id) AS assessmentCount FROM ql_customer c WHERE c.id=? AND c.deleted_at IS NULL",id);
        return row;
    }

    public List<Map<String,Object>> mine(String token) { return history(customerId(token),true); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> submit(String token,QlFriendAssessmentBo bo) {
        QlFriendAssessmentValidator.validate(bo);
        String customerId=customerId(token);
        Map<String,Object> assessmentContext=null;
        // A successful retry remains valid after the period ends; new submissions lock period before owner.
        if (StrUtil.isNotBlank(bo.getInvitationCode()) && db.queryForList("SELECT id FROM ql_friend_assessment WHERE customer_id=? AND client_request_id=?",customerId,bo.getClientRequestId()).isEmpty()) {
            assessmentContext=assessmentContext(bo.getInvitationCode(),true);
        }
        if(db.queryForList("SELECT id FROM ql_customer WHERE id=? AND deleted_at IS NULL FOR UPDATE",customerId).isEmpty()) throw new CustomException("轻友档案不存在",404);
        String fingerprint=fingerprint(bo);
        List<Map<String,Object>> prior=db.queryForList("SELECT id,request_fingerprint AS fingerprint FROM ql_friend_assessment WHERE customer_id=? AND client_request_id=?",customerId,bo.getClientRequestId());
        if(!prior.isEmpty()) {
            if(!fingerprint.equals(prior.get(0).get("fingerprint"))) throw new CustomException("本次提交内容已变化，请重新提交",409);
            return result(customerId,String.valueOf(prior.get(0).get("id")),true);
        }
        Date now=new Date();
        String nickname=StrUtil.blankToDefault(StrUtil.trim(bo.getNickname()),StrUtil.trim(bo.getName()));
        db.update("UPDATE ql_customer SET real_name=?,nickname=?,birth_date=?,height_cm=?,city=?,province=?,data_confidence='verified',revision=revision+1,updated_at=? WHERE id=?",
                StrUtil.trim(bo.getName()),nickname,bo.getBirthDate(),bo.getHeightCm(),StrUtil.trim(bo.getCity()),blank(bo.getProvince()),now,customerId);
        updatePhone(customerId,bo.getPhone(),now);
        String id=UUID.randomUUID().toString();
        try {
            db.update("INSERT INTO ql_friend_assessment(id,customer_id,form_version,weight_kg,clean_body_goals,diet_preference,water_intake_ml,wake_time,sleep_time,bowel_status,energy_status,exercise_status,emotional_status,health_conditions,other_health_condition,medications,pregnancy_status,referral_source,retraining_reason,source,client_request_id,request_fingerprint,submitted_at,created_at,updated_at,profile_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,customerId,QlFriendAssessmentValidator.FORM_VERSION,bo.getWeightKg(),JSONUtil.toJsonStr(bo.getCleanBodyGoals()),bo.getDietPreference(),bo.getWaterIntakeMl(),
                    Time.valueOf(LocalTime.parse(bo.getWakeTime())),Time.valueOf(LocalTime.parse(bo.getSleepTime())),bo.getBowelStatus(),bo.getEnergyStatus(),bo.getExerciseStatus(),
                    JSONUtil.toJsonStr(bo.getEmotionalStatus()),JSONUtil.toJsonStr(bo.getHealthConditions()),blank(bo.getOtherHealthCondition()),blank(bo.getMedications()),
                    blank(bo.getPregnancyStatus()),StrUtil.trim(bo.getReferralSource()),blank(bo.getRetrainingReason()),"mini_program",bo.getClientRequestId(),fingerprint,now,now,now,profileSnapshot(bo));
        } catch(DuplicateKeyException e) {
            Map<String,Object> existing=db.queryForMap("SELECT id,request_fingerprint AS fingerprint FROM ql_friend_assessment WHERE customer_id=? AND client_request_id=?",customerId,bo.getClientRequestId());
            if(!fingerprint.equals(existing.get("fingerprint"))) throw new CustomException("本次提交内容已变化，请重新提交",409);
            return result(customerId,String.valueOf(existing.get("id")),true);
        }
        if (assessmentContext != null) db.update("UPDATE ql_friend_assessment SET session_id=?,invitation_code=? WHERE id=?",assessmentContext.get("sessionId"),bo.getInvitationCode(),id);
        db.update("INSERT INTO ql_consent_record(id,customer_id,consent_type,status,policy_version,granted_at,source,created_at) VALUES(?,?,'sensitive_profile','granted',?,?,'mini_program',?)",
                UUID.randomUUID().toString(),customerId,QlFriendAssessmentValidator.CONSENT_POLICY_VERSION,now,now);
        return result(customerId,id,false);
    }

    /** An amendment keeps the original record and deadline; the previous values remain auditable. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> amend(String token,String id,QlFriendAssessmentBo bo) {
        QlFriendAssessmentValidator.validate(bo);
        String customer=customerId(token);
        if(db.queryForList("SELECT id FROM ql_customer WHERE id=? AND deleted_at IS NULL FOR UPDATE",customer).isEmpty()) throw new CustomException("轻友档案不存在",404);
        List<Map<String,Object>> found=db.queryForList("SELECT a.*, (a.submitted_at<="+BUSINESS_NOW_SQL+" AND "+BUSINESS_NOW_SQL+"<DATE_ADD(a.submitted_at,INTERVAL 48 HOUR)) AS withinWindow FROM ql_friend_assessment a WHERE a.id=? AND a.customer_id=? FOR UPDATE",id,customer);
        if(found.isEmpty()) throw new CustomException("登记记录不存在",404);
        Map<String,Object> row=found.get(0);
        if (StrUtil.isNotBlank(bo.getInvitationCode()) && !bo.getInvitationCode().equals(row.get("invitation_code"))) throw new CustomException("不能更换登记所属期次",400);
        String fingerprint=fingerprint(bo);
        List<Map<String,Object>> prior=db.queryForList("SELECT request_fingerprint AS fingerprint FROM ql_friend_assessment_edit_log WHERE assessment_id=? AND client_request_id=?",id,bo.getClientRequestId());
        if(!prior.isEmpty()) {
            if(!fingerprint.equals(prior.get(0).get("fingerprint"))) throw new CustomException("修改内容已变化，请重新打开记录",409);
            return result(customer,id,true);
        }
        Object window=row.get("withinWindow");
        if(!"mini_program".equals(row.get("source")) || !(Boolean.TRUE.equals(window) || window instanceof Number && ((Number)window).intValue()==1)) throw new CustomException("已超过提交后48小时，登记记录不能修改",403);
        if(!QlFriendAssessmentValidator.FORM_VERSION.equals(row.get("form_version"))) throw new CustomException("此版本登记暂不支持修改",409);
        int revision=((Number)row.get("revision")).intValue();
        if(bo.getRevision()==null || bo.getRevision()!=revision) throw new CustomException("记录已更新，请重新打开后修改",409);
        Date now=new Date();
        row.remove("withinWindow");
        db.update("INSERT INTO ql_friend_assessment_edit_log(id,assessment_id,revision_before,client_request_id,request_fingerprint,previous_values,edited_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),id,revision,bo.getClientRequestId(),fingerprint,JSONUtil.toJsonStr(row),now);
        int updated=db.update("UPDATE ql_friend_assessment SET weight_kg=?,clean_body_goals=?,diet_preference=?,water_intake_ml=?,wake_time=?,sleep_time=?,bowel_status=?,energy_status=?,exercise_status=?,emotional_status=?,health_conditions=?,other_health_condition=?,medications=?,pregnancy_status=?,referral_source=?,retraining_reason=?,profile_snapshot=?,revision=revision+1,updated_at=? WHERE id=? AND customer_id=? AND revision=? AND submitted_at<="+BUSINESS_NOW_SQL+" AND "+BUSINESS_NOW_SQL+"<DATE_ADD(submitted_at,INTERVAL 48 HOUR)",
                bo.getWeightKg(),JSONUtil.toJsonStr(bo.getCleanBodyGoals()),bo.getDietPreference(),bo.getWaterIntakeMl(),Time.valueOf(LocalTime.parse(bo.getWakeTime())),Time.valueOf(LocalTime.parse(bo.getSleepTime())),bo.getBowelStatus(),bo.getEnergyStatus(),bo.getExerciseStatus(),JSONUtil.toJsonStr(bo.getEmotionalStatus()),JSONUtil.toJsonStr(bo.getHealthConditions()),blank(bo.getOtherHealthCondition()),blank(bo.getMedications()),blank(bo.getPregnancyStatus()),StrUtil.trim(bo.getReferralSource()),blank(bo.getRetrainingReason()),profileSnapshot(bo),now,id,customer,revision);
        if(updated!=1) throw new CustomException("修改期限已结束或记录已更新，请重新打开",409);
        String latest=db.queryForObject("SELECT id FROM ql_friend_assessment WHERE customer_id=? ORDER BY submitted_at DESC,id DESC LIMIT 1",String.class,customer);
        if(id.equals(latest)) {
            db.update("UPDATE ql_customer SET real_name=?,nickname=?,birth_date=?,height_cm=?,city=?,province=?,data_confidence='verified',revision=revision+1,updated_at=? WHERE id=?",
                    StrUtil.trim(bo.getName()),StrUtil.blankToDefault(StrUtil.trim(bo.getNickname()),StrUtil.trim(bo.getName())),bo.getBirthDate(),bo.getHeightCm(),StrUtil.trim(bo.getCity()),blank(bo.getProvince()),now,customer);
            updatePhone(customer,bo.getPhone(),now);
        }
        db.update("INSERT INTO ql_consent_record(id,customer_id,consent_type,status,policy_version,granted_at,source,created_at) VALUES(?,?,'sensitive_profile','granted',?,?,'mini_program',?)",UUID.randomUUID().toString(),customer,QlFriendAssessmentValidator.CONSENT_POLICY_VERSION,now,now);
        return result(customer,id,false);
    }

    public List<Map<String,Object>> history(String customerId,boolean sensitive) {
        String phone=sensitive
                ? "(SELECT identifier_value FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL ORDER BY i.is_primary DESC,i.created_at DESC LIMIT 1)"
                : "(SELECT display_hint FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL ORDER BY i.is_primary DESC,i.created_at DESC LIMIT 1)";
        String protectedFields=sensitive
                ? "a.health_conditions AS healthConditions,a.other_health_condition AS otherHealthCondition,a.medications,a.pregnancy_status AS pregnancyStatus"
                : "JSON_ARRAY('[需敏感信息权限]') AS healthConditions,NULL AS otherHealthCondition,NULL AS medications,NULL AS pregnancyStatus";
        String sql="SELECT a.id,a.form_version AS formVersion,a.weight_kg AS weightKg,a.clean_body_goals AS cleanBodyGoals,a.diet_preference AS dietPreference,"+
                "a.water_intake_ml AS waterIntakeMl,TIME_FORMAT(a.wake_time,'%H:%i') AS wakeTime,TIME_FORMAT(a.sleep_time,'%H:%i') AS sleepTime,"+
                "a.bowel_status AS bowelStatus,a.energy_status AS energyStatus,a.exercise_status AS exerciseStatus,a.emotional_status AS emotionalStatus,"+
                protectedFields+",a.session_id AS sessionId,s.session_number AS sessionNumber,s.name AS sessionName,a.referral_source AS referralSource,a.retraining_reason AS retrainingReason,a.source,DATE_FORMAT(a.submitted_at,'%Y-%m-%d %H:%i:%s') AS submittedAt,"+
                "c.real_name AS name,c.nickname,c.birth_date AS birthDate,c.height_cm AS heightCm,c.city,c.province,"+phone+" AS phone,a.profile_snapshot AS profileSnapshot,a.revision,"+
                "(a.source='mini_program' AND a.form_version='2026_v1' AND a.submitted_at<="+BUSINESS_NOW_SQL+" AND "+BUSINESS_NOW_SQL+"<DATE_ADD(a.submitted_at,INTERVAL 48 HOUR)) AS canEdit,DATE_FORMAT(DATE_ADD(a.submitted_at,INTERVAL 48 HOUR),'%Y-%m-%d %H:%i:%s') AS editableUntil "+
                "FROM ql_friend_assessment a JOIN ql_customer c ON c.id=a.customer_id LEFT JOIN ql_session s ON s.id=a.session_id WHERE a.customer_id=? ORDER BY a.submitted_at DESC,a.id DESC";
        List<Map<String,Object>> rows=db.queryForList(sql,customerId);
        for(Map<String,Object> row:rows) {
            Object snapshot=row.remove("profileSnapshot");
            row.put("profileSnapshotAvailable",snapshot!=null);
            if(snapshot!=null) {
                Map<String,Object> profile=JSONUtil.parseObj(snapshot.toString());
                for(String key:Arrays.asList("name","nickname","birthDate","heightCm","city","province")) row.put(key,profile.get(key));
                Object value=profile.get("phone");
                row.put("phone",sensitive?value:maskPhone(value));
            }
            Object editable=row.get("canEdit");
            row.put("canEdit",Boolean.TRUE.equals(editable) || editable instanceof Number && ((Number)editable).intValue()==1);
        }
        return rows;
    }

    /** Sharing does not grant access to submitted health information. */
    public List<Map<String,Object>> assessmentSessions() {
        return db.queryForList("SELECT id,session_number AS sessionNumber,name,DATE_FORMAT(start_date,'%Y-%m-%d') AS startDate,DATE_FORMAT(end_date,'%Y-%m-%d') AS endDate FROM ql_session WHERE status IN ('open','in_progress') AND end_date>=? ORDER BY start_date,session_number LIMIT 100",LocalDate.now(BUSINESS_ZONE).toString());
    }

    @Transactional
    public Map<String,Object> createAssessmentInvitation(String sessionId,long operator) {
        Map<String,Object> session=requireAssessmentSession(sessionId,true);
        String code=UUID.randomUUID().toString().replace("-","");
        db.update("INSERT INTO ql_invitation(code,session_id,owner_customer_id,source,purpose,created_by_sys_user_id,created_at) VALUES(?,?,NULL,'staff','assessment',?,?)",code,sessionId,operator,new Date());
        Map<String,Object> result=assessmentSessionSummary(session);
        result.put("path","/pages/friend-registration/index?invite="+code);
        return result;
    }

    public Map<String,Object> assessmentInvitation(String token,String code) {
        customerId(token);
        return assessmentContext(code,false);
    }

    private Map<String,Object> assessmentContext(String code,boolean lock) {
        if(code==null || !code.matches("[a-fA-F0-9]{32}")) throw new CustomException("登记邀请无效",404);
        List<String> sessions=db.queryForList("SELECT session_id FROM ql_invitation WHERE code=? AND purpose='assessment' AND source='staff' AND owner_customer_id IS NULL",String.class,code);
        if(sessions.isEmpty()) throw new CustomException("登记邀请无效",404);
        return assessmentSessionSummary(requireAssessmentSession(sessions.get(0),lock));
    }

    private Map<String,Object> requireAssessmentSession(String id,boolean lock) {
        List<Map<String,Object>> rows=db.queryForList("SELECT id,session_number,name,end_date,status FROM ql_session WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new CustomException("期次不存在",404);
        Map<String,Object> session=rows.get(0);
        if(!Arrays.asList("open","in_progress").contains(session.get("status")) || LocalDate.parse(String.valueOf(session.get("end_date")).substring(0,10)).isBefore(LocalDate.now(BUSINESS_ZONE))) throw new CustomException("本期登记已结束，请联系运营人员",400);
        return session;
    }

    private Map<String,Object> assessmentSessionSummary(Map<String,Object> row) {
        return map("sessionId",row.get("id"),"sessionNumber",row.get("session_number"),"sessionName",row.get("name"));
    }

    public List<Map<String,Object>> statisticsSessions() {
        return db.queryForList("SELECT id,session_number AS sessionNumber,name," +
                "DATE_FORMAT(registration_open_at,'%Y-%m-%d') AS registrationStartDate," +
                "DATE_FORMAT(end_date,'%Y-%m-%d') AS endDate FROM ql_session ORDER BY session_number DESC LIMIT 1000");
    }

    public Map<String,Object> statistics(String startDate,String endDate) {
        Range r=range(startDate,endDate);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("range",map("startDate",r.start.toLocalDateTime().toLocalDate().toString(),"endDate",r.end.toLocalDateTime().toLocalDate().minusDays(1).toString()));
        out.put("metrics",db.queryForMap("SELECT (SELECT COUNT(*) FROM ql_customer WHERE deleted_at IS NULL) AS friendTotal,"+
                "(SELECT COUNT(*) FROM ql_customer WHERE deleted_at IS NULL AND created_at>=? AND created_at<?) AS newFriends,"+
                "COUNT(DISTINCT customer_id) AS assessmentPeople,COUNT(*) AS assessmentCount,"+
                "COUNT(*)-COUNT(DISTINCT customer_id) AS repeatAssessments,"+
                "(SELECT COUNT(*) FROM (SELECT customer_id FROM ql_friend_assessment WHERE submitted_at>=? AND submitted_at<? GROUP BY customer_id HAVING COUNT(*)>=2) x) AS retrainingFriends "+
                "FROM ql_friend_assessment WHERE submitted_at>=? AND submitted_at<?",r.start,r.end,r.start,r.end,r.start,r.end));
        out.put("cleanBodyGoals",distribution("SELECT jt.value label,COUNT(DISTINCT a.customer_id) value FROM ql_friend_assessment a JOIN JSON_TABLE(a.clean_body_goals,'$[*]' COLUMNS(value VARCHAR(32) PATH '$')) jt WHERE a.submitted_at>=? AND a.submitted_at<? GROUP BY jt.value ORDER BY value DESC",r));
        out.put("dietPreference",distribution("SELECT diet_preference label,COUNT(*) value FROM ql_friend_assessment WHERE submitted_at>=? AND submitted_at<? GROUP BY diet_preference ORDER BY value DESC",r));
        out.put("energyStatus",distribution("SELECT energy_status label,COUNT(*) value FROM ql_friend_assessment WHERE submitted_at>=? AND submitted_at<? GROUP BY energy_status ORDER BY value DESC",r));
        out.put("exerciseStatus",distribution("SELECT exercise_status label,COUNT(*) value FROM ql_friend_assessment WHERE submitted_at>=? AND submitted_at<? GROUP BY exercise_status ORDER BY value DESC",r));
        out.put("emotionalStatus",distribution("SELECT jt.value label,COUNT(DISTINCT a.customer_id) value FROM ql_friend_assessment a JOIN JSON_TABLE(a.emotional_status,'$[*]' COLUMNS(value VARCHAR(32) PATH '$')) jt WHERE a.submitted_at>=? AND a.submitted_at<? GROUP BY jt.value ORDER BY value DESC",r));
        out.put("healthTop10",distribution("SELECT jt.value label,COUNT(*) value FROM ql_friend_assessment a JOIN JSON_TABLE(a.health_conditions,'$[*]' COLUMNS(value VARCHAR(32) PATH '$')) jt WHERE a.submitted_at>=? AND a.submitted_at<? AND jt.value<>'无' GROUP BY jt.value ORDER BY value DESC,label LIMIT 10",r));
        List<Map<String,Object>> cities=db.queryForList("SELECT c.city label,COUNT(DISTINCT c.id) value FROM ql_customer c JOIN ql_friend_assessment a ON a.customer_id=c.id WHERE a.submitted_at>=? AND a.submitted_at<? GROUP BY c.city ORDER BY value DESC,label",r.start,r.end);
        if(cities.size()>10){long other=0;for(int i=10;i<cities.size();i++)other+=((Number)cities.get(i).get("value")).longValue();cities=new ArrayList<>(cities.subList(0,10));cities.add(map("label","其他","value",other));}
        out.put("cities",cities);
        return out;
    }

    private static String fingerprint(QlFriendAssessmentBo bo) {
        cn.hutool.json.JSONObject payload=JSONUtil.parseObj(bo);
        // Preserve the original pre-amendment request fingerprint for legacy clients.
        if(bo.getProvince()==null) payload.remove("province");
        if(bo.getRevision()==null) payload.remove("revision");
        if(bo.getInvitationCode()==null) payload.remove("invitationCode");
        return DigestUtil.sha256Hex(payload.toString());
    }
    private static String profileSnapshot(QlFriendAssessmentBo bo) {
        return JSONUtil.toJsonStr(map("name",StrUtil.trim(bo.getName()),"nickname",StrUtil.blankToDefault(StrUtil.trim(bo.getNickname()),StrUtil.trim(bo.getName())),"birthDate",java.time.Instant.ofEpochMilli(bo.getBirthDate().getTime()).atZone(BUSINESS_ZONE).toLocalDate().toString(),"heightCm",bo.getHeightCm(),"city",StrUtil.trim(bo.getCity()),"province",blank(bo.getProvince()),"phone",bo.getPhone().replaceAll("\\D","")));
    }
    private static String maskPhone(Object raw) {String value=Objects.toString(raw,"");return value.length()>=7?value.substring(0,3)+"****"+value.substring(value.length()-4):"***";}
    private List<Map<String,Object>> distribution(String sql,Range r){return db.queryForList(sql,r.start,r.end);}
    private Map<String,Object> result(String customerId,String id,boolean duplicate){
        Map<String,Object> value=db.queryForMap("SELECT id AS assessmentId,revision,(source='mini_program' AND submitted_at<="+BUSINESS_NOW_SQL+" AND "+BUSINESS_NOW_SQL+"<DATE_ADD(submitted_at,INTERVAL 48 HOUR)) AS canEdit,DATE_FORMAT(submitted_at,'%Y-%m-%d %H:%i:%s') AS submittedAt,DATE_FORMAT(DATE_ADD(submitted_at,INTERVAL 48 HOUR),'%Y-%m-%d %H:%i:%s') AS editableUntil FROM ql_friend_assessment WHERE id=? AND customer_id=?",id,customerId);
        value.put("assessmentCount",db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,customerId));value.put("duplicate",duplicate);return value;
    }
    private void updatePhone(String customerId,String raw,Date now){
        String normalized=raw.replaceAll("\\D","");String hash=DigestUtil.sha256Hex(normalized);String hint=normalized.length()>=7?normalized.substring(0,3)+"****"+normalized.substring(normalized.length()-4):"***";
        db.update("UPDATE ql_customer_identifier SET valid_to=?,updated_at=? WHERE customer_id=? AND identifier_type='phone' AND valid_to IS NULL AND normalized_hash<>?",now,now,customerId,hash);
        int restored=db.update("UPDATE ql_customer_identifier SET identifier_value=?,display_hint=?,is_primary=1,verification_status='verified',valid_to=NULL,updated_at=? WHERE customer_id=? AND identifier_type='phone' AND normalized_hash=?",normalized,hint,now,customerId,hash);
        if(restored==0)db.update("INSERT INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,display_hint,is_primary,verification_status,valid_from,created_at,updated_at) VALUES(?,?,'phone',?,?,?,1,'verified',?,?,?)",UUID.randomUUID().toString(),customerId,normalized,hash,hint,now,now,now);
    }
    private String customerId(String token){if(StrUtil.isBlank(token))throw new CustomException("请先登录",401);Object raw=redis.getCacheObject(APP_TOKEN_PREFIX+token);if(raw==null)throw new CustomException("登录已失效，请重新登录",401);try{return identities.resolve(Long.valueOf(String.valueOf(raw)));}catch(NumberFormatException e){throw new CustomException("登录信息无效",401);}}
    private Range range(String start,String end){LocalDate e=StrUtil.isBlank(end)?LocalDate.now(BUSINESS_ZONE):LocalDate.parse(end);LocalDate s=StrUtil.isBlank(start)?e.minusDays(29):LocalDate.parse(start);if(s.isAfter(e)||s.isBefore(e.minusYears(5)))throw new CustomException("统计时间范围无效",400);return new Range(Timestamp.valueOf(s.atStartOfDay()),Timestamp.valueOf(e.plusDays(1).atStartOfDay()));}
    private static String blank(String value){return StrUtil.isBlank(value)?null:value.trim();}
    private static Map<String,Object> map(Object... values){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put(String.valueOf(values[i]),values[i+1]);return m;}
    private static final class Range{final Timestamp start,end;Range(Timestamp start,Timestamp end){this.start=start;this.end=end;}}
}
