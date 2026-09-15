package com.yicai.web.controller.life;

import com.yicai.life.service.QlWechatLoginService;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.AppUserInfo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Environment-specific WeChat login; credentials and identities never cross environments. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/qinglife/auth")
@ConditionalOnProperty(name="qinglife.wechat.enabled", havingValue="true")
public class QlMiniAppAuthController {
    private final QlWechatLoginService loginService;
    private final RedisCache redisCache;
    private final com.yicai.web.service.QlWechatSessionClient wechat;

    @PostMapping("/login")
    public AjaxResult<Map<String,String>> login(@RequestBody Map<String,String> body) {
        String code = body.get("code");
        if (code == null || code.trim().isEmpty() || code.length() > 256) throw new CustomException("微信登录凭证无效", 400);
        String openId = wechat.exchange(code);
        AppUserInfo user = loginService.resolveUser(openId);
        String token = UUID.randomUUID().toString().replace("-", "");
        redisCache.setCacheObject("appToken:" + token, user.getId(), 43200, TimeUnit.MINUTES);
        return AjaxResult.success(Collections.singletonMap("token", token));
    }
}
