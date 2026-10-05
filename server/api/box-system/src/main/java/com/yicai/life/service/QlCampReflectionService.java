package com.yicai.life.service;

import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Time;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class QlCampReflectionService {
 public static final String CONSENT_VERSION="camp-attribution-20261003";
 private final RedisCache redis;
 private final QlCustomerIdentityService identities;
 private final JdbcTemplate db;
 private final QlReflectionVoiceService voice;
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 private static final String OWNER_SQL="SELECT r.id AS registrationId,r.registration_status AS registrationStatus,s.id AS sessionId,s.status AS sessionStatus,s.session_number AS sessionNumber,s.name,c.nickname AS authorName,s.start_date AS startDate,s.end_date AS endDate,(SELECT end_time FROM ql_session_day d WHERE d.session_id=s.id AND d.status<>'cancelled' ORDER BY day_no DESC LIMIT 1) AS endTime FROM ql_registration r JOIN ql_session s ON s.id=r.session_id JOIN ql_customer c ON c.id=r.customer_id WHERE r.customer_id=? AND s.id=?";

 public Map<String,Object> mine(String token,String sessionId) {
  String customer=customerId(token);
  List<Map<String,Object>> owners=db.queryForList(OWNER_SQL,customer,sessionId);
  if(owners.isEmpty())throw new CustomException("请由本期实际参与者留下心声",403);
  Map<String,Object> owner=owners.get(0), out=new LinkedHashMap<>();
  out.put("registrationId",owner.get("registrationId"));out.put("sessionNumber",owner.get("sessionNumber"));out.put("name",owner.get("name"));out.put("authorName",Objects.toString(owner.get("authorName"),"").trim());
  out.put("canBefore",canWrite(owner,"before",LocalDateTime.now(ZONE)));out.put("canAfter",canWrite(owner,"after",LocalDateTime.now(ZONE)));
  out.put("records",db.queryForList("SELECT phase,draft_note AS note,draft_anonymous AS anonymous,shared_note AS sharedNote,status,share_consent AS shareConsent,share_anonymous AS shareAnonymous,revision FROM ql_camp_reflection WHERE customer_id=? AND session_id=?",customer,sessionId));
  out.put("voiceAvailable",voice.available());return out;
 }

 @Transactional
 public Map<String,Object> save(String token,QlCampReflectionBo bo) {
  String customer=customerId(token);
  Map<String,Object> owner=ownedRegistration(customer,bo.getRegistrationId(),true);
  if(!canWrite(owner,bo.getPhase(),LocalDateTime.now(ZONE)))throw new CustomException("报名确认后，本期心声从开营当天起开放",403);
  String note=validateNote(bo.getNote());
  boolean submit="submit".equals(bo.getAction());
  boolean anonymous=!Boolean.FALSE.equals(bo.getAnonymous());
  String author=anonymous?null:Objects.toString(owner.get("authorName"),"").trim();
  if(submit&&!anonymous&&author.isEmpty())throw new CustomException("请先完善轻生活小名，或选择匿名发布",400);
  if(submit&&!Boolean.TRUE.equals(bo.getShareConsent()))throw new CustomException("分享需要本人明确同意",400);
  if(!submit&&!"private".equals(bo.getAction()))throw new CustomException("保存方式无效",400);
  List<Map<String,Object>> rows=db.queryForList("SELECT id,revision,status FROM ql_camp_reflection WHERE registration_id=? AND phase=? FOR UPDATE",bo.getRegistrationId(),bo.getPhase());
  String id;int revision;Date now=new Date();
  if(rows.isEmpty()) {
   if(!Integer.valueOf(0).equals(bo.getRevision()))throw new CustomException("心声已变化，请重新打开",409);
   id=UUID.randomUUID().toString();revision=1;
   db.update("INSERT INTO ql_camp_reflection(id,registration_id,customer_id,session_id,phase,draft_note,draft_anonymous,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",id,bo.getRegistrationId(),customer,owner.get("sessionId"),bo.getPhase(),note,anonymous?1:0,now,now);
  } else {
   Map<String,Object> old=rows.get(0);id=String.valueOf(old.get("id"));revision=((Number)old.get("revision")).intValue()+1;
   if(!Integer.valueOf(revision-1).equals(bo.getRevision()))throw new CustomException("心声已变化，请重新打开",409);
   db.update("UPDATE ql_camp_reflection SET draft_note=?,draft_anonymous=?,revision=?,updated_at=? WHERE id=?",note,anonymous?1:0,revision,now,id);
  }
  // Editing a private draft never alters the already authorized public snapshot.
  if(submit)db.update("UPDATE ql_camp_reflection SET shared_note=?,share_consent=1,share_anonymous=?,shared_author=?,consent_version=?,consent_at=?,status='submitted',display_excerpt=NULL,display_note=NULL,updated_at=? WHERE id=?",note,anonymous?1:0,author,CONSENT_VERSION,now,now,id);
  log(id,submit?"submit":"private",revision,null,now);
  return db.queryForMap("SELECT phase,draft_note AS note,draft_anonymous AS anonymous,shared_note AS sharedNote,status,share_consent AS shareConsent,share_anonymous AS shareAnonymous,revision FROM ql_camp_reflection WHERE id=?",id);
 }

 @Transactional
 public void withdraw(String token,String sessionId,String phase) {
  String customer=customerId(token);
  List<Map<String,Object>> rows=db.queryForList("SELECT id,revision FROM ql_camp_reflection WHERE customer_id=? AND session_id=? AND phase=? FOR UPDATE",customer,sessionId,phase);
  if(rows.isEmpty())return;
  Map<String,Object> row=rows.get(0);int rev=((Number)row.get("revision")).intValue()+1;Date now=new Date();
  db.update("UPDATE ql_camp_reflection SET share_consent=0,status='withdrawn',revision=?,updated_at=? WHERE id=?",rev,now,row.get("id"));
  log(String.valueOf(row.get("id")),"withdraw",rev,null,now);
 }

 public Map<String,Object> transcribe(String token,QlReflectionVoiceBo bo) {
  String customer=customerId(token);
  if(!canWrite(ownedRegistration(customer,bo.getRegistrationId(),false),bo.getPhase(),LocalDateTime.now(ZONE)))throw new CustomException("当前不能填写这一阶段的心声",403);
  if(!bo.isVoiceConsent())throw new CustomException("请先同意本次语音转文字处理",400);
  return voice.transcribe(customer,bo);
 }

 public List<Map<String,Object>> publicVoices(String sessionId) {
  return db.queryForList("SELECT f.id,f.phase,f.display_excerpt AS excerpt,COALESCE(f.display_note,f.shared_note) AS note,CASE WHEN f.share_anonymous=1 THEN '本期轻友' ELSE f.shared_author END AS author FROM ql_camp_reflection f JOIN ql_session s ON s.id=f.session_id JOIN ql_registration r ON r.id=f.registration_id JOIN ql_customer c ON c.id=f.customer_id WHERE f.session_id=? AND f.share_consent=1 AND f.status='published' AND s.status NOT IN ('draft','cancelled') AND r.registration_status='confirmed' AND c.deleted_at IS NULL ORDER BY f.sort_order,f.updated_at DESC,f.id LIMIT 24",sessionId);
 }

 public Map<String,Object> list(String sessionId,String phase,String status,int page,int size) {
  if(page<1||page>100000||size<1||size>50)throw new CustomException("分页参数无效",400);
  StringBuilder where=new StringBuilder(" WHERE f.share_consent=1");List<Object> params=new ArrayList<>();
  if(StrUtil.isNotBlank(sessionId)){where.append(" AND f.session_id=?");params.add(sessionId);}
  if(StrUtil.isNotBlank(phase)){if(!Arrays.asList("before","after").contains(phase))throw new CustomException("阶段无效",400);where.append(" AND f.phase=?");params.add(phase);}
  if(StrUtil.isNotBlank(status)){if(!Arrays.asList("submitted","approved","published","hidden","rejected").contains(status))throw new CustomException("状态无效",400);where.append(" AND f.status=?");params.add(status);}
  String from=" FROM ql_camp_reflection f JOIN ql_session s ON s.id=f.session_id";
  Map<String,Object> out=new LinkedHashMap<>();out.put("total",db.queryForObject("SELECT COUNT(*)"+from+where,Long.class,params.toArray()));
  params.add(size);params.add((page-1)*size);
  out.put("rows",db.queryForList("SELECT f.id,f.session_id AS sessionId,s.session_number AS sessionNumber,f.phase,f.shared_note AS note,f.share_anonymous AS anonymous,CASE WHEN f.share_anonymous=1 THEN '本期轻友' ELSE f.shared_author END AS author,f.status,f.display_excerpt AS excerpt,COALESCE(f.display_note,f.shared_note) AS displayNote,f.sort_order AS sortOrder,f.revision,f.consent_version AS consentVersion,DATE_FORMAT(f.consent_at,'%Y-%m-%d %H:%i:%s') AS consentAt"+from+where+" ORDER BY f.updated_at DESC,f.id LIMIT ? OFFSET ?",params.toArray()));return out;
 }

 @Transactional
 public void moderate(String id,String action,QlReflectionModerationBo bo,Long operator) {
  List<Map<String,Object>> rows=db.queryForList("SELECT shared_note AS note,status,revision,share_consent AS consent,display_excerpt AS excerpt,display_note AS displayNote,sort_order AS sortOrder FROM ql_camp_reflection WHERE id=? FOR UPDATE",id);
  if(rows.isEmpty()||((Number)rows.get(0).get("consent")).intValue()!=1)throw new CustomException("心声不存在或分享已撤回",404);
  Map<String,Object> row=rows.get(0);int revision=((Number)row.get("revision")).intValue();
  if(!Integer.valueOf(revision).equals(bo.getRevision()))throw new CustomException("心声已变化，请刷新后审核",409);
  String state=String.valueOf(row.get("status")),next,excerpt=Objects.toString(row.get("excerpt"),""),displayNote=(String)row.get("displayNote");
  switch(action) {
   case "approvePublish":
   case "approve": if(!Arrays.asList("submitted","rejected").contains(state))throw new CustomException("此状态不能审核通过",409);next="approvePublish".equals(action)?"published":"approved";
    if(bo.getDisplayNote()!=null){displayNote=validateNote(bo.getDisplayNote());excerpt=prefix(displayNote,32);}
    else {displayNote=null;excerpt=StrUtil.isBlank(bo.getExcerpt())?prefix(String.valueOf(row.get("note")),32):bo.getExcerpt().trim();
     if(excerpt.codePointCount(0,excerpt.length())>40||!String.valueOf(row.get("note")).contains(excerpt))throw new CustomException("展示内容无效",400);}
    break;
   case "edit": if(!Arrays.asList("approved","hidden").contains(state))throw new CustomException("请先下架再编辑心声",409);next=state;displayNote=validateNote(bo.getDisplayNote());excerpt=prefix(displayNote,32);break;
   case "reject": if(!Arrays.asList("submitted","approved").contains(state))throw new CustomException("请先下架已展示的心声",409);next="rejected";break;
   case "publish": if(!Arrays.asList("approved","hidden").contains(state)||excerpt.isEmpty())throw new CustomException("请先审核通过",409);next="published";break;
   case "hide": if(!"published".equals(state))throw new CustomException("此心声尚未展示",409);next="hidden";break;
   case "order": if(!"published".equals(state)||bo.getSortOrder()==null)throw new CustomException("只能调整已展示心声的顺序",409);next=state;break;
   default:throw new CustomException("操作无效",400);
  }
  int order=bo.getSortOrder()==null?((Number)row.get("sortOrder")).intValue():bo.getSortOrder();
  if(order<0||order>999)throw new CustomException("顺序必须为0至999",400);
  Date now=new Date();db.update("UPDATE ql_camp_reflection SET status=?,display_excerpt=?,display_note=?,sort_order=?,revision=revision+1,updated_at=? WHERE id=?",next,excerpt,displayNote,order,now,id);log(id,action,revision+1,operator,now);
 }

 private Map<String,Object> ownedRegistration(String customer,String registration,boolean lock) {
  List<Map<String,Object>> sessions=db.queryForList("SELECT session_id AS sessionId FROM ql_registration WHERE id=? AND customer_id=?"+(lock?" FOR UPDATE":""),registration,customer);
  if(sessions.isEmpty())throw new CustomException("只能保存本人参加期次的心声",403);
  List<Map<String,Object>> owners=db.queryForList(OWNER_SQL,customer,sessions.get(0).get("sessionId"));
  if(owners.isEmpty())throw new CustomException("报名不存在",403);return owners.get(0);
 }
 static boolean canWrite(Map<String,Object> row,String phase,LocalDateTime now) {
  if(!"confirmed".equals(row.get("registrationStatus"))||Arrays.asList("draft","cancelled").contains(row.get("sessionStatus")))return false;
  LocalDate start=LocalDate.parse(String.valueOf(row.get("startDate")).substring(0,10)),end=LocalDate.parse(String.valueOf(row.get("endDate")).substring(0,10));
  Object time=row.get("endTime");
  boolean finished="completed".equals(row.get("sessionStatus"))||now.toLocalDate().isAfter(end)
    ||now.toLocalDate().equals(end)&&time!=null&&!now.toLocalTime().isBefore(time instanceof Time?((Time)time).toLocalTime():LocalTime.parse(String.valueOf(time)));
  if("before".equals(phase))return !now.toLocalDate().isBefore(start)&&!finished;
  return "after".equals(phase)&&finished;
 }
 static String validateNote(String raw) {String note=raw==null?"":raw.trim();if(note.isEmpty()||note.codePointCount(0,note.length())>150)throw new CustomException("请留下一至150字的心声",400);return note;}
 static String prefix(String s,int count){return s.substring(0,s.offsetByCodePoints(0,Math.min(count,s.codePointCount(0,s.length()))));}
 private void log(String id,String action,int rev,Long operator,Date now){db.update("INSERT INTO ql_camp_reflection_log(id,reflection_id,action,revision,operator_id,created_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,action,rev,operator,now);}
 private String customerId(String token) {
  if(StrUtil.isBlank(token))throw new CustomException("请先登录",401);Object cached=redis.getCacheObject("appToken:"+token);
  if(cached==null)throw new CustomException("登录已失效，请重新登录",401);
  try{return identities.resolve(Long.valueOf(String.valueOf(cached)));}catch(NumberFormatException e){throw new CustomException("登录信息无效",401);}
 }
}
