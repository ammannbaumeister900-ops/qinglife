package com.yicai.web.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.yicai.life.mapper.AppUserInfoMapper;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.QlWechatLoginService;
import com.yicai.life.service.QlCustomerIdentityService;
import com.yicai.life.service.QlStaffWorkspaceService;
import com.yicai.life.service.QlAttendanceAudit;
import com.yicai.life.service.IQlRegistrationService;
import com.yicai.common.core.redis.RedisCache;
import org.redisson.api.RedissonClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt-in MySQL integration tests. Requires a newly created, empty qinglife_it_* database. */
class LoginConcurrencyIT {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static QlWechatLoginService login;
    static QlCustomerIdentityService identity;
    static QlStaffWorkspaceService staff;

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("QINGLIFE_TEST_MYSQL_URL");
            if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_[a-z0-9_]+\\?.*")) {
                throw new IllegalStateException("Use an isolated loopback qinglife_it_* database for these tests");
            }
            return new DriverManagerDataSource(url, "root", Objects.toString(System.getenv("QINGLIFE_TEST_MYSQL_PASSWORD"), ""));
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisConfiguration config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(AppUserInfoMapper.class);
            config.addMapper(QlMiniAppMapper.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds); factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean AppUserInfoMapper users(SqlSessionTemplate sql) { return sql.getMapper(AppUserInfoMapper.class); }
        @Bean QlMiniAppMapper mini(SqlSessionTemplate sql) { return sql.getMapper(QlMiniAppMapper.class); }
        @Bean QlWechatLoginService login(AppUserInfoMapper users) { return new QlWechatLoginService(users); }
        @Bean QlCustomerIdentityService identity(AppUserInfoMapper users, QlMiniAppMapper mini) { return new QlCustomerIdentityService(users, mini); }
        @Bean com.yicai.life.service.QlRegistrationPolicy registrationPolicy(JdbcTemplate jdbc) { return new com.yicai.life.service.QlRegistrationPolicy(jdbc); }
        @Bean QlAttendanceAudit attendanceAudit(JdbcTemplate jdbc) { return new QlAttendanceAudit(jdbc); }
        @Bean RedisCache redisCache() { return mock(RedisCache.class); }
        @Bean RedissonClient redissonClient() { return mock(RedissonClient.class); }
        @Bean IQlRegistrationService registrationService() { return mock(IQlRegistrationService.class); }
        @Bean QlStaffWorkspaceService staff(JdbcTemplate jdbc, QlAttendanceAudit audit, RedisCache redis, IQlRegistrationService registrations) { return new QlStaffWorkspaceService(jdbc, audit, redis, registrations); }
    }

    @BeforeAll static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        // No IF NOT EXISTS: accidentally reusing a database must fail, not modify prior data.
        jdbc.execute("CREATE TABLE app_user_info(id BIGINT PRIMARY KEY AUTO_INCREMENT,open_id VARCHAR(50),nick_name VARCHAR(255),status INT,insert_time DATETIME(3),last_login_time DATETIME(3),gender INT,head VARCHAR(255)) ENGINE=InnoDB");
        Path migration = Paths.get(System.getProperty("qinglife.migrations"), "V1_3_11__wechat_login_identity.sql");
        String sql = new String(Files.readAllBytes(migration), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "").trim();
        jdbc.execute(sql);
        jdbc.execute("CREATE TABLE ql_customer(id VARCHAR(36) PRIMARY KEY,customer_no VARCHAR(40) UNIQUE,nickname VARCHAR(255),real_name VARCHAR(255),city VARCHAR(100),birth_date DATE,gender VARCHAR(20),first_source VARCHAR(60),data_source VARCHAR(60),data_confidence VARCHAR(30),status VARCHAR(20),revision INT,created_at DATETIME(3),updated_at DATETIME(3),deleted_at DATETIME(3)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE ql_customer_identifier(id VARCHAR(36) PRIMARY KEY,customer_id VARCHAR(36),identifier_type VARCHAR(30),identifier_value VARCHAR(255),normalized_hash VARCHAR(64),display_hint VARCHAR(100),app_scope VARCHAR(100),is_primary BOOLEAN,verification_status VARCHAR(20),valid_from DATETIME(3),valid_to DATETIME(3),legacy_app_user_id BIGINT UNIQUE,created_at DATETIME(3),updated_at DATETIME(3),FOREIGN KEY(customer_id) REFERENCES ql_customer(id)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE ql_customer_staff_detail(customer_id VARCHAR(36) PRIMARY KEY,birth_month VARCHAR(7),referral_source VARCHAR(200)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_user(user_id BIGINT PRIMARY KEY,nick_name VARCHAR(50),status CHAR(1),del_flag CHAR(1)) ENGINE=InnoDB");
        jdbc.execute("INSERT INTO sys_user VALUES(9,'Synthetic operator','0','0')");
        jdbc.execute("CREATE TABLE ql_interaction(id VARCHAR(36) PRIMARY KEY,customer_id VARCHAR(36),session_id VARCHAR(36),channel VARCHAR(24),input_type VARCHAR(24),content_type VARCHAR(32),summary TEXT,occurred_at DATETIME(3),confirmation_status VARCHAR(24),visibility_scope VARCHAR(24),sensitivity_level INT,operator_id BIGINT,source VARCHAR(32),created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE ql_follow_up_task(id VARCHAR(36) PRIMARY KEY,customer_id VARCHAR(36),session_id VARCHAR(36),source_interaction_id VARCHAR(36),title VARCHAR(200),task_type VARCHAR(32),status VARCHAR(24),priority VARCHAR(16),assignee_user_id BIGINT,due_at DATETIME(3),result VARCHAR(1000),completed_at DATETIME(3),created_by BIGINT,created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)) ENGINE=InnoDB");
        login = context.getBean(QlWechatLoginService.class);
        identity = context.getBean(QlCustomerIdentityService.class);
        staff = context.getBean(QlStaffWorkspaceService.class);
    }
    @AfterAll static void close() { if (context != null) context.close(); }

    private <T> Set<T> concurrent(Callable<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12), start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i=0;i<12;i++) futures.add(pool.submit(() -> { ready.countDown(); start.await(); return action.call(); }));
            assertTrue(ready.await(15, TimeUnit.SECONDS)); start.countDown();
            Set<T> results = new HashSet<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void twelveConcurrentLoginsCreateOneAccount() throws Exception {
        Set<Long> ids = concurrent(() -> login.resolveUser("parallel-login").getId());
        assertEquals(1, ids.size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_info WHERE open_id='parallel-login'", Integer.class));
    }
    @Test void twelveConcurrentBindingsCreateOneCustomer() throws Exception {
        Long userId = login.resolveUser("parallel-binding").getId();
        Set<String> ids = concurrent(() -> identity.resolve(userId));
        assertEquals(1, ids.size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer_identifier WHERE legacy_app_user_id=?", Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer WHERE id=?", Integer.class, ids.iterator().next()));
    }
    @Test void failedBindingRollsBackNewCustomerAndPreservesConflict() {
        Long userId = login.resolveUser("conflicting-binding").getId();
        String customerId = identity.resolve(userId);
        jdbc.update("UPDATE ql_customer_identifier SET verification_status='conflict' WHERE legacy_app_user_id=?",userId);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer", Integer.class);
        assertThrows(RuntimeException.class, () -> identity.resolve(userId));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer", Integer.class));
        assertEquals(customerId, jdbc.queryForObject("SELECT customer_id FROM ql_customer_identifier WHERE legacy_app_user_id=?", String.class,userId));
    }
    @Test void callerRollbackAlsoRollsBackNewBinding() {
        Long userId = login.resolveUser("outer-rollback").getId();
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer", Integer.class);
        org.springframework.transaction.support.TransactionTemplate transaction =
            new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            identity.resolve(userId);
            throw new IllegalStateException("synthetic downstream failure");
        }));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ql_customer_identifier WHERE legacy_app_user_id=?", Integer.class,userId));
    }
    @Test void differentCaseOpenIdsStayDistinct() {
        Long first = login.resolveUser("case-Sensitive").getId();
        Long second = login.resolveUser("case-sensitive").getId();
        assertNotEquals(first, second);
    }
    @Test void productionGuardAcceptsTheMigratedIdentityConstraints() {
        assertDoesNotThrow(() -> new com.yicai.web.config.ProductionSchemaGuard(jdbc).afterPropertiesSet());
    }
    @Test void productionGuardRejectsMissingIdentityMigration() {
        jdbc.execute("ALTER TABLE ql_customer_identifier DROP INDEX legacy_app_user_id");
        try {
            assertThrows(IllegalStateException.class,
                () -> new com.yicai.web.config.ProductionSchemaGuard(jdbc).afterPropertiesSet());
        } finally {
            jdbc.execute("ALTER TABLE ql_customer_identifier ADD UNIQUE KEY legacy_app_user_id (legacy_app_user_id)");
        }
    }
    @Test void stoppedAccountCannotReuseExistingBinding() {
        Long id = login.resolveUser("disabled-binding").getId(); identity.resolve(id);
        jdbc.update("UPDATE app_user_info SET status=0 WHERE id=?", id);
        assertThrows(RuntimeException.class, () -> identity.resolve(id));
        assertThrows(RuntimeException.class, () -> login.resolveUser("disabled-binding"));
    }
    @Test void contactAndFollowUpCommitTogetherAndTransitionsAreValidated() {
        Long userId = login.resolveUser("staff-workflow").getId();
        String customerId = identity.resolve(userId);
        Map<String,Object> access = new HashMap<>(); access.put("sys_user_id", 9L); access.put("can_operate", 1);
        Integer contactsBeforeFailure = jdbc.queryForObject("SELECT COUNT(*) FROM ql_interaction", Integer.class);
        Map<String,Object> invalidFollowUp = new HashMap<>(); invalidFollowUp.put("title", "Must roll back"); invalidFollowUp.put("priority", "not-a-priority");
        Map<String,Object> invalidBody = new HashMap<>(); invalidBody.put("customerId", customerId); invalidBody.put("channel", "phone"); invalidBody.put("inputType", "text"); invalidBody.put("contentType", "objective_fact"); invalidBody.put("summary", "Rolled-back contact"); invalidBody.put("followUp", invalidFollowUp);
        assertThrows(RuntimeException.class, () -> staff.addContact(invalidBody, access));
        assertEquals(contactsBeforeFailure, jdbc.queryForObject("SELECT COUNT(*) FROM ql_interaction", Integer.class));
        Map<String,Object> followUp = new HashMap<>(); followUp.put("title", "Synthetic follow-up"); followUp.put("taskType", "call"); followUp.put("priority", "normal");
        Map<String,Object> body = new HashMap<>(); body.put("customerId", customerId); body.put("channel", "phone"); body.put("inputType", "text"); body.put("contentType", "objective_fact"); body.put("summary", "Synthetic contact"); body.put("followUp", followUp);
        Map<String,Object> result = staff.addContact(body, access);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ql_interaction WHERE id=?", Integer.class, result.get("interactionId")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ql_follow_up_task WHERE id=? AND source_interaction_id=?", Integer.class, result.get("followUpTaskId"), result.get("interactionId")));
        Map<String,Object> invalid = new HashMap<>(); invalid.put("status", "completed");
        assertThrows(RuntimeException.class, () -> staff.transitionFollowUp(String.valueOf(result.get("followUpTaskId")), invalid, access));
        assertEquals("pending", jdbc.queryForObject("SELECT status FROM ql_follow_up_task WHERE id=?", String.class, result.get("followUpTaskId")));
        invalid.put("result", "Reached customer");
        staff.transitionFollowUp(String.valueOf(result.get("followUpTaskId")), invalid, access);
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM ql_follow_up_task WHERE id=?", String.class, result.get("followUpTaskId")));
    }
}
