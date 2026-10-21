package com.yicai.life.service;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.*;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.impl.QlMiniAppServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QlCommunityAccessPolicyTest {
 final QlCommunityAccessPolicy policy=new QlCommunityAccessPolicy();
 static Map<String,Object> row(Object... pairs){Map<String,Object> r=new HashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
 QlCampReflectionBo post(String phase,String action){QlCampReflectionBo b=new QlCampReflectionBo();b.setRegistrationId("reg");b.setPhase(phase);b.setRevision(0);b.setNote("原文");b.setAction(action);b.setAnonymous(false);b.setShareConsent("submit".equals(action));b.setOriginalVersion("absent");b.setSource("new");return b;}
 RedisCache redis(){RedisCache r=mock(RedisCache.class);when(r.getCacheObject("appToken:isolated")).thenReturn(42L);return r;}
 QlCustomerIdentityService identities(){QlCustomerIdentityService i=mock(QlCustomerIdentityService.class);when(i.resolve(42L)).thenReturn("A");return i;}
 QlCustomerPhoneService phones(){QlCustomerPhoneService p=mock(QlCustomerPhoneService.class);when(p.requireWechatPhone(anyString())).thenReturn("19900000001");return p;}
 @Test void immediateInvitationsStayPausedWithoutTreatingProfileValuesAsAuthorization(){
  for(String id:Arrays.asList(null,"","unknown","under14","age17","self-edited-adult","verified-label")){
   assertFalse(policy.canInvite(id));
   assertEquals(403,assertThrows(CustomException.class,()->policy.requireInvitationCreation(id)).getCode());
  }
 }
 @Test void ordinaryOwnerCanSubmitBothPhasesWithoutBirthdayOrInvitationPermission(){
  for(String phase:Arrays.asList("before","after")){
   JdbcTemplate db=mock(JdbcTemplate.class);QlExperienceOriginalService originals=mock(QlExperienceOriginalService.class);
   Map<String,Object> owner=row("registrationId","reg","registrationStatus","confirmed","sessionStatus","after".equals(phase)?"completed":"in_progress","sessionId","s","startDate","2020-01-01","endDate","after".equals(phase)?"2020-01-03":"2099-01-03","authorName","小禾");
   when(db.queryForList(startsWith("SELECT session_id"),eq("reg"),eq("A"))).thenReturn(Collections.singletonList(row("sessionId","s")));
   when(db.queryForList(startsWith("SELECT r.id"),eq("A"),eq("s"))).thenReturn(Collections.singletonList(owner));
  when(db.queryForList(endsWith(" AND r.id=?"),eq("A"),eq("s"),eq("reg"))).thenReturn(Collections.singletonList(owner));
   when(originals.read(anyString(),anyString(),anyBoolean())).thenReturn(null);when(originals.version(null)).thenReturn("absent");
   QlCampReflectionService s=new QlCampReflectionService(redis(),identities(),db,mock(QlReflectionVoiceService.class),originals,policy, new QlReflectionPublicationReviewService(db));
   s.save("isolated",post(phase,"submit"));
   assertTrue(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")&&String.valueOf((Object)i.getArgument(0)).contains("status='submitted'")));
   assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")&&String.valueOf((Object)i.getArgument(0)).contains("status='published'")));
   QlCampReflectionBo old=post(phase,"submit");old.setAnonymous(true);assertEquals(426,assertThrows(CustomException.class,()->s.save("isolated",old)).getCode());
  }
 }
 @Test void submissionStillRequiresIdentityOwnershipAndActivePublicConsent(){
  JdbcTemplate db=mock(JdbcTemplate.class);QlExperienceOriginalService originals=mock(QlExperienceOriginalService.class);
  QlCampReflectionService s=new QlCampReflectionService(redis(),identities(),db,null,originals,policy, new QlReflectionPublicationReviewService(db));
  assertEquals(401,assertThrows(CustomException.class,()->s.save(null,post("before","submit"))).getCode());verifyNoInteractions(db,originals);
  assertEquals(403,assertThrows(CustomException.class,()->s.save("isolated",post("before","submit"))).getCode());
  Map<String,Object> owner=row("registrationStatus","confirmed","sessionStatus","in_progress","sessionId","s","startDate","2020-01-01","endDate","2099-01-03","authorName","小禾");
  when(db.queryForList(startsWith("SELECT session_id"),eq("reg"),eq("A"))).thenReturn(Collections.singletonList(row("sessionId","s")));
  when(db.queryForList(startsWith("SELECT r.id"),eq("A"),eq("s"))).thenReturn(Collections.singletonList(owner));
  when(db.queryForList(endsWith(" AND r.id=?"),eq("A"),eq("s"),eq("reg"))).thenReturn(Collections.singletonList(owner));
  QlCampReflectionBo denied=post("before","submit");denied.setShareConsent(false);assertEquals(400,assertThrows(CustomException.class,()->s.save("isolated",denied)).getCode());
  assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")));
 }
 @Test void ordinaryOwnerRetainsPrivateSaveAndContextRead(){
  JdbcTemplate db=mock(JdbcTemplate.class);QlExperienceOriginalService originals=mock(QlExperienceOriginalService.class);
  Map<String,Object> owner=row("registrationId","reg","registrationStatus","confirmed","sessionStatus","completed","sessionId","s","startDate","2020-01-01","endDate","2020-01-03");
  when(db.queryForList(startsWith("SELECT session_id"),eq("reg"),eq("A"))).thenReturn(Collections.singletonList(row("sessionId","s")));
  when(db.queryForList(startsWith("SELECT r.id"),eq("A"),eq("s"))).thenReturn(Collections.singletonList(owner));
  when(db.queryForList(endsWith(" AND r.id=?"),eq("A"),eq("s"),eq("reg"))).thenReturn(Collections.singletonList(owner));
  when(originals.read(anyString(),anyString(),anyBoolean())).thenReturn(null);
  when(originals.version(null)).thenReturn("absent");
  QlCampReflectionService s=new QlCampReflectionService(redis(),identities(),db,mock(QlReflectionVoiceService.class),originals,policy, new QlReflectionPublicationReviewService(db));
  Map<String,Object> result=s.save("isolated",post("after","private"));assertNotNull(result.get("context"));
  verify(originals).save(eq("A"),eq("reg"),eq("s"),eq("原文"),any(Date.class));
  assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")));
 }
 @Test void withdrawalAndHistoricalPublicReadingRemainAvailableWithoutAgeProof(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForList(startsWith("SELECT f.id,f.revision FROM"),eq("A"),eq("s"),eq("after"),eq("A"),eq("s"))).thenReturn(Collections.singletonList(row("id","f","revision",3)));
  Map<String,Object> historical=row("id","f","author","本期轻友","note","历史匿名内容");
  when(db.queryForList(startsWith("SELECT f.id"),eq("s"))).thenReturn(Collections.singletonList(historical));
  QlCampReflectionService s=new QlCampReflectionService(redis(),identities(),db,mock(QlReflectionVoiceService.class),mock(QlExperienceOriginalService.class),policy, new QlReflectionPublicationReviewService(db));
  assertEquals(historical,s.publicVoices("s").get(0));s.withdraw("isolated","s","after");
  verify(db).update(startsWith("UPDATE ql_camp_reflection SET share_consent=0"),eq(4),any(Date.class),eq("f"));
  assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")&&String.valueOf((Object)i.getArgument(0)).contains("draft_note=")));
 }
 @Test void necessaryIdentityAndAuthorizationEvidenceCannotBeReplacedByContentApproval(){
  JdbcTemplate db=mock(JdbcTemplate.class);Map<String,Object> old=row("customerId","A","note","历史","status","approved","revision",1,"consent",1,"excerpt","历史","sortOrder",0);
  when(db.queryForList(startsWith("SELECT customer_id AS customerId"),eq("f"))).thenReturn(Collections.singletonList(old));
  QlCampReflectionService s=new QlCampReflectionService(null,null,db,null,null,policy, new QlReflectionPublicationReviewService(db));QlReflectionModerationBo b=new QlReflectionModerationBo();b.setRevision(1);
  for(String action:Arrays.asList("approvePublish","publish"))assertEquals(409,assertThrows(CustomException.class,()->s.moderate("f",action,b,9L)).getCode());
  assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")));
  old.put("status","published");s.moderate("f","hide",b,9L);
  verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("hidden"),eq("历史"),isNull(),eq(0),any(Date.class),eq("f"));
 }
 @Test void contentReviewAndRejectionRemainSeparateFromFinalPublicationAndLogTheOperator(){
  JdbcTemplate db=mock(JdbcTemplate.class);Map<String,Object> r=row("customerId","A","note","真实原文","status","submitted","revision",1,"consent",1,"excerpt","","sortOrder",0);
  when(db.queryForList(startsWith("SELECT customer_id AS customerId"),eq("f"))).thenReturn(Collections.singletonList(r));
  QlCampReflectionService s=new QlCampReflectionService(null,null,db,null,null,policy, new QlReflectionPublicationReviewService(db));QlReflectionModerationBo b=new QlReflectionModerationBo();b.setRevision(1);
  s.moderate("f","approve",b,9L);
  verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("approved"),eq("真实原文"),isNull(),eq(0),any(Date.class),eq("f"));
  verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("f"),eq("approve"),eq(2),eq(9L),any(Date.class));
  r.put("status","approved");s.moderate("f","reject",b,9L);
  verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("rejected"),eq(""),isNull(),eq(0),any(Date.class),eq("f"));
  verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("f"),eq("reject"),eq(2),eq(9L),any(Date.class));
 }
 @Test void historicCustomerInvitationCodesCannotBypassVerificationAtResolveOrRegistration(){
  QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);QlMiniAppServiceImpl s=new QlMiniAppServiceImpl(redis(),identities(),mapper,mock(QlSessionPricing.class),mock(QlHabitPlanService.class),mock(QlSessionAdmissionPolicy.class),policy);
  org.springframework.test.util.ReflectionTestUtils.setField(s,"policy",mock(QlRegistrationPolicy.class));
  org.springframework.test.util.ReflectionTestUtils.setField(s,"phones",phones());
  String code="0123456789abcdef0123456789abcdef";
  when(mapper.selectInvitation(code)).thenReturn(row("purpose","registration","sessionId","s","ownerId","old-minor-or-unknown"));
  assertEquals(403,assertThrows(CustomException.class,()->s.resolveInvitation(code)).getCode());
  when(mapper.selectBatchByClientRequestId(anyString())).thenReturn(null);when(mapper.selectSessionForRegistration("s")).thenReturn(row("status","open"));
  QlMiniAppRegistrationBo b=new QlMiniAppRegistrationBo();b.setSessionId("s");b.setClientRequestId("synthetic");b.setInvitationCode(code);b.setContactName("Test");b.setContactPhone("19900000001");b.setServiceConsent(true);
  QlMiniAppRegistrationBo.Participant person=new QlMiniAppRegistrationBo.Participant();person.setSelf(true);person.setName("Test");b.setParticipants(Collections.singletonList(person));
  assertEquals(403,assertThrows(CustomException.class,()->s.register("isolated",b)).getCode());
  assertFalse(mockingDetails(mapper).getInvocations().stream().anyMatch(i->i.getMethod().getName().startsWith("insert")));
  when(mapper.selectInvitation(code)).thenReturn(row("purpose","registration","sessionId","s","ownerId",null));when(mapper.selectSessionDetail("s")).thenReturn(row("id","s"));assertEquals("s",s.resolveInvitation(code).get("sessionId"));
 }
 @Test void customerInvitationAndOverviewShareTheGateWithoutTrustingSelfEditedBirthday(){
  QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);when(mapper.selectCustomerProfile("A")).thenReturn(row("minor",false,"birthDate","1990-01-01","dataConfidence","verified"));
  QlMiniAppServiceImpl s=new QlMiniAppServiceImpl(redis(),identities(),mapper,mock(QlSessionPricing.class),mock(QlHabitPlanService.class),mock(QlSessionAdmissionPolicy.class),policy);
  org.springframework.test.util.ReflectionTestUtils.setField(s,"phones",phones());
  assertEquals(false,s.overview("isolated").get("invitationEligible"));
  assertEquals(403,assertThrows(CustomException.class,()->s.createInvitation("isolated","s",false)).getCode());
  assertEquals(401,assertThrows(CustomException.class,()->s.createInvitation(null,"s",false)).getCode());
  verify(mapper,never()).insertInvitation(anyString(),anyString(),any(),anyString(),any(Date.class));
  when(mapper.selectSessionDetail("s")).thenReturn(row("id","s"));
  assertNotNull(s.createInvitation(null,"s",true).get("code"));
  verify(mapper).insertInvitation(anyString(),eq("s"),isNull(),eq("staff"),any(Date.class));
 }
}
