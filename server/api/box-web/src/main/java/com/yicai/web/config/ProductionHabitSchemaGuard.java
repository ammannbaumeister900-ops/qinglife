package com.yicai.web.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fail before serving requests when the calendar migration was omitted. */
@Component
@Profile("prod")
public class ProductionHabitSchemaGuard implements InitializingBean {
    private final JdbcTemplate jdbc;
    public ProductionHabitSchemaGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void afterPropertiesSet() {
        Integer columns = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ql_habit_pause' AND column_name IN ('habit_plan_id','start_date','end_date')", Integer.class);
        if (columns == null || columns != 3) throw new IllegalStateException("Apply V1_3_12: habit calendar pause table is required");
    }
}