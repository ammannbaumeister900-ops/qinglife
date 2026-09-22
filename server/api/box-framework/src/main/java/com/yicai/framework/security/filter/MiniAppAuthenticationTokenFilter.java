package com.yicai.framework.security.filter;

import cn.hutool.core.util.StrUtil;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.utils.JsonUtils;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Default-deny authentication boundary for miniapp routes. */
@Component
@RequiredArgsConstructor
public class MiniAppAuthenticationTokenFilter extends OncePerRequestFilter {
    private static final String PREFIX = "appToken:";
    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private final RedisCache redisCache;
    private final AppUserInfoMapper userMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!path.startsWith("/app/qinglife/")) return true;
        if (HttpMethod.POST.matches(request.getMethod()) && "/app/qinglife/auth/login".equals(path)) return true;
        if (!HttpMethod.GET.matches(request.getMethod())) return false;
        return "/app/qinglife/sessions".equals(path)
                || PATHS.match("/app/qinglife/sessions/*", path)
                || PATHS.match("/app/qinglife/invitations/*", path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = StrUtil.trim(request.getHeader("token"));
        if (StrUtil.isBlank(token) || token.length() > 128) {
            unauthorized(response, "请先登录");
            return;
        }
        Object cached = redisCache.getCacheObject(PREFIX + token);
        Long userId;
        try {
            userId = cached == null ? null : Long.valueOf(String.valueOf(cached));
        } catch (NumberFormatException error) {
            userId = null;
        }
        AppUserInfo user = userId == null ? null : userMapper.selectForIdentity(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            unauthorized(response, "登录已失效，请重新登录");
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId, token, Collections.singletonList(new SimpleGrantedAuthority("ROLE_MINI_APP")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJsonString(AjaxResult.error(HttpServletResponse.SC_UNAUTHORIZED, message)));
    }
}
