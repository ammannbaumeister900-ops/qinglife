package com.yicai.web.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The new login algorithm must never run in production without its unique keys. */
@Component
@Profile("prod")
public class ProductionSchemaGuard implements InitializingBean {
    private final JdbcTemplate jdbc;
    public ProductionSchemaGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void afterPropertiesSet() {
        requireUnique("app_user_info", "open_id");
        requireUnique("ql_customer_identifier", "legacy_app_user_id");
        Integer binary = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='app_user_info' AND column_name='open_id' AND collation_name='utf8mb4_bin'", Integer.class);
        if (binary == null || binary != 1) throw new IllegalStateException("Apply V1_3_11: OpenID must use case-sensitive collation");
    }
    private void requireUnique(String table, String column) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT index_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND non_unique=0 GROUP BY index_name HAVING COUNT(*)=1 AND MAX(column_name)=? AND MAX(sub_part) IS NULL) unique_keys", Integer.class, table, column);
        if (count == null || count < 1) throw new IllegalStateException("Missing required unique identity constraint: " + table + "." + column);
    }
}
