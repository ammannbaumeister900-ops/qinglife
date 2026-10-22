package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import com.yicai.life.mapper.QlMiniAppMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class QlSessionReadStateTest {
    private final QlSessionAdmissionPolicy policy = new QlSessionAdmissionPolicy();
    // Explicit local fixtures; never inserted into a business database.
    @TestFactory Stream<DynamicTest> nineteenReadAndWriteWindowCases() {
        String[] rows = {
            "audit-before-open-day|open|closed|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-16T00:00:00|2026-10-19T23:59:59|upcoming|not_open|false",
            "audit-before-open-same-day|open|closed|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-15T18:00:00|2026-10-19T23:59:59|upcoming|not_open|false",
            "audit-open-future|open|open|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-19T23:59:59|upcoming|open|true",
            "audit-close-exact-inclusive|open|open|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-15T12:00:00|upcoming|open|true",
            "audit-close-plus-1ms|open|closed|2026-10-20|2026-10-22|2026-10-15T04:00:00.001Z|2026-10-01T00:00:00|2026-10-15T12:00:00|upcoming|closed|false",
            "audit-closed-previous-day|open|closed|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-14T23:59:59|upcoming|closed|false",
            "audit-in-progress-manual-closed-window|in_progress|in_progress|2026-10-14|2026-10-17|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-13T23:59:59|in_progress|closed|false",
            "audit-in-progress-extended-window|open|in_progress|2026-10-14|2026-10-17|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-17T23:59:59|in_progress|open|true",
            "audit-in-progress-close-same-day|open|in_progress|2026-10-14|2026-10-17|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-15T10:00:00|in_progress|closed|false",
            "audit-ended-stale-config|in_progress|completed|2026-10-01|2026-10-03|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-03T23:59:59|completed|closed|false",
            "audit-manual-cancelled-before-open|cancelled|cancelled|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-16T00:00:00|2026-10-19T23:59:59|cancelled|not_open|false",
            "audit-manual-closed|closed|closed|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-19T23:59:59|upcoming|open|false",
            "audit-manual-completed|completed|completed|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-19T23:59:59|completed|open|false",
            "audit-draft|draft|draft|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-19T23:59:59|draft|open|false",
            "audit-full-open|open|open|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|2026-10-01T00:00:00|2026-10-19T23:59:59|upcoming|open|true",
            "audit-midnight-phone-UTC|open|completed|2026-10-13|2026-10-14|2026-10-14T16:30:00.000Z|2026-10-01T00:00:00|2026-10-14T23:59:59|completed|closed|false",
            "audit-legacy-no-booking-window|open|open|2026-10-20|2026-10-22|2026-10-15T04:00:00.000Z|||upcoming|open|true",
            "audit-end-date-last-ms|open|in_progress|2026-10-14|2026-10-15|2026-10-15T15:59:59.999Z|2026-10-01T00:00:00||in_progress|open|true",
            "audit-end-next-day|open|completed|2026-10-14|2026-10-15|2026-10-15T16:00:00.000Z|2026-10-01T00:00:00||completed|closed|false"
        };
        return Arrays.stream(rows).map(row -> DynamicTest.dynamicTest(row.split("\\|")[0], () -> {
            String[] v = row.split("\\|", -1);
            Instant now = Instant.parse(v[5]);
            Map<String,Object> s = new HashMap<>();
            s.put("configuredStatus", v[1]); s.put("status", v[2]);
            s.put("startDate", v[3]); s.put("endDate", v[4]);
            s.put("stateClockMillis", now.toEpochMilli());
            if (!v[6].isEmpty()) s.put("registrationOpenAt", LocalDateTime.parse(v[6]));
            if (!v[7].isEmpty()) s.put("registrationCloseAt", LocalDateTime.parse(v[7]));
            QlSessionReadState.apply(s, policy);
            assertEquals(v[8], s.get("activityState"));
            assertEquals(v[9], s.get("registrationWindowState"));
            assertEquals(Boolean.valueOf(v[10]), s.get("registrationAllowedByPolicy"));
            assertEquals(v[1], s.get("configuredStatus")); assertEquals(v[2], s.get("status"));
            assertFalse(s.containsKey("stateClockMillis"));
            assertTrue(String.valueOf(s.get("businessTime")).endsWith("+08:00"));
            if (Boolean.parseBoolean(v[10])) assertDoesNotThrow(() -> policy.requireNewRegistrationOpen(s, now));
            else assertThrows(CustomException.class, () -> policy.requireNewRegistrationOpen(s, now));
        }));
    }
    @Test void missingClockAndInvalidDatesCannotCreateAnOpenWindow() {
        Map<String,Object> s=new HashMap<>();s.put("status","open");
        QlSessionReadState.apply(s,policy);
        assertEquals("unknown",s.get("activityState"));assertEquals(false,s.get("registrationAllowedByPolicy"));assertNull(s.get("businessTime"));
        s.put("stateClockMillis",Instant.parse("2026-10-15T04:00:00Z").toEpochMilli());s.put("startDate","bad");s.put("endDate","bad");
        QlSessionReadState.apply(s,policy);assertEquals(false,s.get("registrationAllowedByPolicy"));
    }
    @Test void bothPublicQueriesCarryConfigurationAndTheSameDatabaseClock() throws Exception {
        for(String method:Arrays.asList("selectPublicSessions","selectSessionDetail")) {
            java.lang.reflect.Method m=method.equals("selectPublicSessions")?QlMiniAppMapper.class.getMethod(method):QlMiniAppMapper.class.getMethod(method,String.class);
            String sql=String.join("",m.getAnnotation(Select.class).value());
            assertTrue(sql.contains("s.status AS configuredStatus"));
            assertTrue(sql.contains("TIMESTAMPDIFF(MICROSECOND,'1970-01-01 00:00:00',UTC_TIMESTAMP(3)) DIV 1000 AS stateClockMillis"));
            assertTrue(sql.contains(QlSessionAdmissionPolicy.STATUS_SQL));
        }
    }
}
