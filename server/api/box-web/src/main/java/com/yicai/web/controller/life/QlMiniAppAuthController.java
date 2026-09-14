package com.yicai.web.controller.life;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Environment-specific WeChat login; credentials and identities never cross environments. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/qinglife/auth")
@ConditionalOnProperty(name="qinglife.wechat.enabled", havingValue="true")
public class QlMiniAppAuthController {
    private final AppUserInfoMapper userMapper;
    private final RedisCache redisCache;
    @Value("${qinglife.wechat.app-id}") private String appId;
    @Value("${qinglife.wechat.app-secret}") private String appSecret;

    @PostMapping("/login")
    @Transactional
    public synchronized AjaxResult<Map<String,String>> login(@RequestBody Map<String,String> body) {
        String code = body.get("code");
        if (code == null || code.trim().isEmpty() || code.length() > 256) throw new CustomException("微信登录凭证无效", 400);
        Map<String,Object> params = new HashMap<>();
        params.put("appid", appId); params.put("secret", appSecret); params.put("js_code", code); params.put("grant_type", "authorization_code");
        JSONObject session;
        try {
            session = JSONUtil.parseObj(HttpRequest.get("https://api.weixin.qq.com/sns/jscode2session").form(params).timeout(8000).execute().body());
        } catch (Exception e) { throw new CustomException("微信登录服务暂不可用，请重试", 502); }
        String openId = session == null ? null : session.getStr("openid");
        if (openId == null || openId.isEmpty() || session.getInt("errcode", 0) != 0) throw new CustomException("微信登录凭证已失效，请重新登录", 401);
        AppUserInfo user = userMapper.selectOne(Wrappers.<AppUserInfo>lambdaQuery().eq(AppUserInfo::getOpenId, openId));
        Date now = new Date();
        if (user == null) {
            user = new AppUserInfo().setOpenId(openId).setNickName("微信轻友").setStatus(1).setInsertTime(now).setLastLoginTime(now);
            userMapper.insert(user);
        } else {
            if (!Integer.valueOf(1).equals(user.getStatus())) throw new CustomException("账号已停用", 403);
            user.setLastLoginTime(now); userMapper.updateById(user);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisCache.setCacheObject("appToken:" + token, user.getId(), 43200, TimeUnit.MINUTES);
        return AjaxResult.success(Collections.singletonMap("token", token));
    }
}
