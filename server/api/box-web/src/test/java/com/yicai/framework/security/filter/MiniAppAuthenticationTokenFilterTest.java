package com.yicai.framework.security.filter;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiniAppAuthenticationTokenFilterTest {
    private final RedisCache redis = mock(RedisCache.class);
    private final AppUserInfoMapper users = mock(AppUserInfoMapper.class);
    private final MiniAppAuthenticationTokenFilter filter = new MiniAppAuthenticationTokenFilter(redis, users);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void publicDiscoverySkipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/qinglife/sessions");
        request.setServletPath("/app/qinglife/sessions");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(redis, users);
    }

    @Test void protectedRouteRejectsMissingTokenBeforeController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/qinglife/me/overview");
        request.setServletPath("/app/qinglife/me/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("请先登录"));
        verifyNoInteractions(chain);
    }

    @Test void validEnabledAccountCreatesMiniappAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/qinglife/me/overview");
        request.setServletPath("/app/qinglife/me/overview");
        request.addHeader("token", "valid-token");
        when(redis.getCacheObject("appToken:valid-token")).thenReturn(7L);
        AppUserInfo user = new AppUserInfo();
        user.setStatus(1);
        when(users.selectForIdentity(7L)).thenReturn(user);
        FilterChain chain = (req, res) -> {
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(7L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
