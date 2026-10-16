package com.yicai.web.integration;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.life.service.*;
import com.yicai.web.tools.DatabaseMigration;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real staff queries in a newly migrated loopback MySQL database; all rows are synthetic. */
class StaffWorkspaceIT {
    static JdbcTemplate db;
    static QlStaffWorkspaceService staff;
    static String customerId;
    @BeforeAll static void setup() throws Exception {
        String base = System.getenv("QINGLIFE_TEST_MYSQL_URL");
        if (base == null || !base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_[a-z0-9_]+\\?.*"))
            throw new IllegalStateException("New loopback test database required");
        String database = "qinglife_it_staff_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String host = base.substring(0, base.indexOf("/qinglife_it_"));
        String parameters = base.substring(base.indexOf("?"));
        String password = Objects.toString(System.getenv("QINGLIFE_TEST_MYSQL_PASSWORD"), "");
        try (Connection c = DriverManager.getConnection(host + "/" + parameters, "root", password); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(host + "/" + database + parameters, "root", password);
        db = new JdbcTemplate(ds);
        try (Connection c = ds.getConnection()) {
            DatabaseMigration.run(c, Paths.get(System.getProperty("qinglife.migrations")).getParent());
        }
        staff = new QlStaffWorkspaceService(db, mock(QlAttendanceAudit.class), mock(RedisCache.class), mock(IQlRegistrationService.class));
        customerId = UUID.randomUUID().toString();
        db.update("INSERT INTO ql_customer(id,customer_no,nickname,real_name,birth_date,city) VALUES(?,?,'测试轻友!%_','合成姓名','1990-02-03','上海')", customerId, "T" + customerId.substring(0, 20));
        db.update("INSERT INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,is_primary) VALUES(?,?,'phone','13800000000',?,1)", UUID.randomUUID().toString(), customerId, String.join("", Collections.nCopies(64, "a")));
    }
    @Test void searchSupportsNamesPhoneEmptyAndLiteralSpecialCharacters() {
        for (String query : Arrays.asList("", "轻友", "合成", "138000", "!", "%", "_")) {
            List<Map<String,Object>> rows = staff.people(query, 5);
            assertEquals(1, rows.size(), query);
            assertEquals(customerId, rows.get(0).get("id"));
        }
        assertTrue(staff.people("nonexistent", 5).isEmpty());
        assertTrue(staff.people("' OR 1=1 --", 5).isEmpty());
    }
    @Test void customerDetailAndRelatedReads() {
        assertEquals("1990-02", staff.customer(customerId).get("birthMonth"));
        db.update("INSERT INTO ql_customer_staff_detail(customer_id,birth_month,referral_source) VALUES(?,'1991-03','合成来源')", customerId);
        try {
            assertEquals("1991-03", staff.customer(customerId).get("birthMonth"));
            assertEquals("1991-03", staff.people("合成", 5).get(0).get("birthMonth"));
        } finally { db.update("DELETE FROM ql_customer_staff_detail WHERE customer_id=?", customerId); }
        assertTrue(staff.registrations(null, customerId).isEmpty());
        assertTrue(staff.passes(customerId).isEmpty());
        assertTrue(staff.interviews(customerId).isEmpty());
        assertNotNull(staff.sessions(false));
        assertNotNull(staff.sessions(true));
    }
}