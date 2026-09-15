package com.yicai.life.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import com.yicai.life.mapper.QlMiniAppMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;

import org.springframework.transaction.annotation.Transactional;
import java.text.SimpleDateFormat;
import java.util.*;

/** An account row serializes only that user's binding, across all API instances. */
@Service
@RequiredArgsConstructor
public class QlCustomerIdentityService {
    private final AppUserInfoMapper userMapper;
    private final QlMiniAppMapper mapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String resolve(Long userId) {
        AppUserInfo user = userMapper.selectForIdentity(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new CustomException("小程序账号不存在或已停用", 401);
        }
        String customerId = mapper.selectCustomerIdByLegacyUserId(userId);
        if (customerId != null) return customerId;
        Date now = new Date();
        customerId = UUID.randomUUID().toString();
        String number = "QL" + new SimpleDateFormat("yyMMddHHmmss").format(now)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        String gender = Integer.valueOf(1).equals(user.getGender()) ? "male"
                : Integer.valueOf(2).equals(user.getGender()) ? "female" : "unknown";
        mapper.insertCustomer(customerId, number, StrUtil.blankToDefault(user.getNickName(), "微信轻友"), gender, now);
        String openId = StrUtil.blankToDefault(user.getOpenId(), "legacy-user:" + userId);
        mapper.insertIdentifier(UUID.randomUUID().toString(), customerId,
                StrUtil.isBlank(user.getOpenId()) ? "other" : "wechat_openid", openId,
                DigestUtil.sha256Hex(openId), null, "legacy-miniapp", true, "verified", userId, now);
        return customerId;
    }
}
