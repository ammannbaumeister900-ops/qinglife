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
 private final QlExperienceOriginalService originals;
 private final QlCommunityAccessPolicy communityAccess;
 private final QlReflectionPublicationReviewService publicationReviews;
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 private static final String OWNER_SQL="SELECT r.id AS registrationId,r.registration_status AS registrationStatus,s.id AS sessionId,s.status AS sessionStatus,s.session_number AS sessionNumber,s.name,c.nickname AS authorName,s.start_date AS startDate,s.end_date AS endDate,(SELECT end_time FROM ql_session_day d WHERE d.session_id=s.id AND d.status<>'cancelled' ORDER BY day_no DESC LIMIT 1) AS endTime FROM ql_registration r JOIN ql_session s ON s.id=r.session_id JOIN ql_customer c ON c.id=r.customer_id WHERE r.customer_id=? AND s.id=?";

 public Map<String,Object> mine(String token,String sessionId) {
  String customer=customerId(token);
  List<Map<String,Object>> owners=db.queryForList(OWNER_SQL+" ORDER BY r.active_registration_slot DESC,r.registered_at DESC,r.id DESC LIMIT 1",customer,sessionId);
  if(owners.isEmpty())throw new CustomException("请由本期实际参与者留下心声",403);
  Map<String,Object> owner=owners.get(0), out=new LinkedHashMap<>();
  out.put("registrationId",owner.get("registrationId"));out.put("sessionNumber",owner.get("sessionNumber"));out.put("name",owner.get("name"));out.put("authorName",Objects.toString(owner.get("authorName"),"").trim());
  out.put("canBefore",canWrite(owner,"before",LocalDateTime.now(ZONE)));out.put("canAfter",canWrite(owner,"after",LocalDateTime.now(ZONE)));
  out.put("records",db.queryForList("SELECT phase,draft_note AS note,draft_anonymous AS anonymous,shared_note AS sharedNote,status,share_consent AS shareConsent,share_anonymous AS shareAnonymous,revision FROM ql_camp_reflection WHERE customer_id=? AND registration_id=?",customer,owner.get("registrationId")));
  out.put("unifiedAfter",true);
  Map<String,Object> original=originals.read(customer,String.valueOf(owner.get("registrationId")),false);
  out.put("experienceRecord",original);out.put("originalVersion",originals.version(original));
  out.put("voiceAvailable",voice.available());return out;
 }

 @Transactional
 public Map<String,Object> save(String token,QlCampReflectionBo bo) {
  String customer=customerId(token);
  validateSubmissionContract(bo);
  Map<String,Object> owner=ownedRegistration(customer,bo.getRegistrationId(),true);
  if(!canWrite(owner,bo.getPhase(),LocalDateTime.now(ZONE)))throw new CustomException("报名确认后，本期心声从开营当天起开放",403);
  boolean submitting="submit".equals(bo.getAction());
  if("after".equals(bo.getPhase())&&(bo.getOriginalVersion()!=null||submitting))return saveAfter(token,customer,owner,bo);
  String note=validateNote(bo.getNote());
  boolean submit="submit".equals(bo.getAction());
  boolean anonymous=!Boolean.FALSE.equals(bo.getAnonymous());
  String author=anonymous?null:Objects.toString(owner.get("authorName"),"").trim();
  if(submit&&!anonymous&&author.isEmpty())throw new CustomException("请先完善轻生活小名",400);
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
  List<Map<String,Object>> rows=db.queryForList("SELECT f.id,f.revision FROM ql_camp_reflection f WHERE f.customer_id=? AND f.session_id=? AND f.phase=? AND f.registration_id=(SELECT r.id FROM ql_registration r WHERE r.customer_id=? AND r.session_id=? ORDER BY r.active_registration_slot DESC,r.registered_at DESC,r.id DESC LIMIT 1) FOR UPDATE",customer,sessionId,phase,customer,sessionId);
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

 // Public reading uses the same eligibility for legacy list, bounded pages and full text.
 private static final String PUBLIC_COLUMNS="f.id,f.phase,f.display_excerpt AS excerpt,COALESCE(f.display_note,f.shared_note) AS note,CASE WHEN f.share_anonymous=1 THEN '本期轻友' ELSE f.shared_author END AS author";
 private static final String PUBLIC_FROM=" FROM ql_camp_reflection f JOIN ql_session s ON s.id=f.session_id JOIN ql_registration r ON r.id=f.registration_id AND r.customer_id=f.customer_id AND r.session_id=f.session_id JOIN ql_customer c ON c.id=f.customer_id JOIN ql_camp_reflection_log l ON l.id=(SELECT x.id FROM ql_camp_reflection_log x WHERE x.reflection_id=f.id AND x.action IN ('publish','approvePublish') ORDER BY x.revision DESC,x.created_at DESC,x.id DESC LIMIT 1)"
  +" WHERE f.session_id=? AND f.share_consent=1 AND f.consent_version IS NOT NULL AND f.consent_at IS NOT NULL AND f.status='published' AND s.status NOT IN ('draft','cancelled') AND r.registration_status='confirmed' AND c.deleted_at IS NULL"
  +" AND l.operator_id IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.decision'))='ready'"
  +" AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.displayHash'))=SHA2(COALESCE(f.display_note,f.shared_note),256)"
  +" AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.consentVersion'))=f.consent_version"
  +" AND ((JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.version'))='2' AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.mode'))='selection'"
  +" AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.customerId'))=f.customer_id AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.registrationId'))=f.registration_id AND JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.sessionId'))=f.session_id)"
  +" OR JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.authorizationConclusion'))='not_applicable' OR (JSON_UNQUOTE(JSON_EXTRACT(l.publication_review,'$.authorizationConclusion'))='satisfied'"
  +" AND (SELECT COUNT(*) FROM JSON_TABLE(l.publication_review,'$.references[*]' COLUMNS (kind VARCHAR(32) PATH '$.type',refId VARCHAR(36) PATH '$.id')) refs WHERE refs.kind='consent')>0"
  +" AND 0=(SELECT COUNT(*) FROM JSON_TABLE(l.publication_review,'$.references[*]' COLUMNS (kind VARCHAR(32) PATH '$.type',refId VARCHAR(36) PATH '$.id')) refs LEFT JOIN ql_consent_record cr ON cr.id=refs.refId AND cr.customer_id=f.customer_id LEFT JOIN ql_attachment a ON a.id=cr.evidence_attachment_id AND a.customer_id=cr.customer_id WHERE refs.kind='consent' AND (cr.id IS NULL OR cr.consent_type<>'brand_reuse' OR cr.status<>'granted' OR cr.withdrawn_at IS NOT NULL OR cr.granted_at IS NULL OR cr.recorded_by IS NULL OR a.id IS NULL OR a.scan_status<>'clean' OR a.visibility_scope<>'internal' OR (a.retention_until IS NOT NULL AND a.retention_until<CURRENT_DATE())))))";

 public List<Map<String,Object>> publicVoices(String sessionId) {
  return db.queryForList("SELECT "+PUBLIC_COLUMNS+PUBLIC_FROM+" ORDER BY f.sort_order,f.updated_at DESC,f.id LIMIT 24",sessionId);
 }

 public Map<String,Object> publicVoicePage(String sessionId,Integer pageSize,String cursor) {
  if(pageSize==null||pageSize<1||pageSize>24)throw new CustomException("分页大小须为1至24",400);
  List<Object> args=new ArrayList<>();args.add(sessionId);String seek="";
  if(StrUtil.isNotBlank(cursor)) {
   if(!cursor.matches("[0-9]{1,3}:[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new CustomException("心声游标无效",400);
   int order=Integer.parseInt(cursor.substring(0,cursor.indexOf(':')));String id=cursor.substring(cursor.indexOf(':')+1);
   seek=" AND (f.sort_order>? OR (f.sort_order=? AND f.id>?))";args.add(order);args.add(order);args.add(id);
  }
  args.add(pageSize+1);
  // Scoped pseudonym supports same-person grouping without returning a customer identifier.
  List<Map<String,Object>> rows=db.queryForList("SELECT "+PUBLIC_COLUMNS+",f.session_id AS sessionId,SHA2(CONCAT(f.session_id,':',f.customer_id),256) AS personKey,f.sort_order AS cursorOrder"+PUBLIC_FROM+seek+" ORDER BY f.sort_order,f.id LIMIT ?",args.toArray());
  boolean more=rows.size()>pageSize;List<Map<String,Object>> page=new ArrayList<>(rows.subList(0,Math.min(pageSize,rows.size())));String next=null;
  if(more){Map<String,Object> last=page.get(page.size()-1);next=last.get("cursorOrder")+":"+last.get("id");}
  page.forEach(row->row.remove("cursorOrder"));
  Map<String,Object> out=new LinkedHashMap<>();out.put("contractVersion",1);out.put("sessionId",sessionId);out.put("list",page);out.put("hasMore",more);out.put("nextCursor",next);return out;
 }

 public Map<String,Object> publicVoice(String sessionId,String id) {
  if(id==null||!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new CustomException("心声编号无效",400);
  List<Map<String,Object>> rows=db.queryForList("SELECT "+PUBLIC_COLUMNS+",f.session_id AS sessionId"+PUBLIC_FROM+" AND f.id=?",sessionId,id);
  if(rows.isEmpty())throw new CustomException("心声未公开或已撤回",404);
  Map<String,Object> out=new LinkedHashMap<>();out.put("contractVersion",1);out.put("sessionId",sessionId);out.put("item",rows.get(0));return out;
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
  moderate(id,action,bo,operator,Collections.emptySet());
 }

 /** One bounded selection is committed with its per-item audit records, or rolled back in full. */
 @Transactional
 public int publishBatch(QlReflectionPublishBatchBo bo,Long operator,Set<String> readableTypes) {
  if(operator==null)throw new CustomException("缺少审核人员身份",403);
  if(bo==null||bo.getItems()==null||bo.getItems().isEmpty()||bo.getItems().size()>50)throw new CustomException("请选择一至50条心声",400);
  List<QlReflectionPublishBatchBo.Item> items=new ArrayList<>(bo.getItems());Set<String> ids=new HashSet<>();
  for(QlReflectionPublishBatchBo.Item item:items) {
   if(item==null||item.getRevision()==null||item.getRevision()<1||item.getId()==null||!item.getId().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")||!ids.add(item.getId().toLowerCase(Locale.ROOT)))throw new CustomException("心声编号或版本无效或重复",400);
  }
  items.sort(Comparator.comparing(item->item.getId().toLowerCase(Locale.ROOT)));
  Map<String,Map<String,Object>> locked=new LinkedHashMap<>();
  // Lock the entire selection before any state/evidence writes; concurrent withdrawals use these same rows.
  for(QlReflectionPublishBatchBo.Item item:items) {
   List<Map<String,Object>> rows=db.queryForList("SELECT id,status,revision,share_consent AS consent,consent_version AS consentVersion,consent_at AS consentAt,COALESCE(display_note,shared_note) AS displayNote FROM ql_camp_reflection WHERE id=? FOR UPDATE",item.getId());
   if(rows.size()!=1)throw new CustomException("心声 "+item.getId()+" 不存在，整批未发布",409);
   Map<String,Object> row=rows.get(0);
   if(!Integer.valueOf(1).equals(row.get("consent"))||row.get("consentVersion")==null||Objects.toString(row.get("consentVersion"),"").trim().isEmpty()||row.get("consentAt")==null)throw new CustomException("心声 "+item.getId()+" 公开授权缺失或已撤回，整批未发布",409);
   if(!item.getRevision().equals(((Number)row.get("revision")).intValue()))throw new CustomException("心声 "+item.getId()+" 已变化，请刷新后重选；整批未发布",409);
   if(!Arrays.asList("submitted","rejected","approved","hidden").contains(row.get("status")))throw new CustomException("心声 "+item.getId()+" 状态已变化，整批未发布",409);
   locked.put(item.getId(),row);
  }
  for(QlReflectionPublishBatchBo.Item item:items) {
   Map<String,Object> row=locked.get(item.getId());String state=String.valueOf(row.get("status"));
   try {
    if(Arrays.asList("submitted","rejected").contains(state))moderate(item.getId(),"approvePublish",item,operator,readableTypes);
    else {
     int revision=item.getRevision();
     if(item.getDisplayNote()!=null&&!item.getDisplayNote().trim().equals(Objects.toString(row.get("displayNote"),""))) {
      QlReflectionModerationBo edit=new QlReflectionModerationBo();edit.setRevision(revision);edit.setDisplayNote(item.getDisplayNote());
      moderate(item.getId(),"edit",edit,operator,readableTypes);revision++;
     }
     QlReflectionModerationBo publish=new QlReflectionModerationBo();publish.setRevision(revision);publish.setPublicationReview(item.getPublicationReview());
     moderate(item.getId(),"publish",publish,operator,readableTypes);
    }
   } catch(CustomException error) {throw new CustomException("心声 "+item.getId()+"："+error.getMessage()+"；整批未发布",error.getCode());}
  }
  return items.size();
 }

 @Transactional
 public void moderate(String id,String action,QlReflectionModerationBo bo,Long operator,Set<String> readableTypes) {
  List<Map<String,Object>> rows=db.queryForList("SELECT customer_id AS customerId,registration_id AS registrationId,session_id AS sessionId,consent_version AS consentVersion,consent_at AS consentAt,shared_note AS note,status,revision,share_consent AS consent,display_excerpt AS excerpt,display_note AS displayNote,sort_order AS sortOrder FROM ql_camp_reflection WHERE id=? FOR UPDATE",id);
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
   case "defer": if(!Arrays.asList("approved","hidden").contains(state))throw new CustomException("请先完成内容审核",409);next=state;break;
   case "hide": if(!"published".equals(state))throw new CustomException("此心声尚未展示",409);next="hidden";break;
   case "order": if(!"published".equals(state)||bo.getSortOrder()==null)throw new CustomException("只能调整已展示心声的顺序",409);next=state;break;
   default:throw new CustomException("操作无效",400);
  }
  int order="defer".equals(action)||bo.getSortOrder()==null?((Number)row.get("sortOrder")).intValue():bo.getSortOrder();
  if(order<0||order>999)throw new CustomException("顺序必须为0至999",400);
  String reviewJson=null;
  if(Arrays.asList("publish","approvePublish","defer").contains(action)) {
   if("defer".equals(action)&&bo.getPublicationReview()==null)throw new CustomException("暂缓操作缺少结论",400);
   String decision=bo.getPublicationReview()==null?null:bo.getPublicationReview().getDecision();
   if(bo.getPublicationReview()!=null&&!("defer".equals(action)?"deferred":"ready").equals(decision))throw new CustomException("核对结论与操作不符，依据不足请暂缓",409);
   reviewJson=publicationReviews.prepare(row,bo.getPublicationReview(),operator,readableTypes,displayNote==null?String.valueOf(row.get("note")):displayNote);
  }
  Date now=new Date();db.update("UPDATE ql_camp_reflection SET status=?,display_excerpt=?,display_note=?,sort_order=?,revision=revision+1,updated_at=? WHERE id=?",next,excerpt,displayNote,order,now,id);log(id,action,revision+1,operator,now,reviewJson);
 }

 private Map<String,Object> ownedRegistration(String customer,String registration,boolean lock) {
  List<Map<String,Object>> sessions=db.queryForList("SELECT session_id AS sessionId FROM ql_registration WHERE id=? AND customer_id=?"+(lock?" FOR UPDATE":""),registration,customer);
  if(sessions.isEmpty())throw new CustomException("只能保存本人参加期次的心声",403);
  List<Map<String,Object>> owners=db.queryForList(OWNER_SQL+" AND r.id=?",customer,sessions.get(0).get("sessionId"),registration);
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
 private void log(String id,String action,int rev,Long operator,Date now,String reviewJson) {
  if(reviewJson==null){log(id,action,rev,operator,now);return;}
  db.update("INSERT INTO ql_camp_reflection_log(id,reflection_id,action,revision,operator_id,created_at,publication_review) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,action,rev,operator,now,reviewJson);
 }
 public Map<String,Object> reviewHistory(String id,Set<String> readableTypes){return publicationReviews.history(id,readableTypes);}
 private String customerId(String token) {
  if(StrUtil.isBlank(token))throw new CustomException("请先登录",401);Object cached=redis.getCacheObject("appToken:"+token);
  if(cached==null)throw new CustomException("登录已失效，请重新登录",401);
  try{return identities.resolve(Long.valueOf(String.valueOf(cached)));}catch(NumberFormatException e){throw new CustomException("登录信息无效",401);}
 }
 /** Checked before bean validation at the HTTP boundary so old public clients get code 426. */
 public static void validateSubmissionContract(QlCampReflectionBo bo) {
  if(!"submit".equals(bo.getAction())&&!(bo.getAction()==null&&Boolean.TRUE.equals(bo.getShareConsent())))return;
  if(StrUtil.isBlank(bo.getRegistrationId())||StrUtil.isBlank(bo.getPhase())||bo.getRevision()==null||bo.getNote()==null
    ||bo.getAction()==null||!Boolean.FALSE.equals(bo.getAnonymous())||bo.getShareConsent()==null
    ||"after".equals(bo.getPhase())&&(StrUtil.isBlank(bo.getOriginalVersion())||bo.getSource()==null))
   throw new CustomException("请升级小程序后使用统一编辑器实名发表，并明确授权公开",426);
 }
 // Called inside save's transaction: registration -> original -> public snapshot.
 private Map<String,Object> saveAfter(String token,String customer,Map<String,Object> owner,QlCampReflectionBo bo) {
  if(bo.getOriginalVersion()==null||bo.getSource()==null)throw new CustomException("请升级小程序后使用统一感受编辑器",426);
  boolean submit="submit".equals(bo.getAction());
  if(!submit&&!"private".equals(bo.getAction()))throw new CustomException("保存方式无效",400);
  String note=bo.getNote()==null?"":bo.getNote().trim();
  QlExperienceOriginalService.validateNewNote(note);
  String author=Objects.toString(owner.get("authorName"),"").trim();
  if(submit) {
   validateNote(note);
   if(!Boolean.TRUE.equals(bo.getShareConsent()))throw new CustomException("分享需要本人明确同意",400);
   if(author.isEmpty())throw new CustomException("请先完善轻生活小名",400);
  }
  Map<String,Object> original=originals.read(customer,bo.getRegistrationId(),true);
  if(!originals.version(original).equals(bo.getOriginalVersion()))throw new CustomException("私人原稿已变化，请重新读取后选择",409);
  List<Map<String,Object>> rows=db.queryForList("SELECT id,revision,draft_note AS note FROM ql_camp_reflection WHERE registration_id=? AND phase='after' AND customer_id=? FOR UPDATE",bo.getRegistrationId(),customer);
  Map<String,Object> old=rows.isEmpty()?null:rows.get(0);
  int revision=old==null?0:((Number)old.get("revision")).intValue();
  if(!Integer.valueOf(revision).equals(bo.getRevision()))throw new CustomException("心声已变化，请重新读取后选择",409);
  boolean validSource="experience".equals(bo.getSource())?original!=null:"reflection".equals(bo.getSource())?old!=null:"new".equals(bo.getSource())&&original==null&&old==null;
  if(!validSource)throw new CustomException("请选择已有原稿来源",409);
  Date now=new Date();
  originals.save(customer,bo.getRegistrationId(),String.valueOf(owner.get("sessionId")),note,now);
  if(submit) {
   String id=old==null?UUID.randomUUID().toString():String.valueOf(old.get("id"));
   if(old==null)db.update("INSERT INTO ql_camp_reflection(id,registration_id,customer_id,session_id,phase,draft_note,draft_anonymous,created_at,updated_at) VALUES(?,?,?,?,'after','',0,?,?)",id,bo.getRegistrationId(),customer,owner.get("sessionId"),now,now);
   db.update("UPDATE ql_camp_reflection SET shared_note=?,share_consent=1,share_anonymous=0,shared_author=?,consent_version=?,consent_at=?,status='submitted',display_excerpt=NULL,display_note=NULL,revision=?,updated_at=? WHERE id=?",note,author,CONSENT_VERSION,now,revision+1,now,id);
   log(id,"submit",revision+1,null,now);
  }
  Map<String,Object> result=new LinkedHashMap<>();result.put("context",mine(token,String.valueOf(owner.get("sessionId"))));return result;
 }


}
