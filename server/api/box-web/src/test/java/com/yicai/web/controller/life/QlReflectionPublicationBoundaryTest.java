package com.yicai.web.controller.life;

import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.framework.web.service.PermissionService;
import com.yicai.framework.web.service.TokenService;
import com.yicai.life.domain.bo.QlReflectionModerationBo;
import com.yicai.life.service.*;
import com.yicai.system.service.AccountPagePermissionService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real method-security/controller/service/policy; only storage and login are in memory.
 * This is not an HTTP, database rollback, operational-evidence or physical-device acceptance. */
class QlReflectionPublicationBoundaryTest {
    static final JdbcTemplate db = mock(JdbcTemplate.class);
    static final TokenService tokens = mock(TokenService.class);
    static final AccountPagePermissionService pages = mock(AccountPagePermissionService.class);
    static final RedisCache redis = mock(RedisCache.class);
    static final QlCustomerIdentityService identities = mock(QlCustomerIdentityService.class);
    static final QlExperienceOriginalService originals = mock(QlExperienceOriginalService.class);
    static final AnnotationConfigApplicationContext context = context();
    static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext();
        app.getBeanFactory().registerSingleton("tokenService", tokens);
        app.getBeanFactory().registerSingleton("accountPages", pages);
        app.getBeanFactory().registerSingleton("reflections",
            new QlCampReflectionService(redis, identities, db, null, originals, new QlCommunityAccessPolicy(), new QlReflectionPublicationReviewService(db)));
        app.register(SecurityConfig.class);
        app.refresh();
        return app;
    }
    final QlCampReflectionAdminController controller = context.getBean(QlCampReflectionAdminController.class);
    final QlCampReflectionService service = context.getBean(QlCampReflectionService.class);
    LoginUser user;
    Map<String,Object> record;
    QlReflectionModerationBo body;
    @BeforeEach void setup() {
        reset(db, tokens, pages, redis, identities, originals);
        user = new LoginUser(new SysUser().setUserId(42L), new HashSet<>());
        when(tokens.getLoginUser(any(HttpServletRequest.class))).thenReturn(user);
        when(pages.allowsPermission(anyLong(), anyString())).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        record = new HashMap<>();
        record.put("customerId", "A");
        record.put("note", "Original public snapshot");
        record.put("status", "submitted");
        record.put("revision", 1);
        record.put("consent", 1);
        record.put("excerpt", "Original");
        record.put("sortOrder", 100);
        when(db.queryForList(startsWith("SELECT customer_id AS customerId"), eq("voice")))
            .thenAnswer(i -> Collections.singletonList(record));
        body = new QlReflectionModerationBo();
        body.setRevision(1);
    }
    @AfterEach void clearLogin() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }
    @AfterAll static void close() { context.close(); }
    void grant(String... names) { user.getPermissions().clear(); Collections.addAll(user.getPermissions(), names); }
    void noWrites() {
        assertFalse(mockingDetails(db).getInvocations().stream()
            .anyMatch(i -> i.getMethod().getName().equals("update")));
        verifyNoInteractions(originals);
    }
    void logged(String action) {
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"), anyString(),
            eq("voice"), eq(action), eq(2), eq(42L), any(Date.class));
    }
    @Test void contentApprovalLogsServerOperatorButDoesNotPublishOrChangeOriginal() {
        grant("life:reflection:review", "life:reflection:publish");
        controller.review("voice", "approve", body);
        verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),
            eq("approved"), eq("Original public snapshot"), isNull(), eq(100), any(Date.class), eq("voice"));
        logged("approve");
        verifyNoInteractions(originals);
        assertEquals("Original public snapshot", record.get("note"));
    }
    @Test void rejectionOfPendingOrApprovedContentRetainsIndependentAuditAction() {
        grant("life:reflection:review");
        for (String status : Arrays.asList("submitted", "approved")) {
            clearInvocations(db);
            record.put("status", status);
            controller.review("voice", "reject", body);
            verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),
                eq("rejected"), eq("Original"), isNull(), eq(100), any(Date.class), eq("voice"));
            logged("reject");
        }
        verifyNoInteractions(originals);
    }
    @Test void missingRecordedSelfConsentBlocksNewAndRepeatPublicationEvenWithBothPermissions() {
        grant("life:reflection:review", "life:reflection:publish");
        for (String status : Arrays.asList("approved", "hidden")) {
            record.put("status", status);
            assertEquals(409, assertThrows(CustomException.class,
                () -> controller.display("voice", "publish", body)).getCode());
        }
        record.put("status", "submitted");
        assertEquals(409, assertThrows(CustomException.class,
            () -> controller.approveAndPublish("voice", body)).getCode());
        noWrites();
    }
    @Test void selectedPublicationWorksWithoutDossierReadPermissionsOrManualConclusions() {
        grant("life:reflection:review", "life:reflection:publish");
        String reg="00000000-0000-0000-0000-000000000001";
        record.put("registrationId",reg);record.put("sessionId","session");
        record.put("consentVersion","v1");record.put("consentAt",new Date());
        when(db.queryForList(startsWith("SELECT r.id FROM"),eq(reg),eq("A"),eq("session"))).thenReturn(Collections.singletonList(Collections.singletonMap("id",reg)));
        controller.approveAndPublish("voice",body);
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("approvePublish"),eq(2),eq(42L),any(Date.class),contains("\"mode\":\"selection\""));
        verifyNoInteractions(originals);
    }
    @Test void absentOrRevokedSharingAndStaleRevisionCannotBePublishedOrReviewed() {
        grant("life:reflection:review", "life:reflection:publish");
        record.put("consent", 0);
        for (String action : Arrays.asList("approve", "reject")) {
            assertEquals(404, assertThrows(CustomException.class,
                () -> controller.review("voice", action, body)).getCode());
        }
        assertEquals(404, assertThrows(CustomException.class,
            () -> controller.display("voice", "publish", body)).getCode());
        record.put("consent", 1);
        record.put("revision", 2);
        assertEquals(409, assertThrows(CustomException.class,
            () -> controller.review("voice", "approve", body)).getCode());
        assertEquals(409, assertThrows(CustomException.class,
            () -> controller.display("voice", "publish", body)).getCode());
        when(db.queryForList(startsWith("SELECT customer_id AS customerId"), eq("voice")))
            .thenReturn(Collections.emptyList());
        assertEquals(404, assertThrows(CustomException.class,
            () -> controller.display("voice", "publish", body)).getCode());
        noWrites();
    }
    @Test void reviewOnlyPublishOnlyAndPageDenialCannotBypassExistingActionPermissions() {
        grant("life:reflection:review");
        assertThrows(AccessDeniedException.class, () -> controller.display("voice", "publish", body));
        assertThrows(AccessDeniedException.class, () -> controller.approveAndPublish("voice", body));
        grant("life:reflection:publish");
        assertThrows(AccessDeniedException.class, () -> controller.review("voice", "approve", body));
        assertThrows(AccessDeniedException.class, () -> controller.approveAndPublish("voice", body));
        grant("life:reflection:review", "life:reflection:publish");
        when(pages.allowsPermission(42L, "life:reflection:publish")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> controller.display("voice", "publish", body));
        assertThrows(AccessDeniedException.class, () -> controller.approveAndPublish("voice", body));
        verifyNoInteractions(db, originals);
    }
    @Test void alternateActionNamesCannotRoutePublicationThroughReview() {
        grant("life:reflection:review", "life:reflection:publish");
        for (String action : Arrays.asList("publish", "approvePublish", "hide")) {
            assertEquals(400, assertThrows(CustomException.class,
                () -> controller.review("voice", action, body)).getCode());
        }
        for (String action : Arrays.asList("approve", "approvePublish", "reject")) {
            assertEquals(400, assertThrows(CustomException.class,
                () -> controller.display("voice", action, body)).getCode());
        }
        verifyNoInteractions(db, originals);
    }
    @Test void batchPublicationRequiresBothPermissionsAndStillChecksTheReadableEvidenceScope() {
        com.yicai.life.domain.bo.QlReflectionPublishBatchBo b=new com.yicai.life.domain.bo.QlReflectionPublishBatchBo();
        grant("life:reflection:review");assertThrows(AccessDeniedException.class,()->controller.publishBatch(b));
        grant("life:reflection:publish");assertThrows(AccessDeniedException.class,()->controller.publishBatch(b));
        grant("life:reflection:review","life:reflection:publish");
        assertEquals(400,assertThrows(CustomException.class,()->controller.publishBatch(b)).getCode());noWrites();
    }
    @Test void historicalPublishedContentCanStillBeHiddenAndLogsTheOperator() {
        grant("life:reflection:publish");
        record.put("status", "published");
        controller.display("voice", "hide", body);
        verify(db).update(startsWith("UPDATE ql_camp_reflection SET status="),
            eq("hidden"), eq("Original"), isNull(), eq(100), any(Date.class), eq("voice"));
        logged("hide");
        verifyNoInteractions(originals);
    }
    @Test void ownerWithdrawalLogsWithoutDeletingOrEditingPrivateOriginal() {
        when(redis.getCacheObject("appToken:isolated")).thenReturn(7L);
        when(identities.resolve(7L)).thenReturn("A");
        Map<String,Object> owned = new HashMap<>();
        owned.put("id", "voice"); owned.put("revision", 1);
        when(db.queryForList(startsWith("SELECT f.id,f.revision FROM"), eq("A"), eq("session"), eq("after"), eq("A"), eq("session")))
            .thenReturn(Collections.singletonList(owned));
        service.withdraw("isolated", "session", "after");
        verify(db).update(startsWith("UPDATE ql_camp_reflection SET share_consent=0"),
            eq(2), any(Date.class), eq("voice"));
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"), anyString(),
            eq("voice"), eq("withdraw"), eq(2), isNull(), any(Date.class));
        verifyNoInteractions(originals);
    }

    com.yicai.life.domain.bo.QlPublicationReviewBo completeReview() {
        com.yicai.life.domain.bo.QlPublicationReviewBo b=new com.yicai.life.domain.bo.QlPublicationReviewBo();
        b.setVersion(1);b.setDecision("ready");
        b.setContentSafetyConclusion("Reviewed the display for content and privacy safety");
        b.setIdentityConclusion("Reviewed existing business evidence for this participant");
        b.setAuthorizationConclusion("not_applicable");
        b.setBasisSummary("Existing offline facts inspected and applicable reasons recorded");
        com.yicai.life.domain.bo.QlPublicationReviewBo.Reference ref=new com.yicai.life.domain.bo.QlPublicationReviewBo.Reference();
        ref.setType("registration");ref.setId("00000000-0000-0000-0000-000000000001");
        b.setReferences(Collections.singletonList(ref));return b;
    }
    @Test void authorizedExplicitPublishReachesRealEvidenceValidationAndAudit() {
        grant("life:reflection:publish","life:registration:query","life:customer:query");
        String reg="00000000-0000-0000-0000-000000000001";
        record.put("registrationId",reg);record.put("sessionId","session");record.put("status","approved");
        when(db.queryForList(startsWith("SELECT id,registration_status"),eq(reg),eq("A"))).thenReturn(Collections.singletonList(Collections.singletonMap("id",reg)));
        when(db.queryForList(startsWith("SELECT r.id FROM"),eq(reg),eq("A"),eq("session"))).thenReturn(Collections.singletonList(Collections.singletonMap("id",reg)));
        body.setPublicationReview(completeReview());
        controller.display("voice","publish",body);
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("publish"),eq(2),eq(42L),any(Date.class),contains("\"decision\":\"ready\""));
    }
    @Test void publishPermissionDoesNotGrantReadOfEvidenceOrAuditHistory() {
        grant("life:reflection:publish");
        record.put("status","approved");body.setPublicationReview(completeReview());
        assertEquals(403,assertThrows(CustomException.class,()->controller.display("voice","publish",body)).getCode());
        assertThrows(AccessDeniedException.class,()->controller.history("voice"));
        noWrites();
    }
    @Test void deferRequiresPublishPermissionAndRetainsServerOperator() {
        grant("life:reflection:review");record.put("status","approved");
        com.yicai.life.domain.bo.QlPublicationReviewBo b=completeReview();b.setDecision("deferred");b.setAuthorizationConclusion("insufficient");b.setReferences(Collections.emptyList());body.setPublicationReview(b);
        assertThrows(AccessDeniedException.class,()->controller.defer("voice",body));verifyNoInteractions(db);
        grant("life:reflection:publish");controller.defer("voice",body);
        verify(db).update(startsWith("INSERT INTO ql_camp_reflection_log"),anyString(),eq("voice"),eq("defer"),eq(2),eq(42L),any(Date.class),contains("\"decision\":\"deferred\""));
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class SecurityConfig {
        @Bean(name="ss") PermissionService permissions() { return new PermissionService(); }
        @Bean QlCampReflectionAdminController controller(QlCampReflectionService service, PermissionService permissions) {
            return new QlCampReflectionAdminController(service, permissions);
        }
    }
}
