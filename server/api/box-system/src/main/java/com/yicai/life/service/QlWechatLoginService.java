package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

/** Database-backed identity creation. Network calls and token issuance stay outside this transaction. */
@Service
@RequiredArgsConstructor
public class QlWechatLoginService {
    private final AppUserInfoMapper userMapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppUserInfo resolveUser(String openId) {
        if (openId == null || openId.trim().isEmpty() || openId.length() > 50) {
            throw new CustomException("微信身份无效", 401);
        }
        Date now = new Date();
        // Upsert obtains an exclusive unique-key lock directly. Catching a duplicate
        // INSERT and upgrading its shared lock to FOR UPDATE can deadlock first logins.
        userMapper.ensureLoginUser(openId, now);
        AppUserInfo user = userMapper.selectForLogin(openId);
        if (user == null) throw new IllegalStateException("Login identity was not persisted");
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new CustomException("账号已停用", 403);
        }
        // Do not write a stale full user object over concurrent profile/status changes.
        userMapper.updateLoginTime(user.getId(), now);
        return user;
    }
}
