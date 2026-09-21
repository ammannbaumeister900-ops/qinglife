package com.yicai.life.mapper;

import org.apache.ibatis.annotations.*;
import java.util.*;

public interface QlHabitPlanMapper {
    @Select("SELECT DATE_FORMAT(start_date,'%Y-%m-%d') AS startDate, DATE_FORMAT(end_date,'%Y-%m-%d') AS endDate FROM ql_habit_pause WHERE habit_plan_id=#{id} ORDER BY start_date FOR UPDATE")
    List<Map<String,Object>> pauses(@Param("id") String id);

    @Insert("INSERT INTO ql_habit_pause(habit_plan_id,start_date,end_date) VALUES(#{id},#{today},NULL) ON DUPLICATE KEY UPDATE end_date=NULL")
    int pause(@Param("id") String id, @Param("today") String today);

    // Resuming today makes today effective unless it is itself the pause date.
    @Update("UPDATE ql_habit_pause SET end_date=GREATEST(start_date,DATE_SUB(#{today},INTERVAL 1 DAY)) WHERE habit_plan_id=#{id} AND end_date IS NULL")
    int resume(@Param("id") String id, @Param("today") String today);

    @Update("UPDATE ql_habit_plan SET current_day=#{day},status=#{status},paused_at=#{pausedAt},completed_at=#{completedAt} WHERE id=#{id}")
    int progress(@Param("id") String id,@Param("day") int day,@Param("status") String status,
                 @Param("pausedAt") Date pausedAt,@Param("completedAt") Date completedAt);

    @Select("SELECT plan_day FROM ql_habit_day_record WHERE habit_plan_id=#{id} AND status='completed' ORDER BY plan_day FOR UPDATE")
    List<Integer> completedDays(@Param("id") String id);

    @Update("UPDATE ql_habit_day_record SET status='pending',daily_record_id=NULL,completed_at=NULL WHERE habit_plan_id=#{id} AND plan_day=#{day}")
    int excludeDay(@Param("id") String id,@Param("day") int day);

    @Update("UPDATE ql_habit_day_record h JOIN ql_habit_plan p ON p.id=h.habit_plan_id JOIN ql_daily_record d ON d.customer_id=p.customer_id AND d.session_id=p.session_id AND d.record_stage='habit' AND d.record_date=#{today} AND d.plan_day=h.plan_day SET h.status='completed',h.daily_record_id=d.id,h.completed_at=#{now} WHERE h.habit_plan_id=#{id} AND h.plan_day=#{day}")
    int record(@Param("id") String id,@Param("day") int day,@Param("today") String today,@Param("now") Date now);
}