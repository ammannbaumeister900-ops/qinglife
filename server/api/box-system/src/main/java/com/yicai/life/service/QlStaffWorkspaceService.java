package com.yicai.life.service;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
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
        for(Map<String,Object> row:rows) row.put("images",db.queryForList("SELECT id FROM ql_staff_interview_image WHERE interview_id=? ORDER BY id",row.get("id")));
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
    public List<Map<String,Object>> interviews(String customerId){List<Map<String,Object>> rows=db.queryForList("SELECT r.id,r.customer_id AS customerId,c.nickname,r.operator_name AS operatorName,r.content,TIMESTAMPDIFF(MICROSECOND,'1970-01-01 00:00:00',r.created_at)/1000 AS createdAt FROM ql_staff_interview r JOIN ql_customer c ON c.id=r.customer_id "+(customerId==null?"":"WHERE r.customer_id=? ")+"ORDER BY r.created_at DESC LIMIT 100",customerId==null?new Object[]{}:new Object[]{customerId});java.text.SimpleDateFormat displayTime=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");displayTime.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));for(Map<String,Object> r:rows){r.put("createdAt",displayTime.format(new java.util.Date(((Number)r.get("createdAt")).longValue())));r.put("images",db.queryForList("SELECT id FROM ql_staff_interview_image WHERE interview_id=?",r.get("id")));}return rows;}
    public static String text(Map<String,Object> body,String key,int max,boolean required){String value=Objects.toString(body.get(key),"").trim();if(value.length()>max||(required&&value.isEmpty()))throw new CustomException("请检查"+key,400);return value;}
    @Transactional
    public String addCustomer(Map<String,Object> b,long operator){String name=text(b,"nickname",50,true),real=text(b,"realName",50,true),month=text(b,"birthMonth",7,false),ref=text(b,"referralSource",200,false);if(!month.isEmpty()){try{if(YearMonth.parse(month).isAfter(YearMonth.now()))throw new IllegalArgumentException();}catch(Exception e){throw new CustomException("出生年月无效",400);}}String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_customer(id,customer_no,nickname,real_name,city,created_by,updated_by) VALUES(?,?,?,?,?,?,?)",id,"QY"+UUID.randomUUID().toString().replace("-","").substring(0,20),name,real,text(b,"city",100,false),operator,operator);db.update("INSERT INTO ql_customer_staff_detail(customer_id,birth_month,referral_source) VALUES(?,?,?)",id,month.isEmpty()?null:month,ref);return id;}
    @Transactional
    public String addInterview(Map<String,Object> b,Map<String,Object> a){String customerId=text(b,"customerId",36,true),content=text(b,"content",3000,true),requestId=text(b,"requestId",64,true);customer(customerId);List<Map<String,Object>> old=db.queryForList("SELECT id FROM ql_staff_interview WHERE operator_id=? AND request_id=?",operator(a),requestId);if(!old.isEmpty())return old.get(0).get("id").toString();Object raw=b.get("images");List<?> images=raw instanceof List?(List<?>)raw:Collections.emptyList();if(images.size()>6)throw new CustomException("最多6张图片",400);String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_staff_interview(id,customer_id,operator_id,operator_name,content,request_id,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))",id,customerId,operator(a),a.get("name"),content,requestId);int total=0;for(Object image:images){if(!(image instanceof Map))throw new CustomException("图片格式无效",400);Map<?,?> im=(Map<?,?>)image;String mime=Objects.toString(im.get("mime"),"");if(!Arrays.asList("image/jpeg","image/png","image/webp").contains(mime))throw new CustomException("图片类型不支持",400);byte[] bytes;try{bytes=Base64.getDecoder().decode(Objects.toString(im.get("data"),""));}catch(Exception e){throw new CustomException("图片无效",400);}total+=bytes.length;if(total>2097152||bytes.length==0)throw new CustomException("图片合计不能超过2MB",400);try{if(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes))==null)throw new Exception();}catch(Exception e){throw new CustomException("图片无法读取，请使用JPG或PNG",400);}db.update("INSERT INTO ql_staff_interview_image(id,interview_id,mime_type,image_data) VALUES(?,?,?,?)",UUID.randomUUID().toString(),id,mime,bytes);}return id;}
    public Map<String,Object> image(String id){List<Map<String,Object>> rows=db.queryForList("SELECT mime_type,image_data FROM ql_staff_interview_image WHERE id=?",id);if(rows.isEmpty())throw new CustomException("图片不存在",404);return rows.get(0);}
    @Transactional
    public void enroll(String customerId,String sessionId,Map<String,Object> a){
        permit(a,"can_operate");customer(customerId);Map<String,Object> session=lockSession(sessionId);
        if(!"open".equals(session.get("status")))throw new CustomException("当前期次未开放报名",400);
        if(db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE customer_id=? AND session_id=?",Integer.class,customerId,sessionId)>0)throw new CustomException("已有报名记录，请在名单中查看",400);
        java.util.Date now=new java.util.Date();Object opens=session.get("registration_open_at"),closes=session.get("registration_close_at");if((opens instanceof java.util.Date&&now.before((java.util.Date)opens))||(closes instanceof java.util.Date&&now.after((java.util.Date)closes)))throw new CustomException("不在报名开放时间内",400);
        capacity(sessionId,session);
        com.yicai.life.domain.bo.QlRegistrationBo bo=new com.yicai.life.domain.bo.QlRegistrationBo();bo.setCustomerId(customerId);bo.setSessionId(sessionId);bo.setRegistrationStatus("pending");bo.setRegistrationSource("web_admin");registrationService.insertByBo(bo,operator(a));
    }
    private Map<String,Object> lockSession(String id){List<Map<String,Object>> s=db.queryForList("SELECT * FROM ql_session WHERE id=? FOR UPDATE",id);if(s.isEmpty())throw new CustomException("期次不存在",404);return s.get(0);}
    private void capacity(String id,Map<String,Object> session){int count=db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=? AND registration_status='confirmed'",Integer.class,id);if(count>=((Number)session.get("capacity")).intValue())throw new CustomException("本期名额已满",400);}
    @Transactional
    public void confirm(String id,Map<String,Object> a){permit(a,"can_operate");com.yicai.life.domain.QlRegistration r=registrationService.getById(id);if(r==null)throw new CustomException("报名不存在",404);Map<String,Object> session=lockSession(r.getSessionId());r=registrationService.getById(id);if("confirmed".equals(r.getRegistrationStatus()))return;if(!Arrays.asList("open","in_progress").contains(session.get("status"))||!Arrays.asList("pending","waitlisted").contains(r.getRegistrationStatus()))throw new CustomException("当前状态不能确认",400);capacity(r.getSessionId(),session);com.yicai.life.domain.bo.QlRegistrationBo bo=new com.yicai.life.domain.bo.QlRegistrationBo();bo.setId(id);bo.setRegistrationStatus("confirmed");registrationService.updateByBo(bo,operator(a));}
    @Transactional
    public void payment(String id,com.yicai.life.domain.bo.QlPaymentBo bo,Map<String,Object> a){permit(a,"can_payment");com.yicai.life.domain.QlRegistration r=registrationService.getById(id);if(r==null)throw new CustomException("报名不存在",404);if(!"confirmed".equals(r.getRegistrationStatus()))throw new CustomException("请先确认报名",400);if(!"paid".equals(bo.getPaymentStatus()))throw new CustomException("收款更正请在后台处理",400);if(bo.getAmount()==null||bo.getAmount().signum()<=0)throw new CustomException("实收金额必须大于0",400);com.yicai.life.domain.vo.QlRegistrationVo quote=registrationService.queryById(id);java.math.BigDecimal due=quote.getBatchId()==null?quote.getStandardPrice():quote.getPayableAmount();if(due==null||due.compareTo(bo.getAmount())!=0)throw new CustomException("移动端仅支持足额结清，请核对应收金额；差额请在后台处理",400);if(!Arrays.asList("wechat_scan","alipay_scan","transfer","cash","other").contains(bo.getPaymentMethod()))throw new CustomException("付款方式无效",400);if(r.getBatchId()!=null&&!r.getBatchId().isEmpty())registrationService.changeBatchPayment(r.getBatchId(),bo,operator(a));else registrationService.changePayment(id,bo,operator(a));}
    public List<Map<String,Object>> grants(){return db.queryForList("SELECT a.*,u.nick_name AS staffName,w.nick_name AS appName FROM ql_staff_access a JOIN sys_user u ON u.user_id=a.sys_user_id JOIN app_user_info w ON w.id=a.app_user_id ORDER BY a.updated_at DESC");}
    public List<Map<String,Object>> appUsers(String q){return db.queryForList("SELECT id,nick_name AS name,last_login_time AS lastLoginTime FROM app_user_info WHERE status=1 AND (nick_name LIKE ? OR CAST(id AS CHAR)=?) ORDER BY last_login_time DESC LIMIT 30","%"+q+"%",q);}
    public List<Map<String,Object>> operators(){return db.queryForList("SELECT user_id AS id,nick_name AS name FROM sys_user WHERE status='0' AND del_flag='0' ORDER BY user_id LIMIT 200");}
    @Transactional
    public void grant(Map<String,Object> b,long operator){long app=Long.parseLong(text(b,"appUserId",20,true)),user=Long.parseLong(text(b,"sysUserId",20,true));if(db.queryForObject("SELECT COUNT(*) FROM app_user_info WHERE id=? AND status=1",Integer.class,app)!=1||db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'",Integer.class,user)!=1)throw new CustomException("账号不可用",400);db.queryForList("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE",user);if(db.queryForObject("SELECT COUNT(*) FROM ql_staff_access WHERE sys_user_id=? AND app_user_id<>?",Integer.class,user,app)>0)throw new CustomException("该工作人员已绑定其他小程序账号，请先处理原绑定",400);db.update("INSERT INTO ql_staff_access(app_user_id,sys_user_id,enabled,can_operate,can_payment,updated_by) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE sys_user_id=VALUES(sys_user_id),enabled=VALUES(enabled),can_operate=VALUES(can_operate),can_payment=VALUES(can_payment),updated_by=VALUES(updated_by)",app,user,Boolean.TRUE.equals(b.get("enabled"))?1:0,Boolean.TRUE.equals(b.get("canOperate"))?1:0,Boolean.TRUE.equals(b.get("canPayment"))?1:0,operator);}

    public List<Map<String,Object>> attendance(String registrationId){return db.queryForList("SELECT d.id AS dayId,d.day_no AS dayNo,d.activity_date AS activityDate,COALESCE(p.attendance_status,'not_arrived') AS attendanceStatus,p.checked_in_at AS checkedInAt,(d.activity_date=?) AS isToday FROM ql_registration r JOIN ql_session_day d ON d.session_id=r.session_id LEFT JOIN ql_participation_day p ON p.registration_id=r.id AND p.session_day_id=d.id WHERE r.id=? ORDER BY d.day_no",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString(),registrationId);}
    @Transactional
    public void arrive(String id,String dayId,Map<String,Object> a){
        db.queryForList("SELECT id FROM ql_registration WHERE id=? FOR UPDATE",id);
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
        permit(a,"can_operate");db.queryForList("SELECT id FROM ql_registration WHERE id=? FOR UPDATE",id);
        com.yicai.life.domain.QlRegistration r=registrationService.getById(id);if(r==null)throw new CustomException("报名不存在",404);
        if("cancelled".equals(r.getRegistrationStatus()))return;
        if(!"unpaid".equals(r.getPaymentStatus())||r.getBatchId()!=null)throw new CustomException("已结算或小程序订单请由后台核对整单后处理",400);
        if(db.queryForObject("SELECT COUNT(*) FROM ql_participation_day WHERE registration_id=? AND attendance_status IN ('checked_in','late','left_early')",Integer.class,id)>0)throw new CustomException("已有到场记录，请由后台核对处理",400);
        com.yicai.life.domain.bo.QlRegistrationBo bo=new com.yicai.life.domain.bo.QlRegistrationBo();bo.setId(id);bo.setRegistrationStatus("cancelled");registrationService.updateByBo(bo,operator(a));
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
}
