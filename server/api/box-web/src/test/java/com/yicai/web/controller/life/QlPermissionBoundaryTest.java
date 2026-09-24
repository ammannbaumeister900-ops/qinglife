package com.yicai.web.controller.life;

import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.framework.web.service.PermissionService;
import com.yicai.framework.web.service.TokenService;
import com.yicai.life.domain.bo.QlPassAccountBo;
import com.yicai.life.domain.bo.QlPassAdjustmentBo;
import com.yicai.life.domain.bo.QlSettlementBo;
import com.yicai.life.service.IQlRegistrationService;
import com.yicai.life.service.QlPassService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QlPermissionBoundaryTest {
    private static final AnnotationConfigApplicationContext context = createContext();

    private static AnnotationConfigApplicationContext createContext() {
        AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext();
        app.getBeanFactory().registerSingleton("tokenService", mock(TokenService.class));
        app.getBeanFactory().registerSingleton("passService", mock(QlPassService.class));
        app.getBeanFactory().registerSingleton("registrationService", mock(IQlRegistrationService.class));
        app.register(MethodSecurityConfig.class);
        app.refresh();
        return app;
    }
    private final TokenService tokens = context.getBean(TokenService.class);
    private final QlPassService passes = context.getBean(QlPassService.class);
    private final IQlRegistrationService registrations = context.getBean(IQlRegistrationService.class);
    private final QlPassController passController = context.getBean(QlPassController.class);
    private final QlRegistrationController registrationController = context.getBean(QlRegistrationController.class);

    @BeforeEach
    void loginWithoutPassOrPaymentPermission() {
        reset(tokens, passes, registrations);
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
        verifyNoInteractions(passes, registrations);
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

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {
        @Bean(name = "ss") PermissionService permissionService() { return new PermissionService(); }
        @Bean QlPassController passController(QlPassService service) { return new QlPassController(service); }
        @Bean QlRegistrationController registrationController(IQlRegistrationService service) {
            return new QlRegistrationController(service);
        }
    }
}
