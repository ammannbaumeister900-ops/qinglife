package com.yicai.life.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/** WeChat-verified phone numbers; a number already on another dossier is kept as a conflict, never merged. */
@Service
@RequiredArgsConstructor
public class QlCustomerPhoneService {
    public static final String WECHAT_SCOPE = "wechat-phone";
    private static final String APP_TOKEN_PREFIX = "appToken:";
    private final RedisCache redis;
    private final QlCustomerIdentityService identities;
    private final JdbcTemplate db;

    public String customerId(String token) {
        if (StrUtil.isBlank(token)) throw new CustomException("请先登录", 401);
        Object raw = redis.getCacheObject(APP_TOKEN_PREFIX + token);
        if (raw == null) throw new CustomException("登录已失效，请重新登录", 401);
        try {
            return identities.resolve(Long.valueOf(String.valueOf(raw)));
        } catch (NumberFormatException e) {
            throw new CustomException("登录信息无效", 401);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String bindWechatPhone(String customerId, String phone) {
        String normalized = StrUtil.trimToEmpty(phone);
        if (!normalized.matches("^1\\d{10}$")) throw new CustomException("微信手机号授权失败，请重试", 400);
        if (db.queryForList("SELECT id FROM ql_customer WHERE id=? AND deleted_at IS NULL FOR UPDATE", customerId).isEmpty()) {
            throw new CustomException("轻友档案不存在", 404);
        }
        String hash = DigestUtil.sha256Hex(normalized);
        String hint = hint(normalized);
        List<String> others = db.queryForList("SELECT DISTINCT customer_id FROM ql_customer_identifier WHERE identifier_type='phone' AND normalized_hash=? AND valid_to IS NULL AND customer_id<>? AND verification_status<>'conflict'",
                String.class, hash, customerId);
        String status = others.isEmpty() ? "verified" : "conflict";
        Date now = new Date();
        // Earlier numbers stay as history for staff lookup; only the primary flag moves.
        db.update("UPDATE ql_customer_identifier SET is_primary=0,updated_at=? WHERE customer_id=? AND identifier_type='phone' AND valid_to IS NULL AND normalized_hash<>?", now, customerId, hash);
        int updated = db.update("UPDATE ql_customer_identifier SET identifier_value=?,display_hint=?,app_scope=?,is_primary=1,verification_status=?,valid_to=NULL,updated_at=? WHERE customer_id=? AND identifier_type='phone' AND normalized_hash=?",
                normalized, hint, WECHAT_SCOPE, status, now, customerId, hash);
        if (updated == 0) {
            db.update("INSERT INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,display_hint,app_scope,is_primary,verification_status,valid_from,created_at,updated_at) VALUES(?,?,'phone',?,?,?,?,1,?,?,?,?)",
                    UUID.randomUUID().toString(), customerId, normalized, hash, hint, WECHAT_SCOPE, status, now, now, now);
        }
        return hint;
    }

    public String wechatPhone(String customerId) {
        List<String> rows = db.queryForList("SELECT identifier_value FROM ql_customer_identifier WHERE customer_id=? AND identifier_type='phone' AND app_scope=? AND valid_to IS NULL ORDER BY is_primary DESC,updated_at DESC LIMIT 1",
                String.class, customerId, WECHAT_SCOPE);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String requireWechatPhone(String customerId) {
        String phone = wechatPhone(customerId);
        if (phone == null) throw new CustomException("请先授权微信手机号", 403);
        return phone;
    }

    static String hint(String normalized) {
        return normalized.length() >= 7 ? normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4) : "***";
    }
}
