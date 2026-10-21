package com.yicai.web.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** External WeChat phone-number boundary; tests replace this bean, never configure a production bypass endpoint. */
@Component
public class QlWechatPhoneClient {
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=";
    @Value("${qinglife.wechat.app-id}") private String appId;
    @Value("${qinglife.wechat.app-secret}") private String appSecret;
    private final RedisCache redis;

    public QlWechatPhoneClient(RedisCache redis) { this.redis = redis; }

    /** Returns the mainland number verified by WeChat for this one-time phone code. */
    public String phoneNumber(String phoneCode) {
        JSONObject result = requestPhone(accessToken(), phoneCode);
        int errcode = result.getInt("errcode", 0);
        if (errcode == 40001 || errcode == 42001) {
            redis.deleteObject(tokenKey());
            result = requestPhone(accessToken(), phoneCode);
            errcode = result.getInt("errcode", 0);
        }
        JSONObject info = errcode == 0 ? result.getJSONObject("phone_info") : null;
        String phone = info == null ? null : info.getStr("purePhoneNumber");
        if (info == null || !"86".equals(info.getStr("countryCode")) || phone == null || !phone.matches("^1\\d{10}$")) {
            throw new CustomException("微信手机号授权失败，请重试", 400);
        }
        return phone;
    }

    private String accessToken() {
        Object cached = redis.getCacheObject(tokenKey());
        if (cached != null) return String.valueOf(cached);
        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "client_credential");
        body.put("appid", appId);
        body.put("secret", appSecret);
        JSONObject token = post(TOKEN_URL, JSONUtil.toJsonStr(body));
        String value = token.getStr("access_token");
        if (value == null || value.isEmpty()) throw new CustomException("微信服务暂不可用，请重试", 502);
        long ttl = Math.max(60L, token.getLong("expires_in", 7200L) - 300L);
        redis.setCacheObject(tokenKey(), value, ttl, TimeUnit.SECONDS);
        return value;
    }

    private JSONObject requestPhone(String accessToken, String phoneCode) {
        return post(PHONE_URL + accessToken, JSONUtil.toJsonStr(java.util.Collections.singletonMap("code", phoneCode)));
    }

    private JSONObject post(String url, String json) {
        try (HttpResponse response = HttpRequest.post(url).body(json).timeout(8000).execute()) {
            if (!response.isOk()) throw new IllegalStateException("WeChat HTTP failure");
            return JSONUtil.parseObj(response.body());
        } catch (Exception e) {
            throw new CustomException("微信服务暂不可用，请重试", 502);
        }
    }

    private String tokenKey() { return "qinglife:wechat:stable_token:" + appId; }
}
