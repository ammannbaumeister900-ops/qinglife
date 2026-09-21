package com.yicai.life.service;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.UploadContentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.YearMonth;

/** Staff identities are granted by an administrator, never inferred from a nickname or phone. */
@Service
@RequiredArgsConstructor
public class QlStaffWorkspaceService {
    @org.springframework.beans.factory.annotation.Autowired private QlRegistrationPolicy policy;
    private final JdbcTemplate db;
    private final QlAttendanceAudit attendanceAudit;
    private final RedisCache redis;
    private final IQlRegistrationService registrationService;
    public Map<String,Object> access(String token) {
        if(token==null||token.length()>128)throw new CustomException("请先登录",401);
        Object id=redis.getCacheObject("appToken:"+token);
        if(id==null)throw new CustomException("登录已失效",401);
        List<Map<String,Object>> rows=db.queryForList("SELECT a.*,u.nick_name AS name FROM ql_staff_access a JOIN sys_user u ON u.user_id=a.sys_user_id JOIN app_user_info w ON w.id=a.app_user_id WHERE a.app_user_id=? AND a.enabled=1 AND u.status='0' AND u.del_flag='0' AND w.status=1",id);
        if(rows.isEmpty())throw new CustomException("未获得工作人员权限",403);
        return rows.get(0);
    }
    public long operator(Map<String,Object> a){return ((Number)a.get("sys_user_id")).longValue();}
    public void permit(Map<String,Object> a,String flag){if(!Integer.valueOf(1).equals(((Number)a.get(flag)).intValue()))throw new CustomException("没有此操作权限",403);}
    public List<Map<String,Object>> people(String q,int limit){
        String pattern="%"+(q==null?"":q.trim().replace("!","!!").replace("%","!%").replace("_","!_"))+"%";
        return db.queryForList("SELECT c.id,c.nickname,c.real_name AS realName,c.city,COALESCE(d.birth_month,DATE_FORMAT(c.birth_date,'%Y-%m')) AS birthMonth,d.referral_source AS referralSource,(SELECT i.identifier_value FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL ORDER BY i.is_primary DESC LIMIT 1) AS phone FROM ql_customer c LEFT JOIN ql_customer_staff_detail d ON d.customer_id=c.id WHERE c.deleted_at IS NULL AND c.status='active' AND (c.nickname LIKE ? ESCAPE '!' OR c.real_name LIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM ql_customer_identifier i WHERE i.customer_id=c.id AND i.identifier_type='phone' AND i.valid_to IS NULL AND i.identifier_value LIKE ? ESCAPE '!')) ORDER BY c.updated_at DESC LIMIT ?",pattern,pattern,pattern,Math.min(100,Math.max(1,limit)));
    }
    public Map<String,Object> customer(String id){
        List<Map<String,Object>> rows=db.queryForList("SELECT c.id,c.nickname,c.real_name AS realName,c.city,COALESCE(d.birth_month,DATE_FORMAT(c.birth_date,'%Y-%m')) AS birthMonth,d.referral_source AS referralSource,(SELECT identifier_value FROM ql_customer_identifier WHERE customer_id=c.id AND identifier_type='phone' AND valid_to IS NULL ORDER BY is_primary DESC LIMIT 1) AS phone FROM ql_customer c LEFT JOIN ql_customer_staff_detail d ON d.customer_id=c.id WHERE c.id=? AND c.deleted_at IS NULL",id);
        if(rows.isEmpty())throw new CustomException("轻友不存在",404);return rows.get(0);
    }
    public List<Map<String,Object>> sessions(boolean history){return db.queryForList("SELECT id,session_number AS sessionNumber,name,start_date AS startDate,end_date AS endDate,city,leader_name AS leaderName,status FROM ql_session WHERE status IN "+(history?"('completed','cancelled')":"('open','in_progress')")+" ORDER BY "+(history?"start_date DESC":"CASE WHEN status='in_progress' THEN 0 ELSE 1 END,start_date ASC")+(history?" LIMIT 100":" LIMIT 1"));}
    public List<Map<String,Object>> registrations(String sessionId,String customerId){return db.queryForList("SELECT r.id,r.customer_id AS customerId,r.session_id AS sessionId,r.batch_id AS batchId,r.registration_status AS registrationStatus,r.payment_status AS paymentStatus,c.nickname,c.real_name AS realName,s.session_number AS sessionNumber,s.name AS sessionName,s.start_date AS startDate,s.end_date AS endDate,s.status AS sessionStatus FROM ql_registration r JOIN ql_customer c ON c.id=r.customer_id JOIN ql_session s ON s.id=r.session_id WHERE "+(sessionId!=null?"r.session_id=?":"r.customer_id=?")+" ORDER BY s.start_date DESC,r.created_at DESC",sessionId!=null?sessionId:customerId);}
    public List<Map<String,Object>> passes(String customerId){return db.queryForList("SELECT a.id,a.pass_type AS passType,a.status,a.valid_until AS validUntil,COALESCE(SUM(l.quantity_delta),0) AS balance FROM ql_pass_account a LEFT JOIN ql_pass_ledger l ON l.pass_account_id=a.id WHERE a.customer_id=? GROUP BY a.id,a.pass_type,a.status,a.valid_until",customerId);}
    public Map<String,Object> interviewPage(String realName, String nickname, String operatorName, int pageNum, int pageSize) {
        pageNum=Math.max(1,pageNum); pageSize=Math.max(1,Math.min(100,pageSize));
        String from=" FROM ql_staff_interview r JOIN ql_customer c ON c.id=r.customer_id WHERE LOCATE(?,COALESCE(c.real_name,''))>0 AND LOCATE(?,COALESCE(c.nickname,''))>0 AND LOCATE(?,COALESCE(r.operator_name,''))>0";
        Object[] filters={realName.trim(),nickname.trim(),operatorName.trim()};
        Long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,filters);
        List<Map<String,Object>> rows=db.queryForList("SELECT r.id,r.customer_id AS customerId,c.nickname,c.real_name AS realName,r.operator_name AS operatorName,r.content,TIMESTAMPDIFF(MICROSECOND,'1970-01-01 00:00:00',r.created_at)/1000 AS createdAt"+from+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",filters[0],filters[1],filters[2],pageSize,((long)pageNum-1)*pageSize);
        formatInterviewTimes(rows);
        attachInterviewImages(rows);
        Map<String,Object> result=new HashMap<>(); result.put("rows",rows);result.put("total",total);return result;
    }
    public List<Map<String,Object>> interviewImages(String customerId) {
        if(customerId==null || customerId.trim().isEmpty()) throw new CustomException("请选择轻友",400);
        List<Map<String,Object>> rows=db.queryForList("SELECT i.id,r.id AS interviewId,r.operator_name AS operatorName,TIMESTAMPDIFF(MICROSECOND,'1970-01-01 00:00:00',r.created_at)/1000 AS createdAt FROM ql_staff_interview_image i JOIN ql_staff_interview r ON r.id=i.interview_id WHERE r.customer_id=? ORDER BY r.created_at DESC,r.id DESC,i.id",customerId);
        formatInterviewTimes(rows); return rows;
    }
    private void formatInterviewTimes(List<Map<String,Object>> rows) {
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        for(Map<String,Object> row:rows) row.put("createdAt",format.format(new java.util.Date(((Number)row.get("createdAt")).longValue())));
    }
    private void attachInterviewImages(List<Map<String,Object>> rows) {
        if (rows.isEmpty()) return;
        List<Object> ids = new ArrayList<>();
        for (Map<String,Object> row : rows) ids.add(row.get("id"));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<String,List<Map<String,Object>>> imagesByInterview = new HashMap<>();
        for (Map<String,Object> image : db.queryForList("SELECT id,interview_id AS interviewId FROM ql_staff_interview_image WHERE interview_id IN (" + placeholders + ") ORDER BY interview_id,id", ids.toArray())) {
            String interviewId = String.valueOf(image.remove("interviewId"));
            imagesByInterview.computeIfAbsent(interviewId, key -> new ArrayList<>()).add(image);
        }
        for (Map<String,Object> row : rows) row.put("images", imagesByInterview.getOrDefault(String.valueOf(row.get("id")), Collections.emptyList()));
    }
    public List<Map<String,Object>> interviews(String customerId){List<Map<String,Object>> rows=db.queryForList("SELECT r.id,r.customer_id AS customerId,c.nickname,r.operator_name AS operatorName,r.content,TIMESTAMPDIFF(MICROSECOND,'1970-01-01 00:00:00',r.created_at)/1000 AS createdAt FROM ql_staff_interview r JOIN ql_customer c ON c.id=r.customer_id "+(customerId==null?"":"WHERE r.customer_id=? ")+"ORDER BY r.created_at DESC LIMIT 100",customerId==null?new Object[]{}:new Object[]{customerId});formatInterviewTimes(rows);attachInterviewImages(rows);return rows;}
    public static String text(Map<String,Object> body,String key,int max,boolean required){String value=Objects.toString(body.get(key),"").trim();if(value.length()>max||(required&&value.isEmpty()))throw new CustomException("请检查"+key,400);return value;}
    @Transactional
    public String addCustomer(Map<String,Object> b,long operator){String name=text(b,"nickname",50,true),real=text(b,"realName",50,true),month=text(b,"birthMonth",7,false),ref=text(b,"referralSource",200,false);if(!month.isEmpty()){try{if(YearMonth.parse(month).isAfter(YearMonth.now()))throw new IllegalArgumentException();}catch(Exception e){throw new CustomException("出生年月无效",400);}}String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_customer(id,customer_no,nickname,real_name,city,created_by,updated_by) VALUES(?,?,?,?,?,?,?)",id,"QY"+UUID.randomUUID().toString().replace("-","").substring(0,20),name,real,text(b,"city",100,false),operator,operator);db.update("INSERT INTO ql_customer_staff_detail(customer_id,birth_month,referral_source) VALUES(?,?,?)",id,month.isEmpty()?null:month,ref);return id;}
    @Transactional
    public String addInterview(Map<String,Object> b, Map<String,Object> a) {
        String customerId = text(b, "customerId", 36, true);
        String content = text(b, "content", 3000, true);
        String requestId = text(b, "requestId", 64, true);
        customer(customerId);
        List<Map<String,Object>> old = db.queryForList(
                "SELECT id FROM ql_staff_interview WHERE operator_id=? AND request_id=?", operator(a), requestId);
        if (!old.isEmpty()) return old.get(0).get("id").toString();
        Object raw = b.get("images");
        if (raw != null && !(raw instanceof List)) throw new CustomException("图片格式无效", 400);
        List<?> images = raw == null ? Collections.emptyList() : (List<?>) raw;
        if (images.size() > 6) throw new CustomException("最多6张图片", 400);
        List<byte[]> decoded = new ArrayList<>();
        List<String> mimeTypes = new ArrayList<>();
        int total = 0;
        for (Object image : images) {
            if (!(image instanceof Map)) throw new CustomException("图片格式无效", 400);
            Map<?,?> im = (Map<?,?>) image;
            String mime = Objects.toString(im.get("mime"), "");
            byte[] bytes = UploadContentValidator.decodeInterviewImage(
                    mime, Objects.toString(im.get("data"), ""));
            total += bytes.length;
            if (total > 2 * 1024 * 1024) throw new CustomException("图片合计不能超过2MB", 400);
            decoded.add(bytes);
            mimeTypes.add(mime);
        }
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO ql_staff_interview(id,customer_id,operator_id,operator_name,content,request_id,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))",
                id, customerId, operator(a), a.get("name"), content, requestId);
        for (int i = 0; i < decoded.size(); i++) {
            db.update("INSERT INTO ql_staff_interview_image(id,interview_id,mime_type,image_data) VALUES(?,?,?,?)",
                    UUID.randomUUID().toString(), id, mimeTypes.get(i), decoded.get(i));
        }
        return id;
    }
    public Map<String,Object> image(String id){List<Map<String,Object>> rows=db.queryForList("SELECT mime_type,image_data FROM ql_staff_interview_image WHERE id=?",id);if(rows.isEmpty())throw new CustomException("图片不存在",404);return rows.get(0);}
    @Transactional
    public void enroll(String customerId,String sessionId,Map<String,Object> a){
        permit(a,"can_operate"); customer(customerId);
        com.yicai.life.domain.bo.QlRegistrationBo bo=new com.yicai.life.domain.bo.QlRegistrationBo();
        bo.setCustomerId(customerId);bo.setSessionId(sessionId);bo.setRegistrationStatus("pending");bo.setRegistrationSource("web_admin");
        registrationService.insertByBo(bo,operator(a));
    }
    private Map<String,Object> lockSession(String id){List<Map<String,Object>> s=db.queryForList("SELECT * FROM ql_session WHERE id=? FOR UPDATE",id);if(s.isEmpty())throw new CustomException("期次不存在",404);return s.get(0);}
    @Transactional
    public void confirm(String id,Map<String,Object> a){
        permit(a,"can_operate");
        com.yicai.life.domain.bo.QlRegistrationBo bo=new com.yicai.life.domain.bo.QlRegistrationBo();bo.setId(id);bo.setRegistrationStatus("confirmed");
        registrationService.updateByBo(bo,operator(a));
    }
    @Transactional
    public void payment(String id,com.yicai.life.domain.bo.QlPaymentBo bo,Map<String,Object> a){
        permit(a,"can_payment");
        if(!"paid".equals(bo.getPaymentStatus()))throw new CustomException("收款更正请在后台处理",400);
        registrationService.changePayment(id,bo,operator(a));
    }
    public List<Map<String,Object>> grants(){return db.queryForList("SELECT a.*,u.nick_name AS staffName,w.nick_name AS appName FROM ql_staff_access a JOIN sys_user u ON u.user_id=a.sys_user_id JOIN app_user_info w ON w.id=a.app_user_id ORDER BY a.updated_at DESC");}
    public List<Map<String,Object>> appUsers(String q){return db.queryForList("SELECT id,nick_name AS name,last_login_time AS lastLoginTime FROM app_user_info WHERE status=1 AND (nick_name LIKE ? OR CAST(id AS CHAR)=?) ORDER BY last_login_time DESC LIMIT 30","%"+q+"%",q);}
    public List<Map<String,Object>> operators(){return db.queryForList("SELECT user_id AS id,nick_name AS name FROM sys_user WHERE status='0' AND del_flag='0' ORDER BY user_id LIMIT 200");}
    @Transactional
    public void grant(Map<String,Object> b,long operator){long app=Long.parseLong(text(b,"appUserId",20,true)),user=Long.parseLong(text(b,"sysUserId",20,true));if(db.queryForObject("SELECT COUNT(*) FROM app_user_info WHERE id=? AND status=1",Integer.class,app)!=1||db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'",Integer.class,user)!=1)throw new CustomException("账号不可用",400);db.queryForList("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE",user);if(db.queryForObject("SELECT COUNT(*) FROM ql_staff_access WHERE sys_user_id=? AND app_user_id<>?",Integer.class,user,app)>0)throw new CustomException("该工作人员已绑定其他小程序账号，请先处理原绑定",400);db.update("INSERT INTO ql_staff_access(app_user_id,sys_user_id,enabled,can_operate,can_payment,updated_by) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE sys_user_id=VALUES(sys_user_id),enabled=VALUES(enabled),can_operate=VALUES(can_operate),can_payment=VALUES(can_payment),updated_by=VALUES(updated_by)",app,user,Boolean.TRUE.equals(b.get("enabled"))?1:0,Boolean.TRUE.equals(b.get("canOperate"))?1:0,Boolean.TRUE.equals(b.get("canPayment"))?1:0,operator);}

    public List<Map<String,Object>> attendance(String registrationId){return db.queryForList("SELECT d.id AS dayId,d.day_no AS dayNo,d.activity_date AS activityDate,COALESCE(p.attendance_status,'not_arrived') AS attendanceStatus,p.checked_in_at AS checkedInAt,(d.activity_date=?) AS isToday FROM ql_registration r JOIN ql_session_day d ON d.session_id=r.session_id LEFT JOIN ql_participation_day p ON p.registration_id=r.id AND p.session_day_id=d.id WHERE r.id=? ORDER BY d.day_no",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString(),registrationId);}
    @Transactional
    public void arrive(String id,String dayId,Map<String,Object> a){
        permit(a,"can_operate"); policy.lockRegistration(id);
        com.yicai.life.domain.QlRegistration r=registrationService.getById(id);
        if(r==null||!"confirmed".equals(r.getRegistrationStatus()))throw new CustomException("仅已确认报名可登记到场",400);
        String today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        if(db.queryForObject("SELECT COUNT(*) FROM ql_session_day d JOIN ql_session s ON s.id=d.session_id WHERE d.id=? AND d.session_id=? AND d.activity_date=? AND s.status IN ('open','in_progress')",Integer.class,dayId,r.getSessionId(),today)!=1)throw new CustomException("仅可登记本期当天到场",400);
        List<Map<String,Object>> old=db.queryForList("SELECT * FROM ql_participation_day WHERE registration_id=? AND session_day_id=? FOR UPDATE",id,dayId);
        if(!old.isEmpty()&&Arrays.asList("checked_in","late","left_early").contains(old.get(0).get("attendance_status")))return;
        db.update("INSERT INTO ql_participation_day(id,registration_id,session_day_id,customer_id,attendance_status,check_in_source,checked_in_at,checked_in_by) VALUES(?,?,?,?,'checked_in','mobile_workspace',UTC_TIMESTAMP(3),?) ON DUPLICATE KEY UPDATE attendance_status='checked_in',check_in_source='mobile_workspace',checked_in_at=UTC_TIMESTAMP(3),checked_in_by=VALUES(checked_in_by),revision=revision+1",UUID.randomUUID().toString(),id,dayId,r.getCustomerId(),operator(a));
        String participationId=db.queryForObject("SELECT id FROM ql_participation_day WHERE registration_id=? AND session_day_id=?",String.class,id,dayId);
        attendanceAudit.record(participationId,old.isEmpty()?null:old.get(0),"checked_in",null,operator(a),"mobile_workspace");
    }
    @Transactional
    public void cancel(String id,Map<String,Object> a){
        permit(a,"can_operate");
        com.yicai.life.domain.bo.QlRegistrationBo bo=new com.yicai.life.domain.bo.QlRegistrationBo();bo.setId(id);bo.setRegistrationStatus("cancelled");
        registrationService.updateByBo(bo,operator(a));
    }
    public List<Map<String,Object>> rescheduleTargets(){return db.queryForList("SELECT id,name,session_number AS sessionNumber FROM ql_session WHERE status='open' ORDER BY start_date LIMIT 100");}
    @Transactional
    public void requestReschedule(String id,Map<String,Object> body,Map<String,Object> a){
        permit(a,"can_operate");String target=text(body,"targetSessionId",36,true),reason=text(body,"reason",500,true);
        com.yicai.life.domain.QlRegistration r=registrationService.getById(id);
        if(r==null||"cancelled".equals(r.getRegistrationStatus())||target.equals(r.getSessionId()))throw new CustomException("报名或目标期次无效",400);
        if(!"open".equals(lockSession(target).get("status")))throw new CustomException("目标期次未开放",400);
        if(db.queryForObject("SELECT COUNT(*) FROM ql_staff_reschedule_request WHERE registration_id=? AND status='pending'",Integer.class,id)>0)throw new CustomException("已有待处理改期申请",400);
        db.update("INSERT INTO ql_staff_reschedule_request(id,registration_id,target_session_id,reason,operator_id) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),id,target,reason,operator(a));
    }
    public List<Map<String,Object>> rescheduleRequests(){return db.queryForList("SELECT q.*,c.nickname,s.name AS originalSession,t.name AS targetSession,u.nick_name AS operatorName FROM ql_staff_reschedule_request q JOIN ql_registration r ON r.id=q.registration_id JOIN ql_customer c ON c.id=r.customer_id JOIN ql_session s ON s.id=r.session_id JOIN ql_session t ON t.id=q.target_session_id JOIN sys_user u ON u.user_id=q.operator_id ORDER BY q.created_at DESC LIMIT 100");}
    public void resolveReschedule(String id,Map<String,Object> body,long operator){String resolution=text(body,"resolution",500,true);if(db.update("UPDATE ql_staff_reschedule_request SET status='handled',resolution=?,handled_by=?,handled_at=NOW(3) WHERE id=? AND status='pending'",resolution,operator,id)!=1)throw new CustomException("申请不存在或已处理",400);}

    public List<Map<String,Object>> contacts(String customerId) {
        customer(customerId);
        return db.queryForList("SELECT id,customer_id AS customerId,channel,input_type AS inputType,content_type AS contentType,summary,occurred_at AS occurredAt,operator_id AS operatorId FROM ql_interaction WHERE customer_id=? ORDER BY occurred_at DESC,id LIMIT 100",customerId);
    }
    public List<Map<String,Object>> followUps(String customerId) {
        customer(customerId);
        return db.queryForList("SELECT id,customer_id AS customerId,source_interaction_id AS sourceInteractionId,title,task_type AS taskType,status,priority,assignee_user_id AS assigneeUserId,due_at AS dueAt,result FROM ql_follow_up_task WHERE customer_id=? ORDER BY created_at DESC,id LIMIT 100",customerId);
    }
    private String choice(Map<String,Object> body,String key,String fallback,String... allowed) {
        String value=text(body,key,32,false);if(value.isEmpty())value=fallback;
        if(!Arrays.asList(allowed).contains(value))throw new CustomException("请检查"+key,400);return value;
    }
    @Transactional
    public Map<String,Object> addContact(Map<String,Object> body,Map<String,Object> a) {
        permit(a,"can_operate");String customerId=text(body,"customerId",36,true);customer(customerId);
        String id=UUID.randomUUID().toString();
        String channel=choice(body,"channel","phone","wechat","phone","offline","voice_note","mini_program","system","other");
        String input=choice(body,"inputType","text","text");
        String content=choice(body,"contentType","staff_observation","customer_statement","objective_fact","staff_observation","staff_judgement");
        db.update("INSERT INTO ql_interaction(id,customer_id,channel,input_type,content_type,summary,occurred_at,confirmation_status,visibility_scope,sensitivity_level,operator_id,source) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3),'confirmed','internal',0,?,'mobile_workspace')",id,customerId,channel,input,content,text(body,"summary",10000,true),operator(a));
        Map<String,Object> result=new LinkedHashMap<>();result.put("interactionId",id);
        if(body.get("followUp")!=null){
            if(!(body.get("followUp") instanceof Map))throw new CustomException("跟进任务格式无效",400);
            Map<String,Object> task=new HashMap<>((Map<String,Object>)body.get("followUp"));task.put("customerId",customerId);task.put("sourceInteractionId",id);
            result.put("followUpTaskId",addFollowUp(task,a));
        }return result;
    }
    @Transactional
    public String addFollowUp(Map<String,Object> body,Map<String,Object> a) {
        permit(a,"can_operate");String customerId=text(body,"customerId",36,true);customer(customerId);
        String source=text(body,"sourceInteractionId",36,false);
        if(!source.isEmpty()&&db.queryForObject("SELECT COUNT(*) FROM ql_interaction WHERE id=? AND customer_id=?",Integer.class,source,customerId)!=1)throw new CustomException("沟通记录不属于该轻友",400);
        long assignee=operator(a);
        if(body.get("assigneeUserId")!=null)try{assignee=Long.parseLong(body.get("assigneeUserId").toString());}catch(NumberFormatException e){throw new CustomException("负责人无效",400);}
        if(db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'",Integer.class,assignee)!=1)throw new CustomException("负责人不可用",400);
        java.sql.Timestamp due=null;String dueText=text(body,"dueAt",40,false);
        if(!dueText.isEmpty())try{due=java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(dueText.replace(' ','T')));}catch(Exception e){throw new CustomException("到期时间格式无效",400);}
        if(due!=null&&due.before(new Date()))throw new CustomException("到期时间不能早于当前时间",400);
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_follow_up_task(id,customer_id,source_interaction_id,title,task_type,status,priority,assignee_user_id,due_at,created_by) VALUES(?,?,?,?,?,'pending',?,?,?,?)",id,customerId,source.isEmpty()?null:source,text(body,"title",200,true),choice(body,"taskType","call","call","wechat","visit","other"),choice(body,"priority","normal","low","normal","high","urgent"),assignee,due,operator(a));
        return id;
    }
    @Transactional
    public void transitionFollowUp(String id,Map<String,Object> body,Map<String,Object> a) {
        permit(a,"can_operate");List<Map<String,Object>> rows=db.queryForList("SELECT status,result FROM ql_follow_up_task WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new CustomException("任务不存在",404);
        String next=choice(body,"status","","in_progress","completed","skipped","failed","closed"),current=String.valueOf(rows.get(0).get("status"));
        String result=text(body,"result",1000,!"in_progress".equals(next));
        if(next.equals(current)){if(!Objects.equals(result,Objects.toString(rows.get(0).get("result"),"")))throw new CustomException("任务结果已固定",409);return;}
        if(!Arrays.asList("pending","in_progress").contains(current))throw new CustomException("已结束任务不能再次转换",400);
        db.update("UPDATE ql_follow_up_task SET status=?,result=?,completed_at=? WHERE id=?",next,result,"in_progress".equals(next)?null:new Date(),id);
    }
}
