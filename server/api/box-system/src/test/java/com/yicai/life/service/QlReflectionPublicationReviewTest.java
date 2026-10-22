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
    @Test void selectionNeedsNoManualConclusionsButStillBindsConsentParticipationAndContent(){
        validReferences();
        cn.hutool.json.JSONObject audit=JSONUtil.parseObj(reviews.prepare(reflection(),null,42L,Collections.emptySet(),"Approved display"));
        assertEquals(2,audit.getInt("version"));assertEquals("selection",audit.getStr("mode"));
        assertEquals("ready",audit.getStr("decision"));assertEquals(REG,audit.getStr("registrationId"));
        assertEquals("v1",audit.getStr("consentVersion"));assertEquals(3,audit.getInt("reviewedRevision"));
        assertEquals(cn.hutool.crypto.digest.DigestUtil.sha256Hex("Approved display"),audit.getStr("displayHash"));
        for(String field:Arrays.asList("contentSafetyConclusion","identityConclusion","authorizationConclusion","references","basisSummary"))assertFalse(audit.containsKey(field));
        noWrites();
    }
    @Test void selectionRejectsMissingConsentOrInvalidContentWithoutInventingReviewFields(){
        validReferences();
        for(String field:Arrays.asList("consent","consentVersion","consentAt","registrationId","sessionId","customerId")) {
            Map<String,Object> r=reflection();r.remove(field);
            assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(r,null,42L,Collections.emptySet(),"display")).getCode());
        }
        for(String text:Arrays.asList("",String.join("",Collections.nCopies(151,"a"))))assertEquals(400,assertThrows(CustomException.class,()->reviews.prepare(reflection(),null,42L,Collections.emptySet(),text)).getCode());
        noWrites();
    }
    @Test void selectionRejectsUnqualifiedParticipationAndMissingAuthenticatedOperator(){
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),null,42L,Collections.emptySet(),"display")).getCode());
        clearInvocations(db);
        assertEquals(403,assertThrows(CustomException.class,()->reviews.prepare(reflection(),null,null,Collections.emptySet(),"display")).getCode());
        assertEquals(403,assertThrows(CustomException.class,()->reviews.prepare(reflection(),ready(),null,readable,"display")).getCode());
        verifyNoInteractions(db);
    }
    @Test void selectionPublishesThroughTheOriginalModerationAndAuditWithNoDossierPermission(){
        validReferences();Map<String,Object> r=reflection();r.put("status","submitted");moderateRow(r);
        QlReflectionModerationBo b=body(null);b.setDisplayNote("Approved display");
        service().moderate("voice","approvePublish",b,42L,Collections.emptySet());
        verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),eq("published"),eq("Approved display"),eq("Approved display"),eq(100),any(Date.class),eq("voice"));
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("approvePublish"),eq(4),eq(42L),any(Date.class),contains("\"mode\":\"selection\""));
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

    QlReflectionPublishBatchBo batch(String... ids){
        QlReflectionPublishBatchBo body=new QlReflectionPublishBatchBo();List<QlReflectionPublishBatchBo.Item> items=new ArrayList<>();
        for(String id:ids){QlReflectionPublishBatchBo.Item item=new QlReflectionPublishBatchBo.Item();item.setId(id);item.setRevision(3);item.setDisplayNote("Approved display");item.setPublicationReview(ready());items.add(item);}
        body.setItems(items);return body;
    }
    Map<String,Object> locked(String id,String state){Map<String,Object> r=reflection();r.put("id",id);r.put("status",state);when(db.queryForList(startsWith("SELECT id,status,revision"),eq(id))).thenReturn(Collections.singletonList(r));return r;}
    @Test void batchLocksEverySelectedRowInOrderAndDelegatesToTheExistingActions(){
        locked(REG,"submitted");locked(FACT,"hidden");QlCampReflectionService s=spy(service());
        doNothing().when(s).moderate(anyString(),anyString(),any(QlReflectionModerationBo.class),anyLong(),anySet());
        assertEquals(2,s.publishBatch(batch(FACT,REG),42L,readable));
        org.mockito.InOrder order=inOrder(db,s);
        order.verify(db).queryForList(startsWith("SELECT id,status,revision"),eq(REG));
        order.verify(db).queryForList(startsWith("SELECT id,status,revision"),eq(FACT));
        order.verify(s).moderate(eq(REG),eq("approvePublish"),any(QlReflectionModerationBo.class),eq(42L),eq(readable));
        order.verify(s).moderate(eq(FACT),eq("publish"),any(QlReflectionModerationBo.class),eq(42L),eq(readable));
    }
    @Test void revokedConsentStaleRevisionAndPublishedRowsRejectTheWholeSelectionBeforeAnyAction(){
        for(int kind=0;kind<5;kind++){
            locked(REG,"submitted");Map<String,Object> r=locked(FACT,"approved");
            if(kind==0)r.put("consent",0);if(kind==1)r.put("revision",4);if(kind==2)r.put("status","published");if(kind==3)r.put("consentVersion",null);if(kind==4)r.put("consentAt",null);
            QlCampReflectionService s=spy(service());
            assertThrows(CustomException.class,()->s.publishBatch(batch(REG,FACT),42L,readable));
            verify(s,never()).moderate(anyString(),anyString(),any(QlReflectionModerationBo.class),anyLong(),anySet());noWrites();
        }
    }
    @Test void malformedEmptyDuplicateAndUnboundedSelectionsNeverReadStorage(){
        for(QlReflectionPublishBatchBo b:Arrays.asList(batch(),batch(REG,REG),batch("bad"),batch(Collections.nCopies(51,REG).toArray(new String[0]))))assertThrows(CustomException.class,()->service().publishBatch(b,42L,readable));
        QlReflectionPublishBatchBo b=batch(REG);b.getItems().get(0).setRevision(null);assertThrows(CustomException.class,()->service().publishBatch(b,42L,readable));
        assertThrows(CustomException.class,()->service().publishBatch(batch(REG),null,readable));verifyNoInteractions(db);
    }
    @Test void editingAnApprovedSelectionUsesTheExistingEditBeforePublishWithoutMutatingTheRequest(){
        locked(REG,"approved");QlCampReflectionService s=spy(service());doNothing().when(s).moderate(anyString(),anyString(),any(QlReflectionModerationBo.class),anyLong(),anySet());
        QlReflectionPublishBatchBo b=batch(REG);b.getItems().get(0).setDisplayNote("Changed display");s.publishBatch(b,42L,readable);
        org.mockito.ArgumentCaptor<QlReflectionModerationBo> bodies=org.mockito.ArgumentCaptor.forClass(QlReflectionModerationBo.class);
        org.mockito.InOrder order=inOrder(s);order.verify(s).moderate(eq(REG),eq("edit"),bodies.capture(),eq(42L),eq(readable));order.verify(s).moderate(eq(REG),eq("publish"),bodies.capture(),eq(42L),eq(readable));
        assertEquals(3,bodies.getAllValues().get(0).getRevision());assertEquals(4,bodies.getAllValues().get(1).getRevision());assertEquals(3,b.getItems().get(0).getRevision());
    }
    @Test void laterBatchFailureRollsBackTheOuterSpringTransaction(){
        locked(REG,"submitted");locked(FACT,"submitted");QlCampReflectionService target=spy(service());
        doNothing().when(target).moderate(eq(REG),anyString(),any(QlReflectionModerationBo.class),anyLong(),anySet());
        doThrow(new CustomException("Evidence changed",409)).when(target).moderate(eq(FACT),anyString(),any(QlReflectionModerationBo.class),anyLong(),anySet());
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);TransactionStatus status=mock(TransactionStatus.class);when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        assertThrows(CustomException.class,()->((QlCampReflectionService)proxy.getProxy()).publishBatch(batch(REG,FACT),42L,readable));verify(manager).rollback(status);verify(manager,never()).commit(any());
    }
    @Test void reusedEvidenceMustStillMatchItsOriginalSourceHash(){
        validReferences();QlPublicationReviewBo b=ready();b.getReferences().get(0).setSourceHash(String.join("",Collections.nCopies(64,"0")));
        assertEquals(409,assertThrows(CustomException.class,()->reviews.prepare(reflection(),b,42L,readable,"Approved display")).getCode());noWrites();
    }
    @Test void historyReusesOnlyTheSameDisplayConsentAndUnchangedReadableEvidence(){
        validReferences();Map<String,Object> owner=reflection();String audit=reviews.prepare(owner,ready(),42L,readable,"Approved display");
        when(db.queryForList(startsWith("SELECT customer_id AS customerId,registration_id"),eq("voice"))).thenReturn(Collections.singletonList(owner));
        when(db.queryForList(startsWith("SELECT l.action"),eq("voice"))).thenReturn(Collections.singletonList(row("action","publish","publicationReview",audit)));
        assertNotNull(reviews.history("voice",readable).get("reusableReview"));
        owner.put("displayNote","Changed display");assertNull(reviews.history("voice",readable).get("reusableReview"));owner.put("displayNote","Approved display");
        owner.put("consent",0);assertNull(reviews.history("voice",readable).get("reusableReview"));owner.put("consent",1);
        when(db.queryForList(startsWith("SELECT id,registration_status"),eq(REG),eq("A"))).thenReturn(Collections.emptyList());
        assertNull(reviews.history("voice",readable).get("reusableReview"));noWrites();
    }
}
