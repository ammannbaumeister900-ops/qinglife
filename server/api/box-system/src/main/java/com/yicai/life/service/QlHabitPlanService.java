package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlMiniAppHabitBo;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.mapper.QlHabitPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class QlHabitPlanService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final QlMiniAppMapper app;
    private final QlHabitPlanMapper habits;
    private final Clock clock;
    @Autowired public QlHabitPlanService(QlMiniAppMapper app, QlHabitPlanMapper habits) {
        this(app, habits, Clock.system(ZONE));
    }
    QlHabitPlanService(QlMiniAppMapper app, QlHabitPlanMapper habits, Clock clock) {
        this.app = app; this.habits = habits; this.clock = clock;
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    private Date now() { return Date.from(clock.instant()); }
    private static LocalDate date(Object value) {
        if (value instanceof java.sql.Date) return ((java.sql.Date)value).toLocalDate();
        if (value instanceof Date) return Instant.ofEpochMilli(((Date)value).getTime()).atZone(ZONE).toLocalDate();
        return LocalDate.parse(String.valueOf(value).substring(0,10));
    }
    private static Date timestamp(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date)value;
        if (value instanceof LocalDateTime) return Date.from(((LocalDateTime)value).atZone(ZONE).toInstant());
        throw new CustomException("计划时间格式异常");
    }
    @Transactional public Map<String,Object> latest(String customerId) {
        app.lockCustomer(customerId);
        return refresh(app.selectLatestHabit(customerId));
    }
    @Transactional public Map<String,Object> start(String customerId, QlMiniAppHabitBo bo) {
        if (bo.getPlanLength() == null || (bo.getPlanLength()!=14 && bo.getPlanLength()!=21)) throw new CustomException("计划天数只能是14天或21天");
        if (bo.getStartedAt()==null || !date(bo.getStartedAt()).equals(today())) throw new CustomException("计划只能从今天开始");
        if (bo.getSessionId()==null || app.countAttendedSession(customerId,bo.getSessionId())==0) throw new CustomException("完成本人体验后才能开始计划",403);
        app.lockCustomer(customerId);
        Map<String,Object> current = refresh(app.selectLatestHabit(customerId));
        if (current!=null && Arrays.asList("active","paused").contains(current.get("status"))) {
            if (((Number)current.get("planLength")).intValue()!=bo.getPlanLength() || !Objects.equals(current.get("sessionId"),bo.getSessionId())) throw new CustomException("已有进行中的计划，不能改成另一计划",409);
            return current;
        }
        // A repeated start request on the same calendar date cannot create a second plan.
        if (current!=null && today().equals(date(current.get("startedAt")))) throw new CustomException("今天已经创建过计划",409);
        String id=UUID.randomUUID().toString();
        app.insertHabitPlan(id,customerId,bo.getSessionId(),bo.getPlanLength(),java.sql.Date.valueOf(today()),now());
        for(int day=1;day<=bo.getPlanLength();day++) app.insertHabitDay(UUID.randomUUID().toString(),id,day,now());
        return refresh(app.selectLatestHabit(customerId));
    }
    @Transactional public Map<String,Object> change(String customerId,String planId,boolean pause) {
        app.lockCustomer(customerId);
        Map<String,Object> current=refresh(app.selectLatestHabit(customerId));
        if(current==null || !Objects.equals(planId,current.get("id"))) throw new CustomException("计划不存在或已变更",409);
        String status=String.valueOf(current.get("status"));
        if (!Arrays.asList("active","paused").contains(status)) throw new CustomException("计划已结束",409);
        if (pause && "active".equals(status)) {
            habits.pause(planId,today().toString());
            habits.excludeDay(planId,((Number)current.get("currentDay")).intValue());
            current.put("status","paused"); current.put("pausedAt",now());
        } else if (!pause && "paused".equals(status)) {
            habits.resume(planId,today().toString());
            current.put("status","active"); current.put("pausedAt",null);
        }
        return refresh(current);
    }
    public void record(Map<String,Object> plan) {
        if (!Boolean.TRUE.equals(plan.get("canRecordToday"))) throw new CustomException("今天不在可记录的计划日内");
        if(habits.record(String.valueOf(plan.get("id")),((Number)plan.get("currentDay")).intValue(),today().toString(),now())!=1) throw new CustomException("计划记录保存失败");
    }
    private Map<String,Object> refresh(Map<String,Object> original) {
        if(original==null) return null;
        Map<String,Object> plan=new LinkedHashMap<>(original);
        String id=String.valueOf(plan.get("id")), status=String.valueOf(plan.get("status"));
        List<QlHabitCalendar.Pause> pauses=new ArrayList<>();
        for(Map<String,Object> row:habits.pauses(id)) pauses.add(new QlHabitCalendar.Pause(date(row.get("startDate")),row.get("endDate")==null?null:date(row.get("endDate"))));
        QlHabitCalendar.Progress progress=QlHabitCalendar.calculate(date(plan.get("startedAt")),((Number)plan.get("planLength")).intValue(),today(),"paused".equals(status),pauses);
        if (Arrays.asList("active","paused").contains(status)) {
            if(progress.completed) { status="completed"; plan.put("completedAt",Date.from(progress.endDate.plusDays(1).atStartOfDay(ZONE).toInstant())); plan.put("pausedAt",null); }
            plan.put("status",status); plan.put("currentDay",progress.day);
            habits.progress(id,progress.day,status,timestamp(plan.get("pausedAt")),timestamp(plan.get("completedAt")));
        }
        plan.put("serverDate",today().toString());
        plan.put("canRecordToday","active".equals(status) && progress.canRecord);
        plan.put("endDate",progress.endDate==null?null:progress.endDate.toString());
        plan.put("completedDays",habits.completedDays(id));
        return plan;
    }
}