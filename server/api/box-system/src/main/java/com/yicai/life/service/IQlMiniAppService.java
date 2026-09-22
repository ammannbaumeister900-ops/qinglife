package com.yicai.life.service;

import com.yicai.life.domain.bo.*;

import java.util.List;
import java.util.Map;

public interface IQlMiniAppService {
    List<Map<String, Object>> listSessions();
    Map<String, Object> sessionDetail(String sessionId);
    Map<String, Object> createInvitation(String token, String sessionId, boolean staff);
    Map<String, Object> resolveInvitation(String code);
    void saveExperience(String token, QlMiniAppExperienceBo bo);
    Map<String, Object> overview(String token);
    Map<String, Object> register(String token, QlMiniAppRegistrationBo bo);
    void checkIn(String token, QlMiniAppAttendanceBo bo);
    void saveDailyRecord(String token, QlMiniAppDailyRecordBo bo);
    List<Map<String, Object>> listDailyRecords(String token);
    Map<String, Object> startHabit(String token, QlMiniAppHabitBo bo);
    Map<String, Object> changeHabit(String token, String planId, boolean paused);
    void reportPost(String token, Long publishId, QlMiniAppReportBo bo);
    void saveSubscription(String token, String templateKey, QlMiniAppSubscriptionBo bo);
}
