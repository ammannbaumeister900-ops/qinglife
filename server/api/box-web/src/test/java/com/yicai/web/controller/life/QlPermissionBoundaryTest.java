package com.yicai.web.controller.life;

import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.framework.web.service.PermissionService;
import com.yicai.framework.web.service.TokenService;
import com.yicai.life.domain.bo.QlPassAccountBo;
import com.yicai.life.domain.bo.QlPassAdjustmentBo;
import com.yicai.life.domain.bo.QlSettlementBo;
import com.yicai.life.domain.bo.QlPaymentConfirmationBo;
import com.yicai.life.domain.bo.QlSettlementReversalBo;
import com.yicai.life.service.IQlRegistrationService;
import com.yicai.life.service.QlPassService;
import com.yicai.life.service.IQlCustomerService;
import com.yicai.life.service.QlCustomerDossierService;
import com.yicai.life.service.QlFriendAssessmentService;
import com.yicai.life.service.QlFriendAssessmentExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QlPermissionBoundaryTest {
    private static final AnnotationConfigApplicationContext context = createContext();

    private static AnnotationConfigApplicationContext createContext() {
        AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext();
        app.getBeanFactory().registerSingleton("tokenService", mock(TokenService.class));
        app.getBeanFactory().registerSingleton("accountPages", mock(com.yicai.system.service.AccountPagePermissionService.class));
        app.getBeanFactory().registerSingleton("selections", mock(com.yicai.life.service.QlAdminSelectionService.class));
        app.getBeanFactory().registerSingleton("passService", mock(QlPassService.class));
        app.getBeanFactory().registerSingleton("registrationService", mock(IQlRegistrationService.class));
        app.getBeanFactory().registerSingleton("customerService", mock(IQlCustomerService.class));
        app.getBeanFactory().registerSingleton("dossierService", mock(QlCustomerDossierService.class));
        app.getBeanFactory().registerSingleton("friendAssessments", mock(QlFriendAssessmentService.class));
        app.getBeanFactory().registerSingleton("assessmentExport", mock(QlFriendAssessmentExportService.class));
        app.getBeanFactory().registerSingleton("campReflections", mock(com.yicai.life.service.QlCampReflectionService.class));
        app.register(MethodSecurityConfig.class);
        app.refresh();
        return app;
    }
    private final TokenService tokens = context.getBean(TokenService.class);
    private final QlPassService passes = context.getBean(QlPassService.class);
    private final IQlRegistrationService registrations = context.getBean(IQlRegistrationService.class);
    private final QlPassController passController = context.getBean(QlPassController.class);
    private final QlRegistrationController registrationController = context.getBean(QlRegistrationController.class);
    private final QlCustomerController customerController = context.getBean(QlCustomerController.class);
    private final QlFriendAssessmentService assessments = context.getBean(QlFriendAssessmentService.class);
    private final QlFriendAssessmentExportService exports = context.getBean(QlFriendAssessmentExportService.class);

    @BeforeEach
    void loginWithoutPassOrPaymentPermission() {
        reset(tokens, passes, registrations, assessments, exports, context.getBean(com.yicai.life.service.QlCampReflectionService.class));
        com.yicai.system.service.AccountPagePermissionService pages=context.getBean(com.yicai.system.service.AccountPagePermissionService.class);reset(pages);when(pages.allowsPermission(anyLong(),anyString())).thenReturn(true);
        LoginUser user = new LoginUser(new SysUser().setUserId(42L),
                new HashSet<>(Collections.singletonList("life:session:list")));
        when(tokens.getLoginUser(any(HttpServletRequest.class))).thenReturn(user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unauthorizedStaffCannotReadOrChangePassesOrConfirmSettlement() {
        assertThrows(AccessDeniedException.class, () -> passController.list(null));
        assertThrows(AccessDeniedException.class, () -> passController.ledger("pass-1"));
        assertThrows(AccessDeniedException.class, () -> passController.open(new QlPassAccountBo()));
        assertThrows(AccessDeniedException.class, () -> passController.adjust("pass-1", new QlPassAdjustmentBo()));
        assertThrows(AccessDeniedException.class,
                () -> registrationController.confirmSettlement("registration-1", new QlSettlementBo()));
        assertThrows(AccessDeniedException.class, () -> registrationController.revokeSettlement(
                "registration-1", "settlement-1", new QlSettlementReversalBo()));
        verifyNoInteractions(passes, registrations);
    }

    @Test void unifiedPaymentAndStatisticsSelectorsKeepExistingPermissionBoundaries() {
        assertThrows(AccessDeniedException.class,()->registrationController.confirmPayment("registration-1",new QlPaymentConfirmationBo()));
        assertThrows(AccessDeniedException.class,()->customerController.statisticsSessions());verifyNoInteractions(registrations,assessments);
        LoginUser user=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();user.getPermissions().add("life:registration:payment");user.getPermissions().add("life:assessment:statistics");
        QlPaymentConfirmationBo b=new QlPaymentConfirmationBo();when(registrations.confirmPayment("registration-1",b,42L)).thenReturn(true);registrationController.confirmPayment("registration-1",b);verify(registrations).confirmPayment("registration-1",b,42L);
        when(assessments.statisticsSessions()).thenReturn(Collections.emptyList());customerController.statisticsSessions();verify(assessments).statisticsSessions();
    }
    @Test void revokedAccountPageDeniesCachedTokenAndPrivateImage() {
        LoginUser user=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();user.getPermissions().add("life:pass:list");user.getPermissions().add("life:pass:query");
        com.yicai.system.service.AccountPagePermissionService pages=context.getBean(com.yicai.system.service.AccountPagePermissionService.class);when(pages.allowsPermission(42L,"life:pass:list")).thenReturn(false);when(pages.allowsPermission(42L,"life:pass:query")).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->passController.list(null));assertThrows(AccessDeniedException.class,()->passController.image("private-image"));verifyNoInteractions(passes);
    }
    @Test
    void readPermissionDoesNotGrantPassMutation() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.getPermissions().add("life:pass:list");
        when(passes.list(null)).thenReturn(Collections.emptyList());
        passController.list(null);
        assertThrows(AccessDeniedException.class, () -> passController.open(new QlPassAccountBo()));
        verify(passes).list(null);
        verifyNoMoreInteractions(passes);
    }

    @Test
    void paymentPermissionDoesNotGrantSettlementRevocation() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.getPermissions().add("life:registration:payment");
        assertThrows(AccessDeniedException.class, () -> registrationController.revokeSettlement(
                "registration-1", "settlement-1", new QlSettlementReversalBo()));
        verifyNoInteractions(passes, registrations);
    }

    @Test
    void employeeSensitivePermissionAllowsFullAssessmentView() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.getPermissions().add("life:customer:query");
        user.getPermissions().add("life:assessment:sensitive");
        customerController.assessments("customer-1");
        verify(assessments).history("customer-1", true);
    }

    @Test
    void superAdminWildcardAllowsFullAssessmentView() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.getPermissions().clear();
        user.getPermissions().add("*:*:*");
        customerController.assessments("customer-1");
        verify(assessments).history("customer-1", true);
    }

    @Test
    void queryWithoutSensitivePermissionKeepsAssessmentFieldsMasked() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.getPermissions().add("life:customer:query");
        customerController.assessments("customer-1");
        verify(assessments).history("customer-1", false);
    }

    @Test
    void missingQueryPermissionCannotReadAssessments() {
        assertThrows(AccessDeniedException.class, () -> customerController.assessments("customer-1"));
        verifyNoInteractions(assessments);
    }

    @Test
    void sensitiveViewPermissionDoesNotGrantExport() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.getPermissions().add("life:customer:query");
        user.getPermissions().add("life:assessment:sensitive");
        assertThrows(AccessDeniedException.class, () -> customerController.export(null));
        verifyNoInteractions(exports);
    }

    @Test
    void miniAppAndMobileWorkspaceExposeNoSettlementRevocationRoute() {
        assertFalse(hasSettlementRevocationRoute(QlMiniAppController.class));
        assertFalse(hasSettlementRevocationRoute(QlStaffWorkspaceController.class));
    }

    private boolean hasSettlementRevocationRoute(Class<?> controller) {
        for (java.lang.reflect.Method method : controller.getDeclaredMethods()) {
            org.springframework.web.bind.annotation.PutMapping mapping =
                    method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
            if (mapping == null) continue;
            for (String path : mapping.value()) {
                if (path.contains("/settlement/") && path.endsWith("/revoke")) return true;
            }
        }
        return false;
    }
    @Test void sessionPermissionDoesNotGrantReflectionModerationOrPublication() {
        QlCampReflectionAdminController controller=context.getBean(QlCampReflectionAdminController.class);
        assertThrows(AccessDeniedException.class,()->controller.list(null,null,null,1,10));
        assertThrows(AccessDeniedException.class,()->controller.review("voice","approve",new com.yicai.life.domain.bo.QlReflectionModerationBo()));
        assertThrows(AccessDeniedException.class,()->controller.display("voice","publish",new com.yicai.life.domain.bo.QlReflectionModerationBo()));
        verifyNoInteractions(context.getBean(com.yicai.life.service.QlCampReflectionService.class));
    }
    @Test void editingReflectionUsesReviewPermissionWithoutGrantingPublication() {
        QlCampReflectionAdminController controller=context.getBean(QlCampReflectionAdminController.class);com.yicai.life.domain.bo.QlReflectionModerationBo bo=new com.yicai.life.domain.bo.QlReflectionModerationBo();bo.setRevision(1);bo.setDisplayNote("修改后的心声");
        assertThrows(AccessDeniedException.class,()->controller.review("voice","edit",bo));
        LoginUser user=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();user.getPermissions().add("life:reflection:review");controller.review("voice","edit",bo);
        verify(context.getBean(com.yicai.life.service.QlCampReflectionService.class)).moderate("voice","edit",bo,42L);assertThrows(AccessDeniedException.class,()->controller.display("voice","publish",bo));
    }
    @Test void combinedReflectionReviewRequiresBothExistingPermissions() {
        QlCampReflectionAdminController controller=context.getBean(QlCampReflectionAdminController.class);
        com.yicai.life.service.QlCampReflectionService service=context.getBean(com.yicai.life.service.QlCampReflectionService.class);reset(service);
        LoginUser user=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        com.yicai.life.domain.bo.QlReflectionModerationBo bo=new com.yicai.life.domain.bo.QlReflectionModerationBo();bo.setRevision(1);bo.setDisplayNote("审核后展示");
        user.getPermissions().add("life:reflection:review");assertThrows(AccessDeniedException.class,()->controller.approveAndPublish("voice",bo));
        user.getPermissions().remove("life:reflection:review");user.getPermissions().add("life:reflection:publish");assertThrows(AccessDeniedException.class,()->controller.approveAndPublish("voice",bo));verifyNoInteractions(service);
        user.getPermissions().add("life:reflection:review");controller.approveAndPublish("voice",bo);verify(service).moderate("voice","approvePublish",bo,42L);
    }
    @Test void pageConfigurationAndSelectorsRequireTheirOwnActionGrants() {
        com.yicai.web.controller.system.AccountPagePermissionController pages=context.getBean(com.yicai.web.controller.system.AccountPagePermissionController.class);
        assertThrows(AccessDeniedException.class,()->pages.list(null,1,20));assertThrows(AccessDeniedException.class,()->pages.save(42L,new com.yicai.system.domain.bo.AccountPagePermissionBo()));
        QlAdminSelectionController selectors=context.getBean(QlAdminSelectionController.class);assertThrows(AccessDeniedException.class,()->selectors.customers());assertThrows(AccessDeniedException.class,()->selectors.sessions());assertThrows(AccessDeniedException.class,()->selectors.passes("r"));
        LoginUser user=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();user.getPermissions().add("system:accountPermission:list");pages.list(null,1,20);assertThrows(AccessDeniedException.class,()->pages.save(42L,new com.yicai.system.domain.bo.AccountPagePermissionBo()));
    }
    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {
        @Bean QlAdminSelectionController selectionsController(com.yicai.life.service.QlAdminSelectionService service){return new QlAdminSelectionController(service);}
        @Bean com.yicai.web.controller.system.AccountPagePermissionController accountController(com.yicai.system.service.AccountPagePermissionService service){return new com.yicai.web.controller.system.AccountPagePermissionController(service);}
        @Bean QlCampReflectionAdminController campReflectionController(com.yicai.life.service.QlCampReflectionService reflections) { return new QlCampReflectionAdminController(reflections); }
        @Bean(name = "ss") PermissionService permissionService() { return new PermissionService(); }
        @Bean QlPassController passController(QlPassService service) { return new QlPassController(service); }
        @Bean QlRegistrationController registrationController(IQlRegistrationService service) {
            return new QlRegistrationController(service);
        }
        @Bean QlCustomerController customerController(IQlCustomerService customers,
                QlCustomerDossierService dossier, QlFriendAssessmentService assessments,
                QlFriendAssessmentExportService exports, PermissionService permissions) {
            return new QlCustomerController(customers, dossier, assessments, exports, permissions);
        }
    }
}
