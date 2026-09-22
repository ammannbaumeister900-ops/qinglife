package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.*;
import java.util.Date;
import java.util.Map;

/** One precise clock rule for registration and invitation entry points. */
@Service
public class QlSessionAdmissionPolicy {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public void requireNewRegistrationOpen(Map<String, Object> session) {
        requireNewRegistrationOpen(session, Instant.now());
    }

    void requireNewRegistrationOpen(Map<String, Object> session, Instant now) {
        if (!"open".equals(String.valueOf(session.get("status")))) {
            throw new CustomException("当前期次不接受报名", 400);
        }
        Object opens = value(session, "registration_open_at", "registrationOpenAt");
        Object closes = value(session, "registration_close_at", "registrationCloseAt");
        if (opens != null && now.isBefore(toInstant(opens))) {
            throw new CustomException("报名尚未开放", 400);
        }
        // The configured close instant is inclusive. One millisecond later is closed.
        if (closes != null && now.isAfter(toInstant(closes))) {
            throw new CustomException("报名已经截止", 400);
        }
        if (closes == null) {
            Object end = value(session, "end_date", "endDate");
            if (end != null) {
                Instant exclusiveEnd = toLocalDate(end).plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
                if (!now.isBefore(exclusiveEnd)) {
                    throw new CustomException("报名已经截止", 400);
                }
            }
        }
    }

    private Object value(Map<String, Object> session, String databaseKey, String apiKey) {
        Object value = session.get(databaseKey);
        return value != null ? value : session.get(apiKey);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Timestamp) return ((Timestamp) value).toInstant();
        if (value instanceof Date) return ((Date) value).toInstant();
        if (value instanceof LocalDateTime) return ((LocalDateTime) value).atZone(BUSINESS_ZONE).toInstant();
        if (value instanceof OffsetDateTime) return ((OffsetDateTime) value).toInstant();
        if (value instanceof ZonedDateTime) return ((ZonedDateTime) value).toInstant();
        try {
            return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')).atZone(BUSINESS_ZONE).toInstant();
        } catch (RuntimeException error) {
            throw new CustomException("报名时间配置无效", 400);
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof LocalDateTime) return ((LocalDateTime) value).toLocalDate();
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        if (value instanceof Date) return ((Date) value).toInstant().atZone(BUSINESS_ZONE).toLocalDate();
        String text = String.valueOf(value);
        try {
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
        } catch (RuntimeException error) {
            throw new CustomException("活动日期配置无效", 400);
        }
    }
}
