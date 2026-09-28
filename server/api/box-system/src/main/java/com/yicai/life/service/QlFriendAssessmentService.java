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
    private final RedisCache redis;
    private final QlCustomerIdentityService identities;
    private final JdbcTemplate db;

    public Map<String,Object> myProfile(String token) {
        String id=customerId(token);
        Map<String,Object> row=db.queryForMap("SELECT c.id,c.real_name AS name,c.nickname,c.birth_date AS birthDate,c.height_cm AS heightCm,c.city,"+
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
        if(db.queryForList("SELECT id FROM ql_customer WHERE id=? AND deleted_at IS NULL FOR UPDATE",customerId).isEmpty()) throw new CustomException("轻友档案不存在",404);
        String fingerprint=DigestUtil.sha256Hex(JSONUtil.toJsonStr(bo));
        List<Map<String,Object>> prior=db.queryForList("SELECT id,request_fingerprint AS fingerprint FROM ql_friend_assessment WHERE customer_id=? AND client_request_id=?",customerId,bo.getClientRequestId());
        if(!prior.isEmpty()) {
            if(!fingerprint.equals(prior.get(0).get("fingerprint"))) throw new CustomException("本次提交内容已变化，请重新提交",409);
            return result(customerId,String.valueOf(prior.get(0).get("id")),true);
        }
        Date now=new Date();
        String nickname=StrUtil.blankToDefault(StrUtil.trim(bo.getNickname()),StrUtil.trim(bo.getName()));
        db.update("UPDATE ql_customer SET real_name=?,nickname=?,birth_date=?,height_cm=?,city=?,data_confidence='verified',revision=revision+1,updated_at=? WHERE id=?",
                StrUtil.trim(bo.getName()),nickname,bo.getBirthDate(),bo.getHeightCm(),StrUtil.trim(bo.getCity()),now,customerId);
        updatePhone(customerId,bo.getPhone(),now);
        String id=UUID.randomUUID().toString();
        try {
            db.update("INSERT INTO ql_friend_assessment(id,customer_id,form_version,weight_kg,clean_body_goals,diet_preference,water_intake_ml,wake_time,sleep_time,bowel_status,energy_status,exercise_status,emotional_status,health_conditions,other_health_condition,medications,pregnancy_status,referral_source,retraining_reason,source,client_request_id,request_fingerprint,submitted_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,customerId,QlFriendAssessmentValidator.FORM_VERSION,bo.getWeightKg(),JSONUtil.toJsonStr(bo.getCleanBodyGoals()),bo.getDietPreference(),bo.getWaterIntakeMl(),
                    Time.valueOf(LocalTime.parse(bo.getWakeTime())),Time.valueOf(LocalTime.parse(bo.getSleepTime())),bo.getBowelStatus(),bo.getEnergyStatus(),bo.getExerciseStatus(),
                    JSONUtil.toJsonStr(bo.getEmotionalStatus()),JSONUtil.toJsonStr(bo.getHealthConditions()),blank(bo.getOtherHealthCondition()),blank(bo.getMedications()),
                    blank(bo.getPregnancyStatus()),StrUtil.trim(bo.getReferralSource()),blank(bo.getRetrainingReason()),"mini_program",bo.getClientRequestId(),fingerprint,now,now,now);
        } catch(DuplicateKeyException e) {
            Map<String,Object> existing=db.queryForMap("SELECT id,request_fingerprint AS fingerprint FROM ql_friend_assessment WHERE customer_id=? AND client_request_id=?",customerId,bo.getClientRequestId());
            if(!fingerprint.equals(existing.get("fingerprint"))) throw new CustomException("本次提交内容已变化，请重新提交",409);
            return result(customerId,String.valueOf(existing.get("id")),true);
        }
        db.update("INSERT INTO ql_consent_record(id,customer_id,consent_type,status,policy_version,granted_at,source,created_at) VALUES(?,?,'sensitive_profile','granted',?,?,'mini_program',?)",
                UUID.randomUUID().toString(),customerId,QlFriendAssessmentValidator.CONSENT_POLICY_VERSION,now,now);
        return result(customerId,id,false);
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
                protectedFields+",a.referral_source AS referralSource,a.retraining_reason AS retrainingReason,a.source,DATE_FORMAT(a.submitted_at,'%Y-%m-%d %H:%i:%s') AS submittedAt,"+
                "c.real_name AS name,c.nickname,c.birth_date AS birthDate,c.height_cm AS heightCm,c.city,"+phone+" AS phone "+
                "FROM ql_friend_assessment a JOIN ql_customer c ON c.id=a.customer_id WHERE a.customer_id=? ORDER BY a.submitted_at DESC,a.id DESC";
        return db.queryForList(sql,customerId);
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

    private List<Map<String,Object>> distribution(String sql,Range r){return db.queryForList(sql,r.start,r.end);}
    private Map<String,Object> result(String customerId,String id,boolean duplicate){return map("assessmentId",id,"assessmentCount",db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,customerId),"duplicate",duplicate);}
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
