package com.yicai.life.service;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlMiniAppDailyRecordBo;
import com.yicai.life.domain.bo.QlMiniAppHabitBo;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.impl.QlMiniAppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QlMiniAppDailyHabitServiceTest {
    private QlMiniAppMapper mapper;
    private QlMiniAppServiceImpl service;

    @BeforeEach void setup() {
        RedisCache redis = mock(RedisCache.class);
        QlCustomerIdentityService identity = mock(QlCustomerIdentityService.class);
        mapper = mock(QlMiniAppMapper.class);
        when(redis.getCacheObject("appToken:token")).thenReturn(7L);
        when(identity.resolve(7L)).thenReturn("customer-1");
        service = new QlMiniAppServiceImpl(redis, identity, mapper, mock(QlSessionPricing.class),
                new QlHabitPlanService(mapper, mock(com.yicai.life.mapper.QlHabitPlanMapper.class)),
                new QlSessionAdmissionPolicy());
    }

    @Test void todayEndpointRejectsClientForgedDate() {
        QlMiniAppDailyRecordBo bo = daily("general", 0);
        bo.setRecordDate(new Date(System.currentTimeMillis() - 2L * 86400000L));
        assertThrows(CustomException.class, () -> service.saveDailyRecord("token", bo));
        verify(mapper, never()).upsertDailyRecord(anyString(), anyString(), any(), any(), anyString(), anyInt(), anyString(), any(), any());
    }

    @Test void habitRecordUsesServerPlanDayAndRejectsMismatch() {
        Map<String,Object> plan = new HashMap<>();
        plan.put("startedAt", new Date(System.currentTimeMillis() - 2L * 86400000L)); plan.put("id", "plan-1"); plan.put("sessionId", "session-1"); plan.put("planLength", 14); plan.put("currentDay", 3); plan.put("status", "active");
        when(mapper.selectLatestHabit("customer-1")).thenReturn(plan);
        QlMiniAppDailyRecordBo bo = daily("habit", 4);
        assertThrows(CustomException.class, () -> service.saveDailyRecord("token", bo));
        verify(mapper, never()).upsertDailyRecord(anyString(), anyString(), any(), any(), anyString(), anyInt(), anyString(), any(), any());
    }

    @Test void startHabitRequiresActualParticipation() {
        QlMiniAppHabitBo bo = habit(14);
        when(mapper.countAttendedSession("customer-1", "session-1")).thenReturn(0);
        assertThrows(CustomException.class, () -> service.startHabit("token", bo));
        verify(mapper, never()).insertHabitPlan(anyString(), anyString(), any(), anyInt(), any(), any());
    }

    @Test void activePlanCannotSilentlyChangeLength() {
        QlMiniAppHabitBo bo = habit(21);
        when(mapper.countAttendedSession("customer-1", "session-1")).thenReturn(1);
        Map<String,Object> current = new HashMap<>();
        current.put("startedAt", new Date()); current.put("currentDay", 1); current.put("id", "plan-1"); current.put("sessionId", "session-1"); current.put("planLength", 14); current.put("status", "active");
        when(mapper.selectLatestHabit("customer-1")).thenReturn(current);
        assertThrows(CustomException.class, () -> service.startHabit("token", bo));
        verify(mapper, never()).insertHabitPlan(anyString(), anyString(), any(), anyInt(), any(), any());
    }

    private QlMiniAppDailyRecordBo daily(String stage, int day) {
        QlMiniAppDailyRecordBo bo = new QlMiniAppDailyRecordBo();
        bo.setRecordDate(new Date()); bo.setRecordStage(stage); bo.setPlanDay(day); bo.setChoiceValue("done");
        return bo;
    }

    private QlMiniAppHabitBo habit(int length) {
        QlMiniAppHabitBo bo = new QlMiniAppHabitBo();
        bo.setSessionId("session-1"); bo.setPlanLength(length); bo.setStartedAt(new Date());
        return bo;
    }
}
