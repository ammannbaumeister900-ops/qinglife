package com.yicai.life.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;

import com.yicai.life.domain.bo.*;
import com.yicai.life.service.QlCustomerIdentityService;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.IQlMiniAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class QlMiniAppServiceImpl implements IQlMiniAppService {
    private static final String APP_TOKEN_PREFIX = "appToken:";
    private static final String CONSENT_POLICY_VERSION = "2026-09-15";
    private static final Set<String> DAILY_STAGES = new HashSet<>(Arrays.asList("refeed", "habit", "general"));
    private static final Set<String> DAILY_CHOICES = new HashSet<>(Arrays.asList("done", "light", "rest"));
    private static final Set<String> SUBSCRIPTION_STATUSES = new HashSet<>(Arrays.asList("enabled", "disabled", "rejected"));

    @Autowired private com.yicai.life.service.QlRegistrationPolicy policy;
    private final RedisCache redisCache;
    private final QlCustomerIdentityService identityService;
    private final QlMiniAppMapper miniAppMapper;
    private final com.yicai.life.service.QlSessionPricing pricing;
    private final com.yicai.life.service.QlHabitPlanService habitPlans;

    @Override
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> sessions = miniAppMapper.selectPublicSessions();
        if (sessions.isEmpty()) return sessions;
        List<String> sessionIds = new ArrayList<>();
        for (Map<String, Object> session : sessions) sessionIds.add(String.valueOf(session.get("id")));
        Map<String, List<Map<String, Object>>> daysBySession = new HashMap<>();
        for (Map<String, Object> day : miniAppMapper.selectSessionDaysBySessionIds(sessionIds)) {
            String sessionId = String.valueOf(day.remove("sessionId"));
            daysBySession.computeIfAbsent(sessionId, key -> new ArrayList<>()).add(day);
        }
        for (Map<String, Object> session : sessions) {
            session.put("days", daysBySession.getOrDefault(String.valueOf(session.get("id")), Collections.emptyList()));
        }
        return sessions;
    }
    @Override
    public Map<String, Object> overview(String token) {
        String customerId = requireCustomerId(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("profile", miniAppMapper.selectCustomerProfile(customerId));
        result.put("registrations", miniAppMapper.selectCustomerRegistrations(customerId));
        result.put("attendance", miniAppMapper.selectCustomerAttendance(customerId));
        result.put("dailyRecords", miniAppMapper.selectDailyRecords(customerId));
        result.put("experienceRecords", miniAppMapper.selectExperienceRecords(customerId));
        result.put("habit", habitPlans.latest(customerId));
        result.put("returningEligible", miniAppMapper.countCompletedSessions(customerId) > 0);
        result.put("invitationEligible", miniAppMapper.countCompletedExperience(customerId) > 0);
        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> register(String token, QlMiniAppRegistrationBo bo) {
        String buyerId = requireCustomerId(token);
        if (!Boolean.TRUE.equals(bo.getServiceConsent())) {
            throw new CustomException("请先同意服务必要信息处理");
        }
        if (bo.getParticipants().size() > 10) {
            throw new CustomException("一次最多登记10位参与人");
        }
        if (bo.getClientRequestId().length() > 64) throw new CustomException("请求编号过长");
        String scopedRequestId = DigestUtil.sha256Hex(buyerId + ":" + bo.getClientRequestId());
        String fingerprint = DigestUtil.sha256Hex(cn.hutool.json.JSONUtil.toJsonStr(bo));
        Map<String, Object> existing = miniAppMapper.selectBatchByClientRequestId(scopedRequestId);
        if (existing != null) {
            return replayRegistration(existing, fingerprint);
        }
        Map<String, Object> session = miniAppMapper.selectSessionForRegistration(bo.getSessionId());
        if (session == null || !"open".equals(String.valueOf(session.get("status")))) {
            throw new CustomException("当前期次未开放报名");
        }
        existing = miniAppMapper.selectBatchByClientRequestId(scopedRequestId);
        if (existing != null) return replayRegistration(existing, fingerprint);
        policy.admit(bo.getSessionId(), bo.getParticipants().size(), true);
        Date requestTime = new Date();

        String referrerId = null;
        if (StrUtil.isNotBlank(bo.getInvitationCode())) {
            Map<String,Object> invitation = miniAppMapper.selectInvitation(bo.getInvitationCode());
            if (invitation == null || !bo.getSessionId().equals(String.valueOf(invitation.get("sessionId")))) throw new CustomException("邀请与当前活动不匹配");
            if (invitation.get("ownerId") != null) referrerId = String.valueOf(invitation.get("ownerId"));
        }
        Date now = requestTime;
        String batchId = uuid();
        String orderNo = orderNo(now);
        BigDecimal newPrice = session.get("standardPrice") == null ? null : new BigDecimal(String.valueOf(session.get("standardPrice")));
        BigDecimal returningPrice = session.get("returningPrice") == null ? null : new BigDecimal(String.valueOf(session.get("returningPrice")));
        BigDecimal amount = BigDecimal.ZERO;
        miniAppMapper.insertRegistrationBatch(batchId, orderNo, bo.getSessionId(), buyerId,
                scopedRequestId, bo.getParticipants().size(), amount, now);
        miniAppMapper.setRequestFingerprint(batchId, fingerprint);
        miniAppMapper.insertServiceConsent(uuid(), buyerId, CONSENT_POLICY_VERSION, now);

        String registrationStatus = "auto".equals(String.valueOf(session.get("confirmMode"))) ? "confirmed" : "pending";
        boolean selfUsed = false;
        Set<String> participantIds = new HashSet<>();
        List<Map<String, Object>> registrations = new ArrayList<>();
        List<Map<String, Object>> days = miniAppMapper.selectSessionDays(bo.getSessionId());
        for (QlMiniAppRegistrationBo.Participant participant : bo.getParticipants()) {
            boolean isSelf = Boolean.TRUE.equals(participant.getSelf());
            if (!Boolean.TRUE.equals(participant.getMinor()) && (participant.getPhone() == null || !participant.getPhone().matches("^1\\d{10}$"))) throw new CustomException("成年人必须填写本人手机号");
            if (isSelf && selfUsed) {
                throw new CustomException("本人只能登记一次");
            }
            String participantCustomerId;
            if (isSelf) {
                participantCustomerId = buyerId;
                selfUsed = true;
                if (StrUtil.isNotBlank(participant.getPhone())) {
                    String phone = participant.getPhone().trim(), hash = DigestUtil.sha256Hex(phone);
                    List<String> candidates = miniAppMapper.selectPhoneCandidates(hash);
                    if (candidates.stream().anyMatch(id -> !id.equals(buyerId))) throw new CustomException("该手机号涉及已有档案，请联系工作人员核验");
                    if (!candidates.contains(buyerId)) miniAppMapper.insertIdentifier(uuid(), buyerId, "phone", phone, hash,
                            phone.substring(0,3) + "****" + phone.substring(7), null, false, "unverified", null, now);
                }
            } else {
                if (StrUtil.isNotBlank(participant.getCustomerId())) {
                    participantCustomerId = miniAppMapper.selectOwnedParticipant(participant.getCustomerId(), buyerId);
                    if (participantCustomerId == null) throw new CustomException("无权使用该参与人", 403);
                } else {
                    participantCustomerId = createParticipant(participant, buyerId, now);
                }
            }
            if (!participantIds.add(participantCustomerId) || miniAppMapper.countRegistration(participantCustomerId, bo.getSessionId()) > 0) throw new CustomException("该参与人已报名本期", 409);
            String effectiveReferrer = participantCustomerId.equals(referrerId) ? null : referrerId;
            if (effectiveReferrer != null) miniAppMapper.setFirstReferrer(participantCustomerId, effectiveReferrer);
            BigDecimal unitPrice = pricing.price(participantCustomerId, newPrice, returningPrice);
            amount = amount.add(unitPrice);
            String registrationId = uuid();
            miniAppMapper.insertRegistration(registrationId, batchId, participantCustomerId, bo.getSessionId(),
                    registrationStatus, bo.getMotivation(), StrUtil.blankToDefault(participant.getRelation(), isSelf ? "本人" : "其他"),
                    Boolean.TRUE.equals(participant.getMinor()), now);
            miniAppMapper.setRegistrationPrice(registrationId, unitPrice);
            if (effectiveReferrer != null) miniAppMapper.setSessionReferrer(registrationId, effectiveReferrer);
            miniAppMapper.insertRegistrationStatusLog(uuid(), registrationId, registrationStatus,
                    "小程序提交报名", buyerId, now);
            for (Map<String, Object> day : days) {
                miniAppMapper.insertParticipationDay(uuid(), registrationId, String.valueOf(day.get("id")),
                        participantCustomerId, now);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("registrationId", registrationId);
            item.put("customerId", participantCustomerId);
            item.put("name", participant.getName());
            item.put("status", registrationStatus);
            item.put("unitPrice", unitPrice);
            registrations.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        miniAppMapper.setBatchAmount(batchId, amount);
        result.put("id", batchId);
        result.put("orderNo", orderNo);
        result.put("paymentStatus", "unpaid");
        result.put("payableAmount", amount);
        result.put("registrationStatus", registrationStatus);
        result.put("registrations", registrations);
        return result;
    }

    @Override
    @Transactional
    public void checkIn(String token, QlMiniAppAttendanceBo bo) {
        String customerId = requireCustomerId(token);
        policy.lockRegistration(bo.getRegistrationId());
        int rows = miniAppMapper.checkIn(bo.getRegistrationId(), bo.getSessionDayId(), customerId, new Date());
        if (rows != 1) {
            throw new CustomException("签到失败：请确认报名已通过且今日尚未签到");
        }
    }

    @Override
    @Transactional
    public void saveDailyRecord(String token, QlMiniAppDailyRecordBo bo) {
        String customerId = requireCustomerId(token);
        if (!DAILY_STAGES.contains(bo.getRecordStage()) || !DAILY_CHOICES.contains(bo.getChoiceValue())) {
            throw new CustomException("记录类型不合法");
        }
        Date today = new Date();
        if (bo.getRecordDate() == null || !dateKey(today).equals(dateKey(bo.getRecordDate()))) {
            throw new CustomException("today 接口只能保存今天的记录");
        }
        String sessionId = bo.getSessionId();
        int planDay = 0;
        Map<String,Object> habit = null;
        if ("habit".equals(bo.getRecordStage())) {
            habit = habitPlans.latest(customerId);
            if (habit == null || !"active".equals(String.valueOf(habit.get("status")))) throw new CustomException("当前没有进行中的习惯计划");
            if (!Boolean.TRUE.equals(habit.get("canRecordToday"))) throw new CustomException("今天不在可记录的计划日内");
            sessionId = habit.get("sessionId") == null ? null : String.valueOf(habit.get("sessionId"));
            planDay = number(habit.get("currentDay")).intValue();
            if (planDay < 1 || planDay > number(habit.get("planLength")).intValue()) throw new CustomException("计划天数状态异常");
            if (bo.getPlanDay() != null && bo.getPlanDay() != planDay) throw new CustomException("计划天数与服务端进度不一致");
        } else if ("refeed".equals(bo.getRecordStage())) {
            if (StrUtil.isBlank(sessionId) || miniAppMapper.countAttendedSession(customerId, sessionId) == 0) throw new CustomException("只能记录本人实际参与过的活动");
        } else {
            sessionId = null;
        }
        miniAppMapper.upsertDailyRecord(uuid(), customerId, sessionId, today,
                bo.getRecordStage(), planDay,
                bo.getChoiceValue(), bo.getNote(), new Date());
        if (habit != null) habitPlans.record(habit);
    }

    @Override
    public List<Map<String, Object>> listDailyRecords(String token) {
        return miniAppMapper.selectDailyRecords(requireCustomerId(token));
    }

    @Override
    @Transactional
    public Map<String, Object> startHabit(String token, QlMiniAppHabitBo bo) {
        return habitPlans.start(requireCustomerId(token), bo);
    }

    @Override
    public Map<String,Object> changeHabit(String token, String planId, boolean paused) {
        return habitPlans.change(requireCustomerId(token), planId, paused);
    }
    @Override
    @Transactional
    public void reportPost(String token, Long publishId, QlMiniAppReportBo bo) {
        String customerId = requireCustomerId(token);
        try {
            miniAppMapper.insertPostReport(uuid(), publishId, customerId,
                    StrUtil.blankToDefault(bo.getReasonCode(), "other"), bo.getReasonNote(), new Date());
        } catch (DuplicateKeyException ignored) {
            // 同一轻友重复举报同一内容视为幂等成功。
        }
    }

    @Override
    @Transactional
    public void saveSubscription(String token, String templateKey, QlMiniAppSubscriptionBo bo) {
        String customerId = requireCustomerId(token);
        if (!SUBSCRIPTION_STATUSES.contains(bo.getStatus())) {
            throw new CustomException("订阅状态不合法");
        }
        Date now = new Date();
        miniAppMapper.upsertSubscription(uuid(), customerId, templateKey, bo.getStatus(),
                "enabled".equals(bo.getStatus()) ? now : null,
                "disabled".equals(bo.getStatus()) ? now : null, now);
    }


    private Map<String,Object> replayRegistration(Map<String,Object> batch, String fingerprint) {
        if (!fingerprint.equals(batch.get("requestFingerprint"))) throw new CustomException("请求编号已用于不同报名", 409);
        List<Map<String,Object>> rows = miniAppMapper.selectBatchRegistrations(String.valueOf(batch.get("id")));
        batch.remove("requestFingerprint");
        batch.put("registrations", rows);
        batch.put("registrationStatus", rows.isEmpty() ? "pending" : rows.get(0).get("status"));
        return batch;
    }

    @Override
    public Map<String,Object> sessionDetail(String sessionId) {
        Map<String,Object> session = miniAppMapper.selectSessionDetail(sessionId);
        if (session == null) throw new CustomException("活动不存在或未发布", 404);
        session.put("days", miniAppMapper.selectSessionDays(sessionId));
        return session;
    }

    private Map<String,Object> requireOpenInvitationSession(String sessionId) {
        Map<String,Object> session = sessionDetail(sessionId);
        Date now = new Date();
        if (!"open".equals(session.get("status")) || (session.get("registrationOpenAt") != null && now.before(asDate(session.get("registrationOpenAt")))) || (session.get("registrationCloseAt") != null && dateKey(now).compareTo(dateKey(asDate(session.get("registrationCloseAt")))) > 0) || dateKey(now).compareTo(dateKey(asDate(session.get("endDate")))) > 0) throw new CustomException("活动尚未开放、已截止或已取消");
        return session;
    }

    @Override
    @Transactional
    public Map<String,Object> createInvitation(String token, String sessionId, boolean staff) {
        String ownerId = staff ? null : requireCustomerId(token);
        if (!staff && miniAppMapper.countCompletedExperience(ownerId) == 0) throw new CustomException("完成本人体验后可生成邀请", 403);
        requireOpenInvitationSession(sessionId);
        String code = uuid().replace("-", "");
        miniAppMapper.insertInvitation(code, sessionId, ownerId, staff ? "staff" : "referral", new Date());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("code", code); result.put("sessionId", sessionId);
        result.put("path", "/pages/camp-flow/index?view=detail&id=" + sessionId + "&invite=" + code);
        return result;
    }

    @Override
    public Map<String,Object> resolveInvitation(String code) {
        Map<String,Object> invitation = miniAppMapper.selectInvitation(code);
        if (invitation == null) throw new CustomException("邀请无效", 404);
        String sessionId = String.valueOf(invitation.get("sessionId"));
        requireOpenInvitationSession(sessionId);
        Map<String,Object> result = new LinkedHashMap<>(); result.put("sessionId", sessionId); return result;
    }

    private Date asDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date) value;
        if (value instanceof java.time.LocalDateTime) return Date.from(((java.time.LocalDateTime)value).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        if (value instanceof java.time.LocalDate) return Date.from(((java.time.LocalDate)value).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        throw new CustomException("活动日期格式异常，请联系工作人员");
    }

    private String dateKey(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(date);
    }

    @Override
    @Transactional
    public void saveExperience(String token, QlMiniAppExperienceBo bo) {
        String customerId = requireCustomerId(token);
        Map<String,Object> owner = miniAppMapper.selectExperienceOwner(bo.getRegistrationId(), customerId);
        if (owner == null) throw new CustomException("只能记录本人已确认活动的感受", 403);
        String phase = bo.getPhase(), sessionId = String.valueOf(owner.get("sessionId"));
        String today = dateKey(new Date()), nodeKey = "";
        if ("before".equals(phase)) {
            if (today.compareTo(dateKey(asDate(owner.get("startDate")))) >= 0) throw new CustomException("活动前记录已结束");
        } else if ("during".equals(phase)) {
            boolean validDay = false;
            for (Map<String,Object> day : miniAppMapper.selectSessionDays(sessionId)) {
                if (String.valueOf(day.get("id")).equals(bo.getSessionDayId()) && dateKey(asDate(day.get("activityDate"))).equals(today)) validDay = true;
            }
            if (!validDay) throw new CustomException("只能保存当天活动感受");
            nodeKey = bo.getSessionDayId();
        } else if ("after".equals(phase)) {
            if (!"completed".equals(owner.get("sessionStatus")) && today.compareTo(dateKey(asDate(owner.get("endDate")))) <= 0) throw new CustomException("活动尚未结束");
        } else throw new CustomException("记录阶段无效");
        miniAppMapper.upsertExperience(uuid(), bo.getRegistrationId(), customerId, sessionId, phase, nodeKey, bo.getEnergy(), bo.getRelaxation(), bo.getNote(), new Date());
    }

    private String requireCustomerId(String token) {
        if (StrUtil.isBlank(token)) {
            throw new CustomException("请先登录", 401);
        }
        Object cachedUserId = redisCache.getCacheObject(APP_TOKEN_PREFIX + token);
        if (cachedUserId == null) {
            throw new CustomException("登录已失效，请重新登录", 401);
        }
        Long legacyUserId;
        try {
            legacyUserId = Long.valueOf(String.valueOf(cachedUserId));
        } catch (NumberFormatException e) {
            throw new CustomException("登录信息无效", 401);
        }
        return identityService.resolve(legacyUserId);
    }

    private String createParticipant(QlMiniAppRegistrationBo.Participant participant, String buyerId, Date now) {
        if (StrUtil.isNotBlank(participant.getPhone())) {
            List<String> candidates = miniAppMapper.selectPhoneCandidates(DigestUtil.sha256Hex(participant.getPhone().replaceAll("\\s+", "")));
            if (candidates.size() == 1 && miniAppMapper.selectOwnedParticipant(candidates.get(0), buyerId) != null) return candidates.get(0);
            if (!candidates.isEmpty()) throw new CustomException("手机号存在历史档案，请由工作人员核对，不自动合并");
        }
        String customerId = uuid();
        miniAppMapper.insertParticipantCustomer(customerId, customerNo(), participant.getName(), now);
        if (StrUtil.isNotBlank(participant.getPhone())) {
            String normalized = participant.getPhone().replaceAll("\\s+", "");
            String hint = normalized.length() >= 7
                    ? normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4) : "***";
            miniAppMapper.insertIdentifier(uuid(), customerId, "phone", normalized,
                    DigestUtil.sha256Hex(normalized), hint, null, true, "unverified", null, now);
        }
        return customerId;
    }

    private Number number(Object value) {
        return value instanceof Number ? (Number) value : Integer.valueOf(String.valueOf(value));
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private String customerNo() {
        return "QL" + new SimpleDateFormat("yyMMddHHmmss").format(new Date())
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String orderNo(Date now) {
        return "QL" + new SimpleDateFormat("yyyyMMddHHmmss").format(now)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
