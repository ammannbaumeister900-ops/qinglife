package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QlSessionAdmissionPolicyTest {
    private final QlSessionAdmissionPolicy policy = new QlSessionAdmissionPolicy();

    @Test void usesTheSameInclusiveCloseInstantForEveryCaller() {
        Instant close = Instant.parse("2026-09-22T10:00:00Z");
        Map<String,Object> session = openSession();
        session.put("registrationCloseAt", LocalDateTime.ofInstant(close, QlSessionAdmissionPolicy.BUSINESS_ZONE));
        assertDoesNotThrow(() -> policy.requireNewRegistrationOpen(session, close.minusMillis(1)));
        assertDoesNotThrow(() -> policy.requireNewRegistrationOpen(session, close));
        assertThrows(CustomException.class, () -> policy.requireNewRegistrationOpen(session, close.plusMillis(1)));
    }

    @Test void rejectsBeforeOpenAndFallsBackToEndOfEndDate() {
        Map<String,Object> session = openSession();
        session.put("registrationOpenAt", LocalDateTime.of(2026, 9, 22, 9, 0));
        session.put("endDate", LocalDate.of(2026, 9, 23));
        assertThrows(CustomException.class, () -> policy.requireNewRegistrationOpen(session,
                LocalDateTime.of(2026, 9, 22, 8, 59, 59).atZone(QlSessionAdmissionPolicy.BUSINESS_ZONE).toInstant()));
        assertDoesNotThrow(() -> policy.requireNewRegistrationOpen(session,
                LocalDateTime.of(2026, 9, 23, 23, 59, 59).atZone(QlSessionAdmissionPolicy.BUSINESS_ZONE).toInstant()));
        assertThrows(CustomException.class, () -> policy.requireNewRegistrationOpen(session,
                LocalDate.of(2026, 9, 24).atStartOfDay(QlSessionAdmissionPolicy.BUSINESS_ZONE).toInstant()));
    }

    @Test void rejectsNonOpenSession() {
        Map<String,Object> session = openSession();
        session.put("status", "cancelled");
        assertThrows(CustomException.class, () -> policy.requireNewRegistrationOpen(session, Instant.now()));
    }

    @Test void acceptsJdbcDateReturnedByMysql() {
        Map<String,Object> session = openSession();
        session.put("end_date", java.sql.Date.valueOf("2026-09-23"));
        assertDoesNotThrow(() -> policy.requireNewRegistrationOpen(session,
                LocalDateTime.of(2026, 9, 23, 12, 0).atZone(QlSessionAdmissionPolicy.BUSINESS_ZONE).toInstant()));
    }

    private Map<String,Object> openSession() {
        Map<String,Object> session = new HashMap<>();
        session.put("status", "open");
        return session;
    }
}
