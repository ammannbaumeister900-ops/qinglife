package com.yicai.life.service;

import cn.hutool.json.JSONUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-memory contract tests; database/operational truth acceptance remains separately NT. */
class QlReflectionPublicationReviewTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final QlReflectionPublicationReviewService reviews=new QlReflectionPublicationReviewService(db);
    final Set<String> readable=new HashSet<>(Arrays.asList("registration","assessment","interaction","consent"));
    static final String REG="00000000-0000-0000-0000-000000000001";
    static final String FACT="00000000-0000-0000-0000-000000000002";
    static final String CONSENT="00000000-0000-0000-0000-000000000003";
    static Map<String,Object> row(Object... pairs){Map<String,Object> out=new HashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    Map<String,Object> reflection(){return row("customerId","A","registrationId",REG,"sessionId","session","revision",3,"consent",1,"status","approved","note","Private snapshot","displayNote","Approved display","excerpt","Approved","sortOrder",100,"consentVersion","v1","consentAt","2026-10-11 10:00:00");}
    QlPublicationReviewBo.Reference ref(String type,String id){QlPublicationReviewBo.Reference r=new QlPublicationReviewBo.Reference();r.setType(type);r.setId(id);return r;}
    QlPublicationReviewBo ready(){
        QlPublicationReviewBo b=new QlPublicationReviewBo();b.setVersion(1);b.setDecision("ready");
        b.setContentSafetyConclusion("Reviewed the actual display for private data and harmful content");
        b.setIdentityConclusion("Existing offline registration evidence inspected for this author");
        b.setAuthorizationConclusion("not_applicable");
        b.setBasisSummary("Checked the linked business material and recorded why no special authorization applies");
        b.setReferences(Collections.singletonList(ref("registration",REG)));return b;
    }
    void validReferences(){
        when(db.queryForList(startsWith("SELECT id,registration_status AS status"),eq(REG),eq("A")))
            .thenReturn(Collections.singletonList(row("id",REG,"status","confirmed")));
        when(db.queryForList(startsWith("SELECT r.id FROM ql_registration"),eq(REG),eq("A"),eq("session")))
            .thenReturn(Collections.singletonList(row("id",REG)));
    }
    QlCampReflectionService service(){return new QlCampReflectionService(null,null,db,null,mock(QlExperienceOriginalService.class),new QlCommunityAccessPolicy(),reviews);}
    QlReflectionModerationBo body(QlPublicationReviewBo review){QlReflectionModerationBo b=new QlReflectionModerationBo();b.setRevision(3);b.setPublicationReview(review);return b;}
    void moderateRow(Map<String,Object> r){when(db.queryForList(startsWith("SELECT customer_id AS customerId,registration_id"),eq("voice"))).thenReturn(Collections.singletonList(r));}
    void noWrites(){assertFalse(mockingDetails(db).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("update")));}
    @Test void validReviewBindsOwnerConsentRevisionAndDisplayWithoutCopyingSourceData(){
        validReferences();
        String json=reviews.prepare(reflection(),ready(),42L,readable,"Approved display");
        cn.hutool.json.JSONObject saved=JSONUtil.parseObj(json);
        assertEquals("A",saved.getStr("customerId"));assertEquals(3,saved.getInt("reviewedRevision"));
        assertEquals("v1",saved.getStr("consentVersion"));assertEquals(64,saved.getStr("displayHash").length());
        assertEquals(64,saved.getJSONArray("references").getJSONObject(0).getStr("sourceHash").length());
        assertFalse(json.contains("operatorId"));assertFalse(json.contains("birthDate"));noWrites();
    }
    @Test void readyPublicationAndEvidenceUseTheSameStateUpdateAndAuditTransaction(){
        validReferences();moderateRow(reflection());
        service().moderate("voice","publish",body(ready()),42L,readable);
        verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("published"),eq("Approved"),eq("Approved display"),eq(100),any(Date.class),eq("voice"));
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("publish"),eq(4),eq(42L),any(Date.class),contains("\"reviewedRevision\":3"));
    }
    @Test void legacyCombinedEndpointStillRequiresAndStoresTheSameEvidence(){
        validReferences();Map<String,Object> r=reflection();r.put("status","submitted");moderateRow(r);
        service().moderate("voice","approvePublish",body(ready()),42L,readable);
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("approvePublish"),eq(4),eq(42L),any(Date.class),contains("\"decision\":\"ready\""));
    }
    @Test void insufficientEvidenceIsRecordedWithoutPublishingOrAlteringTheOriginal(){
        Map<String,Object> r=reflection();moderateRow(r);
        QlPublicationReviewBo deferred=new QlPublicationReviewBo();deferred.setVersion(1);deferred.setDecision("deferred");deferred.setBasisSummary("Necessary offline authorization source is missing");deferred.setAuthorizationConclusion("insufficient");
        QlReflectionModerationBo b=body(deferred);b.setSortOrder(0);
        service().moderate("voice","defer",b,42L,readable);
        verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("approved"),eq("Approved"),eq("Approved display"),eq(100),any(Date.class),eq("voice"));
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("defer"),eq(4),eq(42L),any(Date.class),contains("\"decision\":\"deferred\""));
        assertEquals("Private snapshot",r.get("note"));
    }
    @Test void blankCheckboxLikeOrInsufficientConclusionsCannotPublish(){
        for(int kind=0;kind<6;kind++){
            QlPublicationReviewBo b=ready();
            switch(kind){case 0:b.setBasisSummary("");break;case 1:b.setIdentityConclusion("yes");break;case 2:b.setContentSafetyConclusion("checked");break;case 3:b.setAuthorizationConclusion("insufficient");break;case 4:b.setReferences(Collections.emptyList());break;default:b.setVersion(2);}
            assertThrows(CustomException.class,()->reviews.prepare(reflection(),b,42L,readable,"display"));
        }
        noWrites();
    }
    @Test void missingOldClientReviewAndMissingAuthenticatedOperatorFailClosed(){
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),null,42L,readable,"display")).getCode());
        assertEquals(403,assertThrows(CustomException.class,()->reviews.prepare(reflection(),ready(),null,readable,"display")).getCode());
        verifyNoInteractions(db);
    }
    @Test void clientCannotReferenceAnotherOwnerMissingOrRevokedBusinessRecord(){
        // The actual SQL binds the reflected owner; the mock supplies no eligible row.
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),ready(),42L,readable,"display")).getCode());
        verify(db).queryForList(contains("customer_id=?"),eq(REG),eq("A"));noWrites();
    }
    @Test void businessReadingPermissionIsRequiredEvenWithPublicationPermission(){
        assertEquals(403,assertThrows(CustomException.class,()->reviews.prepare(reflection(),ready(),42L,Collections.emptySet(),"display")).getCode());
        verifyNoInteractions(db);
    }
    @Test void invalidDuplicateOrUnboundedReferencesAreRejected(){
        validReferences();
        for(int kind=0;kind<4;kind++){
            QlPublicationReviewBo b=ready();
            if(kind==0)b.setReferences(Collections.singletonList(ref("attachment","../private")));
            if(kind==1)b.setReferences(Arrays.asList(ref("registration",REG),ref("registration",REG)));
            if(kind==2)b.setReferences(Collections.nCopies(6,ref("registration",REG)));
            if(kind==3)b.setReferences(Collections.singletonList(null));
            assertThrows(CustomException.class,()->reviews.prepare(reflection(),b,42L,readable,"display"));
        }
        noWrites();
    }
    @Test void expiredDeletedOrUnconfirmedParticipationCannotBePublished(){
        when(db.queryForList(startsWith("SELECT id,registration_status"),eq(REG),eq("A"))).thenReturn(Collections.singletonList(row("id",REG)));
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),ready(),42L,readable,"display")).getCode());noWrites();
    }
    @Test void specialAuthorizationNeedsPurposeEvidenceAndConfirmedObjectiveFacts(){
        validReferences();QlPublicationReviewBo b=ready();b.setAuthorizationConclusion("satisfied");
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),b,42L,readable,"display")).getCode());
        b.setReferences(Arrays.asList(ref("interaction",FACT),ref("consent",CONSENT)));
        when(db.queryForList(startsWith("SELECT id,content_type"),eq(FACT),eq("A"))).thenReturn(Collections.singletonList(row("contentType","objective_fact","summary","Offline scope and subject fact")));
        when(db.queryForList(startsWith("SELECT c.id,c.policy_version"),eq(CONSENT),eq("A"))).thenReturn(Collections.singletonList(row("recordedBy",9L,"evidenceAttachmentId",REG)));
        assertNotNull(reviews.prepare(reflection(),b,42L,readable,"display"));
        verify(db).queryForList(contains("consent_type='brand_reuse'"),eq(CONSENT),eq("A"));
        verify(db).queryForList(contains("a.scan_status='clean'"),eq(CONSENT),eq("A"));
        noWrites();
    }
    @Test void genericSelfConsentOrMissingOfflineEvidenceCannotBeSpecialAuthorization(){
        validReferences();QlPublicationReviewBo b=ready();b.setAuthorizationConclusion("satisfied");b.setReferences(Arrays.asList(ref("interaction",FACT),ref("consent",CONSENT)));
        when(db.queryForList(startsWith("SELECT id,content_type"),eq(FACT),eq("A"))).thenReturn(Collections.singletonList(row("contentType","objective_fact")));
        when(db.queryForList(startsWith("SELECT c.id,c.policy_version"),eq(CONSENT),eq("A"))).thenReturn(Collections.singletonList(row("recordedBy",null,"evidenceAttachmentId",null)));
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),b,42L,readable,"display")).getCode());noWrites();
    }
    @Test void deferredConclusionCannotBeUsedForPublishAndReadyCannotBeSavedAsDefer(){
        moderateRow(reflection());QlPublicationReviewBo b=ready();b.setDecision("deferred");
        assertEquals(409,assertThrows(CustomException.class,()->service().moderate("voice","publish",body(b),42L,readable)).getCode());
        b.setDecision("ready");assertEquals(409,assertThrows(CustomException.class,()->service().moderate("voice","defer",body(b),42L,readable)).getCode());noWrites();
    }
    @Test void rePublicationRequiresNewEvidenceAndCurrentRevision(){
        validReferences();Map<String,Object> r=reflection();r.put("status","hidden");r.put("revision",4);moderateRow(r);
        assertEquals(409,assertThrows(CustomException.class,()->service().moderate("voice","publish",body(ready()),42L,readable)).getCode());noWrites();
        QlReflectionModerationBo b=body(ready());b.setRevision(4);
        service().moderate("voice","publish",b,42L,readable);
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("publish"),eq(5),eq(42L),any(Date.class),contains("\"reviewedRevision\":4"));
    }
    @Test void auditInsertFailureTriggersRollbackRatherThanSuccessfulPublication(){
        validReferences();moderateRow(reflection());
        when(db.update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),anyString(),anyString(),anyInt(),anyLong(),any(Date.class),anyString())).thenThrow(new IllegalStateException("Audit unavailable"));
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        TransactionStatus status=mock(TransactionStatus.class);when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        ProxyFactory proxy=new ProxyFactory(service());proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        QlCampReflectionService transactional=(QlCampReflectionService)proxy.getProxy();
        assertThrows(IllegalStateException.class,()->transactional.moderate("voice","publish",body(ready()),42L,readable));
        verify(manager).rollback(status);verify(manager,never()).commit(any());
        // This proves Spring advice, not a real MySQL rollback.
    }
    @Test void historyRetainsLegacyNullRecordsAndRedactsUnreadableEvidence(){
        when(db.queryForList(startsWith("SELECT customer_id AS customerId,registration_id"),eq("voice"))).thenReturn(Collections.singletonList(reflection()));
        String audit="{\"references\":[{\"type\":\"registration\",\"id\":\""+REG+"\"}],\"registrationId\":\""+REG+"\"}";
        when(db.queryForList(startsWith("SELECT l.action"),eq("voice"))).thenReturn(Arrays.asList(row("action","approve","publicationReview",null),row("action","publish","publicationReview",audit)));
        Map<String,Object> result=reviews.history("voice",Collections.singleton("interaction"));
        List<?> rows=(List<?>)result.get("rows");assertNull(((Map<?,?>)rows.get(0)).get("publicationReview"));
        assertTrue((Boolean)((Map<?,?>)rows.get(1)).get("basisRestricted"));assertFalse(result.containsKey("registrationId"));
        assertNull(((Map<?,?>)rows.get(1)).get("publicationReview"));
    }
}
