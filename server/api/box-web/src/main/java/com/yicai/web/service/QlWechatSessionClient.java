package com.yicai.web.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yicai.common.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** External WeChat boundary; tests replace this bean, never configure a production bypass endpoint. */
@Component
public class QlWechatSessionClient {
    @Value("${qinglife.wechat.app-id}") private String appId;
    @Value("${qinglife.wechat.app-secret}") private String appSecret;
    public String exchange(String code) {
        Map<String,Object> params=new HashMap<>();
        params.put("appid",appId);params.put("secret",appSecret);params.put("js_code",code);params.put("grant_type","authorization_code");
        JSONObject session;
        try(HttpResponse response=HttpRequest.get("https://api.weixin.qq.com/sns/jscode2session").form(params).timeout(8000).execute()) {
            if(!response.isOk())throw new IllegalStateException("WeChat HTTP failure");
            session=JSONUtil.parseObj(response.body());
        } catch(Exception e) {throw new CustomException("微信登录服务暂不可用，请重试",502);}
        String openId=session==null?null:session.getStr("openid");
        if(openId==null||openId.isEmpty()||session.getInt("errcode",0)!=0)throw new CustomException("微信登录凭证已失效，请重新登录",401);
        return openId;
    }
}
