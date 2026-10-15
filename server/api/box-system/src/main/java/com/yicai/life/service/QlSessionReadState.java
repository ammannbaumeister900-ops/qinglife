package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Public read projection only. Does not reserve seats or decide customer eligibility. */
public final class QlSessionReadState {
    private QlSessionReadState() {}

    public static void apply(Map<String, Object> session, QlSessionAdmissionPolicy policy) {
        Object clock = session.remove("stateClockMillis");
        Object configured = session.containsKey("configuredStatus") ? session.get("configuredStatus") : session.get("status");
        session.put("configuredStatus", configured);
        session.put("stateContractVersion", 1);
        session.put("activityState", "unknown");
        session.put("registrationWindowState", "unknown");
        session.put("registrationAllowedByPolicy", false);
        session.put("registrationWindowReason", "报名信息待核对");
        session.put("businessTime", null);
        if (!(clock instanceof Number)) return;
        Instant now = Instant.ofEpochMilli(((Number) clock).longValue());
        session.put("businessTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atZone(QlSessionAdmissionPolicy.BUSINESS_ZONE)));
        try {
            String phase = activityState(session, String.valueOf(configured), now, policy);
            QlSessionAdmissionPolicy.Window window = policy.registrationWindow(session, now);
            session.put("activityState", phase);
            session.put("registrationWindowState", window.state);
            session.put("registrationAllowedByPolicy", window.allowed);
            session.put("registrationWindowReason", window.reason);
        } catch (CustomException | IllegalArgumentException error) {
            // Invalid source fields stay unavailable; never substitute a phone/JVM clock.
        }
    }

    private static String activityState(Map<String, Object> session, String configured, Instant now, QlSessionAdmissionPolicy policy) {
        if ("cancelled".equals(configured) || "draft".equals(configured) || "completed".equals(configured)) return configured;
        if (!"open".equals(configured) && !"in_progress".equals(configured) && !"closed".equals(configured)) return "unknown";
        Object end = session.get("endDate"), start = session.get("startDate");
        if (end == null || start == null) throw new IllegalArgumentException("Missing activity dates");
        LocalDate today = now.atZone(QlSessionAdmissionPolicy.BUSINESS_ZONE).toLocalDate();
        if (policy.toLocalDate(end).isBefore(today)) return "completed";
        return policy.toLocalDate(start).isAfter(today) ? "upcoming" : "in_progress";
    }
}
