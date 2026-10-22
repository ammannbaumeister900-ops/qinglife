package com.yicai.life.service;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlCampReflectionBo;
import org.junit.jupiter.api.*;
import com.yicai.life.mapper.QlExperienceOriginalMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QlUnifiedReflectionTest {
 QlExperienceOriginalMapper mapper; JdbcTemplate db; RedisCache redis; QlCustomerIdentityService identities; QlExperienceOriginalService originals; QlCampReflectionService service;
 Map<String,Object> owner,old,original;
 static Map<String,Object> row(Object... pairs){Map<String,Object> r=new HashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);redis=mock(RedisCache.class);identities=mock(QlCustomerIdentityService.class);mapper=mock(QlExperienceOriginalMapper.class);originals=new QlExperienceOriginalService(mapper);
  when(redis.getCacheObject("appToken:isolated")).thenReturn("42");when(identities.resolve(42L)).thenReturn("A");
  owner=row("registrationId","reg","registrationStatus","confirmed","sessionStatus","completed","sessionId","s","startDate","2020-01-01","endDate","2020-01-03","authorName","小禾");
  old=row("id","f","revision",3,"phase","after","note","历史匿名原稿","sharedNote","历史公开","shareAnonymous",1,"shareConsent",1,"status","published");
  original=row("id","e","note","私人原文","energy",4,"relaxation",2,"recordStamp","2026-10-10 01:00:00.123000");
  when(db.queryForList(startsWith("SELECT session_id"),eq("reg"),eq("A"))).thenReturn(Collections.singletonList(row("sessionId","s")));
  when(db.queryForList(startsWith("SELECT r.id"),eq("A"),eq("s"))).thenReturn(Collections.singletonList(owner));
  when(db.queryForList(endsWith(" AND r.id=?"),eq("A"),eq("s"),eq("reg"))).thenReturn(Collections.singletonList(owner));
  when(mapper.read("A","reg")).thenAnswer(i->original);when(mapper.readForUpdate("A","reg")).thenAnswer(i->original);
  when(db.queryForList(startsWith("SELECT id,revision,draft_note"),eq("reg"),eq("A"))).thenAnswer(i->old==null?Collections.emptyList():Collections.singletonList(old));
  when(db.queryForList(startsWith("SELECT phase,draft_note"),eq("A"),eq("reg"))).thenAnswer(i->old==null?Collections.emptyList():Collections.singletonList(old));
  service=new QlCampReflectionService(redis,identities,db,mock(QlReflectionVoiceService.class),originals,new QlCommunityAccessPolicy(), new QlReflectionPublicationReviewService(db));
 }
 QlCampReflectionBo bo(String action){QlCampReflectionBo b=new QlCampReflectionBo();b.setRegistrationId("reg");b.setPhase("after");b.setRevision(old==null?0:3);b.setNote("同一份新感受");b.setAction(action);b.setAnonymous(false);b.setShareConsent("submit".equals(action));b.setSource(original!=null?"experience":old!=null?"reflection":"new");b.setOriginalVersion(originals.version(original));return b;}
 void noWrites(){assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")));verify(mapper,never()).save(anyString(),anyString(),anyString(),anyString(),anyString(),any(Date.class));}
 @Test void publicSavesOwnerOriginalAndNamedPendingSnapshotWithoutOverwritingLegacyDraft(){
  service.save("isolated",bo("submit"));
  org.mockito.InOrder order=inOrder(db,mapper);
  order.verify(db).queryForList(startsWith("SELECT session_id"),eq("reg"),eq("A"));
  order.verify(mapper).readForUpdate("A","reg");
  order.verify(db).queryForList(startsWith("SELECT id,revision,draft_note"),eq("reg"),eq("A"));
  order.verify(mapper).save(anyString(),eq("A"),eq("reg"),eq("s"),eq("同一份新感受"),any(Date.class));
  order.verify(db).update(startsWith("UPDATE ql_camp_reflection SET shared_note="),eq("同一份新感受"),eq("小禾"),eq(QlCampReflectionService.CONSENT_VERSION),any(Date.class),eq(4),any(Date.class),eq("f"));
  assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")&&String.valueOf((Object)i.getArgument(0)).startsWith("UPDATE ql_camp_reflection SET draft_note=")));
  assertEquals("历史匿名原稿",old.get("note"));assertEquals(1,old.get("shareAnonymous"));
 }
 @Test void privateOnlyWritesCanonicalOriginalAndKeepsRatingsSnapshotAndModeration(){
  service.save("isolated",bo("private"));verify(mapper).save(anyString(),eq("A"),eq("reg"),eq("s"),eq("同一份新感受"),any(Date.class));
  assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")&&String.valueOf((Object)i.getArgument(0)).contains("ql_camp_reflection")));assertEquals(4,original.get("energy"));assertEquals(2,original.get("relaxation"));
 }
 @Test void newAndExplicitLegacySourceWorkWithoutMigration(){
  original=null;service.save("isolated",bo("private"));verify(db,never()).update(contains("ql_camp_reflection"),any(Object[].class));
  reset(db);setup();original=null;old=null;service.save("isolated",bo("submit"));verify(db).update(startsWith("INSERT INTO ql_camp_reflection"),anyString(),eq("reg"),eq("A"),eq("s"),any(Date.class),any(Date.class));
 }
 @Test void staleOriginalOrPublicRevisionAndInvalidSourceRejectBeforeWrite(){
  QlCampReflectionBo b=bo("submit");b.setOriginalVersion("stale");assertEquals(409,assertThrows(CustomException.class,()->service.save("isolated",b)).getCode());
  b.setOriginalVersion(originals.version(original));b.setRevision(2);assertThrows(CustomException.class,()->service.save("isolated",b));b.setRevision(3);b.setSource("new");assertThrows(CustomException.class,()->service.save("isolated",b));noWrites();
 }
 @Test void codepointBoundaryAndHistoricLongRecordAreNeverTruncated(){
  String emoji=new String(Character.toChars(0x1f33f));QlCampReflectionBo b=bo("submit");b.setNote(String.join("",Collections.nCopies(151,emoji)));assertThrows(CustomException.class,()->service.save("isolated",b));noWrites();
  original.put("note",String.join("",Collections.nCopies(1000,"长")));Map<String,Object> context=service.mine("isolated","s");assertEquals(original,context.get("experienceRecord"));assertEquals(1000,String.valueOf(original.get("note")).length());
  b.setOriginalVersion(originals.version(original));b.setNote(String.join("",Collections.nCopies(150,emoji)));service.save("isolated",b);
 }
 @Test void emptyPrivateIsAllowedButPublicRequiresTextNicknameAndConsent(){
  QlCampReflectionBo b=bo("submit");b.setNote("");assertThrows(CustomException.class,()->service.save("isolated",b));b.setNote("文字");owner.put("authorName"," ");assertThrows(CustomException.class,()->service.save("isolated",b));owner.put("authorName","小禾");b.setShareConsent(false);assertThrows(CustomException.class,()->service.save("isolated",b));noWrites();b.setAction("private");b.setNote("");service.save("isolated",b);
 }
 @Test void legacyAnonymousMissingConsentAndMissingUnifiedContractReturnUpgradeCodeWithoutWrites(){
  for(String phase:Arrays.asList("before","after"))for(int missing=0;missing<4;missing++){
   QlCampReflectionBo b=bo("submit");b.setPhase(phase);owner.put("sessionStatus",phase.equals("before")?"in_progress":"completed");owner.put("startDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());owner.put("endDate","2099-01-03");
   if(missing==0)b.setAnonymous(true);if(missing==1)b.setAnonymous(null);if(missing==2)b.setShareConsent(null);if(missing==3){if(phase.equals("before"))continue;b.setOriginalVersion(null);}
   CustomException error=assertThrows(CustomException.class,()->service.save("isolated",b));assertEquals(426,error.getCode());assertTrue(error.getMessage().contains("升级"));
  }noWrites();
 }
 @Test void foreignOwnerInvalidTokenAndWrongStageNeverReachOriginalWriter(){
  when(redis.getCacheObject("appToken:isolated")).thenReturn(null);assertEquals(401,assertThrows(CustomException.class,()->service.save("isolated",bo("submit"))).getCode());
  when(redis.getCacheObject("appToken:isolated")).thenReturn("42");when(db.queryForList(startsWith("SELECT session_id"),eq("reg"),eq("A"))).thenReturn(Collections.emptyList());assertEquals(403,assertThrows(CustomException.class,()->service.save("isolated",bo("submit"))).getCode());noWrites();
 }
 @Test void publicWriteFailureRollsBackTheSpringTransactionInsteadOfReturningPrivateSuccess(){
  when(db.update(startsWith("UPDATE ql_camp_reflection SET shared_note="),anyString(),anyString(),anyString(),any(Date.class),anyInt(),any(Date.class),anyString())).thenThrow(new IllegalStateException("isolated failure"));
  PlatformTransactionManager manager=mock(PlatformTransactionManager.class);TransactionStatus status=mock(TransactionStatus.class);when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
  ProxyFactory proxy=new ProxyFactory(service);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
  QlCampReflectionService transactional=(QlCampReflectionService)proxy.getProxy();assertThrows(IllegalStateException.class,()->transactional.save("isolated",bo("submit")));
  verify(manager).rollback(status);verify(manager,never()).commit(any());
 }
 @Test void originalVersionCoversBodyRatingsAndExactTimestamp(){
  String version=originals.version(original);original.put("energy",5);assertNotEquals(version,originals.version(original));original.put("energy",4);original.put("note","别的原稿");assertNotEquals(version,originals.version(original));assertEquals("absent",originals.version(null));
 }
}
