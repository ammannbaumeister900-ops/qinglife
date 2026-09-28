package com.yicai.web.integration;

import com.yicai.web.tools.DatabaseMigration;
import com.yicai.life.domain.QlRegistration;
import com.yicai.life.service.*;
import com.yicai.life.service.impl.*;
import com.yicai.life.mapper.*;
import com.yicai.life.domain.bo.*;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.redisson.api.RedissonClient;
import javax.sql.DataSource;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real migrated MySQL + Spring services. Redis/Wechat are deliberately mocked in this suite. */
class RegistrationReadinessIT {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate db;
    static IQlRegistrationService registrations;
    static QlStaffWorkspaceService staff;
    static IQlMiniAppService mini;
    static QlPassService passes;
    static int number=5000;
    static final Map<String,Object> access=new HashMap<>();
    @Configuration @EnableTransactionManagement
    @Import({QlRegistrationPolicy.class,QlSessionAdmissionPolicy.class,QlRegistrationServiceImpl.class,QlSessionPricing.class,QlMiniAppServiceImpl.class,QlStaffWorkspaceService.class,QlAttendanceAudit.class,QlHabitPlanService.class,QlPassService.class,QlFriendAssessmentService.class})
    static class Config {
        @Bean DataSource dataSource() {
            String url=System.getenv("QINGLIFE_TEST_MYSQL_URL");
            if(url==null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_[a-z0-9_]+\\?.*"))throw new IllegalStateException("Isolated loopback database required");
            return new DriverManagerDataSource(url.replace("?","_registration?"),"root",Objects.toString(System.getenv("QINGLIFE_TEST_MYSQL_PASSWORD"),""));
        }
        @Bean PlatformTransactionManager tx(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean SqlSessionFactory factory(DataSource ds)throws Exception {
            MybatisConfiguration config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
            config.addMapper(QlRegistrationMapper.class);config.addMapper(QlSessionMapper.class);config.addMapper(QlMiniAppMapper.class);config.addMapper(QlHabitPlanMapper.class);
            config.addMapper(QlRegistrationStatusLogMapper.class);config.addMapper(QlTransactionMapper.class);
            MybatisSqlSessionFactoryBean f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(config);
            f.setTypeAliasesPackage("com.yicai.life.domain");
            f.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/life/*.xml"));return f.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean QlRegistrationMapper registrations(SqlSessionTemplate s){return s.getMapper(QlRegistrationMapper.class);}
        @Bean QlSessionMapper sessions(SqlSessionTemplate s){return s.getMapper(QlSessionMapper.class);}
        @Bean QlMiniAppMapper mini(SqlSessionTemplate s){return s.getMapper(QlMiniAppMapper.class);}
        @Bean QlHabitPlanMapper habits(SqlSessionTemplate s){return s.getMapper(QlHabitPlanMapper.class);}
        @Bean QlRegistrationStatusLogMapper logs(SqlSessionTemplate s){return s.getMapper(QlRegistrationStatusLogMapper.class);}
        @Bean QlTransactionMapper transactions(SqlSessionTemplate s){return s.getMapper(QlTransactionMapper.class);}
        @Bean RedisCache redis(){return mock(RedisCache.class);}
        @Bean RedissonClient redisson(){return mock(RedissonClient.class);}
        @Bean QlCustomerIdentityService identity(){return mock(QlCustomerIdentityService.class);}
    }
    @BeforeAll static void setup()throws Exception {
        context=new AnnotationConfigApplicationContext(Config.class);db=context.getBean(JdbcTemplate.class);
        try(Connection c=context.getBean(DataSource.class).getConnection()){DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());}
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,status,del_flag) VALUES(9,0,'synthetic','Synthetic','00','0','0')");
        registrations=context.getBean(IQlRegistrationService.class);staff=context.getBean(QlStaffWorkspaceService.class);mini=context.getBean(IQlMiniAppService.class);passes=context.getBean(QlPassService.class);
        access.put("sys_user_id",9L);access.put("can_operate",1);access.put("can_payment",1);
    }
    @AfterAll static void close(){if(context!=null)context.close();}
    String customer(){String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_customer(id,customer_no,nickname) VALUES(?,?,?)",id,"T"+id.replace("-","").substring(0,20),"Synthetic");return id;}
    String session(int capacity){String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_session(id,session_number,name,start_date,end_date,capacity,status,standard_price,returning_price) VALUES(?,?,?,CURRENT_DATE(),DATE_ADD(CURRENT_DATE(),INTERVAL 2 DAY),?,'open',100,50)",id,++number,"Synthetic",capacity);return id;}
    String add(String customer,String session,String status){QlRegistrationBo b=new QlRegistrationBo();b.setCustomerId(customer);b.setSessionId(session);b.setRegistrationStatus(status);registrations.insertByBo(b,9L);return db.queryForObject("SELECT id FROM ql_registration WHERE customer_id=? AND session_id=?",String.class,customer,session);}
    void status(String id,String status){QlRegistrationBo b=new QlRegistrationBo();b.setId(id);b.setRegistrationStatus(status);registrations.updateByBo(b,9L);}
    QlPaymentBo payment(String state,String amount){QlPaymentBo b=new QlPaymentBo();b.setPaymentStatus(state);b.setAmount(new BigDecimal(amount));b.setPaymentMethod("cash");b.setChangeReason("Synthetic correction");return b;}
    void settle(String id,String amount){QlSettlementBo b=new QlSettlementBo();b.setFinalAmount(new BigDecimal(amount));b.setSettlementType("money");b.setNote("Synthetic settlement");registrations.confirmSettlement(id,b,9L);}
    String pass(String customer,int units){QlPassAccountBo b=new QlPassAccountBo();b.setCustomerId(customer);b.setPassType("Synthetic card");b.setInitialUnits(units);b.setReason("Synthetic verified opening");return passes.open(b,9L);}    void settlePass(String id,String accountId,int units){QlSettlementBo b=new QlSettlementBo();b.setFinalAmount(BigDecimal.ZERO);b.setSettlementType("pass");b.setPassAccountId(accountId);b.setPassUnits(units);b.setNote("Synthetic pass settlement");registrations.confirmSettlement(id,b,9L);}
    String currentSettlementId(String registrationId){QlRegistration current=registrations.getById(registrationId);if(current==null)throw new AssertionError("Registration disappeared");if(current.getBatchId()!=null)return db.queryForObject("SELECT current_settlement_id FROM ql_registration_batch WHERE id=?",String.class,current.getBatchId());return current.getCurrentSettlementId();}
    String batch(String buyer,String sessionId,String... registrationIds){String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_registration_batch(id,order_no,session_id,submitted_by_customer_id,source,participant_count,submitted_at,quoted_amount,payable_amount,payment_status) VALUES(?,?,?,?,'mini_program',?,NOW(),200,200,'unpaid')",id,"T"+id.replace("-","").substring(0,20),sessionId,buyer,registrationIds.length);for(String registrationId:registrationIds)db.update("UPDATE ql_registration SET batch_id=? WHERE id=?",id,registrationId);return id;}
    void revoke(String registrationId,String settlementId){registrations.revokeSettlement(registrationId,settlementId,"Synthetic settlement correction",9L);}
    @SuppressWarnings("unchecked") Map<String,Object> miniRegistration(String token,String registrationId){List<Map<String,Object>> rows=(List<Map<String,Object>>)mini.overview(token).get("registrations");return rows.stream().filter(row->registrationId.equals(row.get("registrationId"))).findFirst().orElseThrow(()->new AssertionError("Miniapp registration missing"));}
    @Test @SuppressWarnings("unchecked") void publicSessionsIncludeScheduledDays() {
        String sessionId=session(2);
        db.update("INSERT INTO ql_session_day(id,session_id,day_no,activity_date,status) VALUES(?,?,1,CURRENT_DATE(),'scheduled')",UUID.randomUUID().toString(),sessionId);
        Map<String,Object> found=mini.listSessions().stream().filter(row->sessionId.equals(row.get("id"))).findFirst().orElseThrow(AssertionError::new);
        List<Map<String,Object>> days=(List<Map<String,Object>>)found.get("days");
        assertEquals(1,days.size());
        assertEquals(1,((Number)days.get(0).get("dayNo")).intValue());
    }
    @Test void completeAdminBusinessNavigationIsSeeded() {
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2054,2055,2056,2057) AND parent_id=0 AND visible='0'",Integer.class));
        assertEquals(10,db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE component IN ('life/collect/index','life/contact/index','life/essay/index','life/label/index','life/tag/index','life/userComment/index','life/userMessage/index','life/userPublish/index','life/publishTemplate/index','life/publishTemplateProject/index') AND visible='0'",Integer.class));
        assertEquals("contentPublishing",db.queryForObject("SELECT path FROM sys_menu WHERE menu_id=2055",String.class));
        assertEquals("customerMoments",db.queryForObject("SELECT path FROM sys_menu WHERE menu_id=2056",String.class));
    }
    @Test void migratedSchemaCanBeReplayedAndTamperingFails()throws Exception {
        try(Connection c=context.getBean(DataSource.class).getConnection()) {
            Path server=Paths.get(System.getProperty("qinglife.migrations")).getParent();DatabaseMigration.run(c,server);
            assertEquals(DatabaseMigration.files(server).size(),db.queryForObject("SELECT COUNT(*) FROM ql_migration_run WHERE state='complete'",Integer.class));
            String name="V1_3_11__wechat_login_identity.sql";String hash=db.queryForObject("SELECT sha256 FROM ql_migration_run WHERE name=?",String.class,name);
            db.update("UPDATE ql_migration_run SET sha256=? WHERE name=?",String.join("",Collections.nCopies(64,"0")),name);
            try{assertThrows(IllegalStateException.class,()->DatabaseMigration.run(c,server));}finally{db.update("UPDATE ql_migration_run SET sha256=? WHERE name=?",hash,name);}
        }
    }
    @Test void settlementReversalMenuIsIsolatedAndAttendanceMenusRemainUnchanged() {
        Map<String,Object> attendance=db.queryForMap("SELECT menu_name AS menuName,menu_type AS menuType,path,component,perms FROM sys_menu WHERE menu_id=2214");
        assertEquals("活动签到",attendance.get("menuName"));
        assertEquals("C",attendance.get("menuType"));
        assertEquals("attendance",attendance.get("path"));
        assertEquals("life/attendance/index",attendance.get("component"));
        assertEquals("life:attendance:list",attendance.get("perms"));
        assertEquals(Arrays.asList(2215L,2216L),db.queryForList("SELECT menu_id FROM sys_menu WHERE parent_id=2214 ORDER BY menu_id",Long.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_id=2215 AND menu_name='签到查询' AND menu_type='F' AND parent_id=2214 AND perms='life:attendance:list'",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_id=2216 AND menu_name='签到修改' AND menu_type='F' AND parent_id=2214 AND perms='life:attendance:edit'",Integer.class));

        Map<String,Object> revoke=db.queryForMap("SELECT menu_name AS menuName,parent_id AS parentId,menu_type AS menuType,perms FROM sys_menu WHERE menu_id=2400");
        assertEquals("撤销结算",revoke.get("menuName"));
        assertEquals(2209,((Number)revoke.get("parentId")).intValue());
        assertEquals("F",revoke.get("menuType"));
        assertEquals("life:registration:settlement:revoke",revoke.get("perms"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms='life:registration:settlement:revoke'",Integer.class));
        assertEquals(Collections.singletonList("ql_admin"),db.queryForList(
                "SELECT r.role_key FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE rm.menu_id=2400 ORDER BY r.role_key",String.class));
        for(String roleKey:Arrays.asList("ql_operator","ql_leader","ql_finance")) {
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE r.role_key=? AND rm.menu_id=2400",Integer.class,roleKey));
        }

        Map<String,List<Long>> expectedAttendance=new LinkedHashMap<>();
        expectedAttendance.put("ql_admin",Arrays.asList(2214L,2215L,2216L));
        expectedAttendance.put("ql_operator",Arrays.asList(2214L,2215L,2216L));
        expectedAttendance.put("ql_leader",Arrays.asList(2214L,2215L));
        expectedAttendance.put("ql_finance",Arrays.asList(2214L,2215L));
        for(Map.Entry<String,List<Long>> expected:expectedAttendance.entrySet()) {
            List<Long> actual=db.queryForList("SELECT rm.menu_id FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE r.role_key=? AND rm.menu_id BETWEEN 2214 AND 2216 ORDER BY rm.menu_id",Long.class,expected.getKey());
            assertEquals(expected.getValue(),actual,"attendance role-menu grants for "+expected.getKey());
        }
    }
    @Test void pendingSeatCanConfirmWhenFullButWaitlistCannot(){String s=session(1),r=add(customer(),s,"pending"),w=add(customer(),s,"waitlisted");staff.confirm(r,access);assertThrows(CustomException.class,()->status(w,"confirmed"));assertThrows(CustomException.class,()->add(customer(),s,"pending"));}
    @Test void friendAssessmentIsAppendOnlyIdempotentAndMenusProtectSensitiveData() {
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ql_friend_assessment'",Integer.class));
        assertEquals(Arrays.asList(2230L,2231L,2232L),db.queryForList("SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 2230 AND 2232 ORDER BY menu_id",Long.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE r.role_key='ql_operator' AND rm.menu_id IN (2231,2232)",Integer.class));
        String customer=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:friend-token")).thenReturn(99L);when(identity.resolve(99L)).thenReturn(customer);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);QlFriendAssessmentBo form=assessment();
        Map<String,Object> first=service.submit("friend-token",form),retry=service.submit("friend-token",form);
        assertEquals(first.get("assessmentId"),retry.get("assessmentId"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,customer));
        form.setClientRequestId(UUID.randomUUID().toString());service.submit("friend-token",form);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,customer));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_consent_record WHERE customer_id=? AND consent_type='sensitive_profile'",Integer.class,customer));
    }
    QlFriendAssessmentBo assessment(){QlFriendAssessmentBo b=new QlFriendAssessmentBo();b.setClientRequestId(UUID.randomUUID().toString());b.setName("测试轻友");b.setBirthDate(java.sql.Date.valueOf("1990-01-01"));b.setPhone("13800001234");b.setHeightCm(new BigDecimal("165"));b.setWeightKg(new BigDecimal("55.5"));b.setCity("上海");b.setCleanBodyGoals(Arrays.asList("减重","调理"));b.setDietPreference("荤素各半");b.setWaterIntakeMl(1500);b.setWakeTime("07:00");b.setSleepTime("00:30");b.setBowelStatus("1次/天");b.setEnergyStatus("一般");b.setExerciseStatus("偶尔运动");b.setEmotionalStatus(Collections.singletonList("平静"));b.setHealthConditions(Collections.singletonList("无"));b.setReferralSource("朋友小雨");b.setSensitiveConsent(true);return b;}
    @Test void paymentAndCancellationShareRules(){String s=session(2),r=add(customer(),s,"confirmed");assertThrows(CustomException.class,()->registrations.changePayment(r,payment("paid","100"),9L));settle(r,"100");assertThrows(CustomException.class,()->registrations.changePayment(r,payment("paid","99"),9L));registrations.changePayment(r,payment("paid","100"),9L);assertThrows(CustomException.class,()->staff.cancel(r,access));registrations.changePayment(r,payment("unpaid","0"),9L);staff.cancel(r,access);assertThrows(CustomException.class,()->status(r,"confirmed"));}
    @Test void freeSessionAndMethodValidationAreShared(){String s=session(1);db.update("UPDATE ql_session SET standard_price=0 WHERE id=?",s);String r=add(customer(),s,"confirmed");settle(r,"0");QlPaymentBo b=payment("paid","0");b.setPaymentMethod("unsupported");assertThrows(CustomException.class,()->registrations.changePayment(r,b,9L));b.setPaymentMethod("cash");staff.payment(r,b,access);assertEquals("paid",registrations.getById(r).getPaymentStatus());}
    @Test void passSettlementAtomicallyConsumesOneOwnedAccount(){String customer=customer(),r=add(customer,session(1),"confirmed"),account=pass(customer,3);QlSettlementBo b=new QlSettlementBo();b.setSettlementType("pass");b.setPassUnits(2);b.setPassAccountId(account);b.setFinalAmount(new BigDecimal("10"));assertThrows(CustomException.class,()->registrations.confirmSettlement(r,b,9L));b.setFinalAmount(BigDecimal.ZERO);registrations.confirmSettlement(r,b,9L);assertEquals("confirmed",registrations.getById(r).getSettlementStatus());assertThrows(CustomException.class,()->registrations.changePayment(r,payment("paid","0"),9L));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_id=?",Integer.class,r));assertEquals(1,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));assertEquals(1,db.queryForObject("SELECT balance_after FROM ql_pass_ledger WHERE registration_id=? AND entry_type='consume'",Integer.class,r));assertThrows(CustomException.class,()->registrations.confirmSettlement(r,b,9L));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE registration_id=? AND entry_type='consume'",Integer.class,r));}
    @Test void passSettlementRejectsWrongOwnerAndInsufficientBalanceWithoutLedger(){String owner=customer(),other=customer(),r=add(owner,session(1),"confirmed"),account=pass(other,1);QlSettlementBo b=new QlSettlementBo();b.setSettlementType("pass");b.setPassUnits(1);b.setPassAccountId(account);b.setFinalAmount(BigDecimal.ZERO);assertThrows(CustomException.class,()->registrations.confirmSettlement(r,b,9L));assertEquals(1,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));String own=pass(owner,1);b.setPassAccountId(own);b.setPassUnits(2);assertThrows(CustomException.class,()->registrations.confirmSettlement(r,b,9L));assertEquals(1,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,own));}
    @Test void failedSettlementLogRollsBackPassAndSettlement() {
        String owner = customer(), registration = add(owner, session(1), "confirmed"), account = pass(owner, 2);
        QlSettlementBo settlement = new QlSettlementBo();
        settlement.setSettlementType("pass");
        settlement.setPassUnits(1);
        settlement.setPassAccountId(account);
        settlement.setFinalAmount(BigDecimal.ZERO);
        db.execute("CREATE TRIGGER ql_it_fail_settlement_log BEFORE INSERT ON ql_settlement_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic settlement log failure'");
        try {
            assertThrows(RuntimeException.class, () -> registrations.confirmSettlement(registration, settlement, 9L));
            assertEquals(2, db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?", Integer.class, account));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE registration_id=? AND entry_type='consume'", Integer.class, registration));
            assertEquals("pending", registrations.getById(registration).getSettlementStatus());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?", Integer.class, registration));
        } finally {
            db.execute("DROP TRIGGER ql_it_fail_settlement_log");
        }
        registrations.confirmSettlement(registration, settlement, 9L);
        assertEquals("confirmed", registrations.getById(registration).getSettlementStatus());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE registration_id=? AND entry_type='consume'", Integer.class, registration));
    }
    @Test void passManagementAdjustsByLedgerAndNeverAllowsNegativeBalance(){String account=pass(customer(),5);QlPassAdjustmentBo b=new QlPassAdjustmentBo();b.setQuantityDelta(-2);b.setReason("Synthetic correction");assertEquals(3,passes.adjust(account,b,9L));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));b.setQuantityDelta(-4);assertThrows(CustomException.class,()->passes.adjust(account,b,9L));assertEquals(3,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));}
    @Test void groupPassSettlementUsesInitiatorAccountNotParticipantAccount(){String buyer=customer(),participant=customer(),s=session(1),r=add(participant,s,"confirmed"),batch=UUID.randomUUID().toString();db.update("INSERT INTO ql_registration_batch(id,session_id,submitted_by_customer_id,source,participant_count,submitted_at,payable_amount,payment_status,order_no) VALUES(?,?,?,'mini_program',1,NOW(),100,'unpaid',?)",batch,s,buyer,"T"+batch.replace("-","").substring(0,20));db.update("UPDATE ql_registration SET batch_id=? WHERE id=?",batch,r);String participantAccount=pass(participant,2),buyerAccount=pass(buyer,2);QlSettlementBo b=new QlSettlementBo();b.setSettlementType("pass");b.setPassUnits(1);b.setFinalAmount(BigDecimal.ZERO);b.setPassAccountId(participantAccount);assertThrows(CustomException.class,()->registrations.confirmSettlement(r,b,9L));assertEquals(2,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,participantAccount));b.setPassAccountId(buyerAccount);registrations.confirmSettlement(r,b,9L);assertThrows(CustomException.class,()->registrations.changeBatchPayment(batch,payment("paid","0"),9L));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_batch_id=?",Integer.class,batch));assertEquals(1,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,buyerAccount));assertEquals(buyerAccount,db.queryForObject("SELECT pass_account_id FROM ql_registration_batch WHERE id=?",String.class,batch));}
    @Test void threeEntrypointsCompeteForLastSeat()throws Exception {
        String s=session(1),buyer=customer(),adminCustomer=customer(),staffCustomer=customer();
        when(context.getBean(RedisCache.class).getCacheObject("appToken:synthetic")).thenReturn(77L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(77L)).thenReturn(buyer);
        QlMiniAppRegistrationBo b=new QlMiniAppRegistrationBo();b.setSessionId(s);b.setClientRequestId(UUID.randomUUID().toString());b.setServiceConsent(true);b.setContactName("Synthetic");b.setContactPhone("13800000000");
        QlMiniAppRegistrationBo.Participant p=new QlMiniAppRegistrationBo.Participant();p.setSelf(true);p.setName("Synthetic");p.setMinor(false);p.setPhone("13800000000");b.setParticipants(Collections.singletonList(p));
        List<Callable<Boolean>> actions=Arrays.asList(()->{add(adminCustomer,s,"pending");return true;},()->{staff.enroll(staffCustomer,s,access);return true;},()->{mini.register("synthetic",b);return true;});
        ExecutorService pool=Executors.newFixedThreadPool(3);CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
        try{for(Callable<Boolean> action:actions)futures.add(pool.submit(()->{start.await();try{return action.call();}catch(CustomException expected){return false;}}));start.countDown();int success=0;for(Future<Boolean> f:futures)if(f.get(30,TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,s));}finally{pool.shutdownNow();}
    }
    @Test @SuppressWarnings("unchecked") void contactParticipantAndSettlementFactsStaySeparate() {
        String s=session(3),buyer=customer();
        when(context.getBean(RedisCache.class).getCacheObject("appToken:p0-contact")).thenReturn(80L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(80L)).thenReturn(buyer);
        QlMiniAppRegistrationBo b=new QlMiniAppRegistrationBo();b.setSessionId(s);b.setClientRequestId(UUID.randomUUID().toString());b.setServiceConsent(true);b.setContactName("家庭联系人");b.setContactPhone("13900000000");
        QlMiniAppRegistrationBo.Participant self=new QlMiniAppRegistrationBo.Participant();self.setSelf(true);self.setName("本人");
        QlMiniAppRegistrationBo.Participant companion=new QlMiniAppRegistrationBo.Participant();companion.setSelf(false);companion.setName("同行人");companion.setRelation("家人");
        b.setParticipants(Arrays.asList(self,companion));
        Map<String,Object> result=mini.register("p0-contact",b);
        List<Map<String,Object>> rows=(List<Map<String,Object>>)result.get("registrations");
        assertEquals(2,rows.size());
        assertEquals(new BigDecimal("200.00"),new BigDecimal(result.get("quotedAmount").toString()));
        assertNull(result.get("finalAmount"));
        String batch=String.valueOf(result.get("id"));
        assertEquals("家庭联系人",db.queryForObject("SELECT contact_name FROM ql_registration_batch WHERE id=?",String.class,batch));
        assertEquals("13900000000",db.queryForObject("SELECT contact_phone FROM ql_registration_batch WHERE id=?",String.class,batch));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_customer_identifier i JOIN ql_registration r ON r.customer_id=i.customer_id WHERE r.batch_id=?",Integer.class,batch));
        for(Map<String,Object> row:rows)status(String.valueOf(row.get("registrationId")),"confirmed");
        String first=String.valueOf(rows.get(0).get("registrationId"));
        settle(first,"150");
        assertThrows(CustomException.class,()->registrations.changePayment(first,payment("paid","200"),9L));
        registrations.changePayment(first,payment("paid","150"),9L);
        assertEquals("paid",db.queryForObject("SELECT payment_status FROM ql_registration_batch WHERE id=?",String.class,batch));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_batch_id=?",Integer.class,batch));

        String duplicateSession=session(2);
        b.setSessionId(duplicateSession);b.setClientRequestId(UUID.randomUUID().toString());
        self.setPhone("13700000000");companion.setPhone("13700000000");
        assertThrows(CustomException.class,()->mini.register("p0-contact",b));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,duplicateSession));
        self.setPhone(null);companion.setPhone("13900000000");b.setClientRequestId(UUID.randomUUID().toString());
        assertThrows(CustomException.class,()->mini.register("p0-contact",b));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,duplicateSession));
    }
    @Test void wholeBatchCancellationIsAtomicAndIdempotent() {
        String s=session(2),c=customer(),r=add(c,s,"confirmed"),r2=add(customer(),s,"pending"),batch=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_registration_batch(id,session_id,submitted_by_customer_id,source,participant_count,submitted_at,payable_amount,payment_status,order_no) VALUES(?,?,?,'mini_program',2,NOW(),200,'unpaid',?)",batch,s,c,"T"+batch.replace("-","").substring(0,20));
        db.update("UPDATE ql_registration SET batch_id=? WHERE id IN (?,?)",batch,r,r2);
        assertThrows(CustomException.class,()->status(r,"cancelled"));
        db.update("UPDATE ql_registration SET payment_status='paid' WHERE id=?",r2);
        assertThrows(CustomException.class,()->registrations.cancelBatch(batch,"Synthetic",9L));
        assertEquals("confirmed",registrations.getById(r).getRegistrationStatus());
        db.update("UPDATE ql_registration SET payment_status='unpaid' WHERE id=?",r2);
        registrations.cancelBatch(batch,"Synthetic",9L);registrations.cancelBatch(batch,"Synthetic",9L);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE batch_id=? AND registration_status='cancelled'",Integer.class,batch));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration_status_log WHERE registration_id IN (?,?) AND to_status='cancelled'",Integer.class,r,r2));
    }
    @Test void dailyAttendanceOverridesLegacyFactsAndPricing() {
        String c=customer(),s=session(1),r=add(c,s,"confirmed"),day=UUID.randomUUID().toString();
        db.update("UPDATE ql_session SET status='completed' WHERE id=?",s);
        db.update("INSERT INTO ql_participation(id,customer_id,session_id,registration_id,attendance_status) VALUES(?,?,?,?,'completed')",UUID.randomUUID().toString(),c,s,r);
        QlMiniAppMapper mapper=context.getBean(QlMiniAppMapper.class);assertEquals(1,mapper.countCompletedSessions(c));
        db.update("INSERT INTO ql_session_day(id,session_id,day_no,activity_date) VALUES(?,?,1,CURRENT_DATE())",day,s);
        db.update("INSERT INTO ql_participation_day(id,registration_id,session_day_id,customer_id,attendance_status) VALUES(?,?,?,?,'absent')",UUID.randomUUID().toString(),r,day,c);
        assertEquals(0,mapper.countCompletedSessions(c));assertEquals(new BigDecimal("100"),context.getBean(QlSessionPricing.class).price(c,new BigDecimal("100"),new BigDecimal("50")));
        db.update("UPDATE ql_participation_day SET attendance_status='late' WHERE registration_id=?",r);
        assertEquals(1,mapper.countCompletedSessions(c));
    }
    @Test void paymentRacingCancellationNeverLeavesPaidCancelledRegistration() throws Exception {
        String s=session(1),r=add(customer(),s,"confirmed");settle(r,"100");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<?> pay=pool.submit(()->{try{start.await();registrations.changePayment(r,payment("paid","100"),9L);}catch(CustomException expected){}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}});
            Future<?> cancel=pool.submit(()->{try{start.await();staff.cancel(r,access);}catch(CustomException expected){}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}});
            start.countDown();pay.get(30,TimeUnit.SECONDS);cancel.get(30,TimeUnit.SECONDS);
            com.yicai.life.domain.QlRegistration result=registrations.getById(r);
            assertTrue("paid".equals(result.getPaymentStatus()) && "confirmed".equals(result.getRegistrationStatus()) || "unpaid".equals(result.getPaymentStatus()) && "cancelled".equals(result.getRegistrationStatus()));
        }finally{pool.shutdownNow();}
    }
    @Test void contactsUseRealConstraintsAndRollbackWithInvalidTask() {
        String c=customer();Map<String,Object> body=new HashMap<>();body.put("customerId",c);body.put("summary","Synthetic");
        Map<String,Object> task=new HashMap<>();task.put("title","Synthetic");task.put("priority","bad");body.put("followUp",task);
        assertThrows(CustomException.class,()->staff.addContact(body,access));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_interaction WHERE customer_id=?",Integer.class,c));
        task.put("priority","normal");Map<String,Object> result=staff.addContact(body,access);
        Map<String,Object> finish=new HashMap<>();finish.put("status","completed");finish.put("result","Synthetic result");
        staff.transitionFollowUp(String.valueOf(result.get("followUpTaskId")),finish,access);
        assertEquals("completed",staff.followUps(c).get(0).get("status"));assertEquals(1,staff.contacts(c).size());
    }
    @Test void interruptedMigrationRequiresRecovery() throws Exception {
        String name="V1_3_11__wechat_login_identity.sql";db.update("UPDATE ql_migration_run SET state='running' WHERE name=?",name);
        try(Connection c=context.getBean(DataSource.class).getConnection()) {
            assertThrows(IllegalStateException.class,()->DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent()));
        }finally{db.update("UPDATE ql_migration_run SET state='complete' WHERE name=?",name);}
    }
    @Test void closedWindowRejectsAdminAndStaff(){String s=session(3);db.update("UPDATE ql_session SET registration_close_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?",s);assertThrows(CustomException.class,()->add(customer(),s,"pending"));assertThrows(CustomException.class,()->staff.enroll(customer(),s,access));}
    String habitFixture(int elapsed) {
        String customer=customer(), session=session(5), plan=UUID.randomUUID().toString();
        java.time.LocalDate today=java.time.LocalDate.now(QlHabitPlanService.ZONE);
        db.update("INSERT INTO ql_habit_plan(id,customer_id,session_id,plan_length,current_day,status,started_at) VALUES(?,?,?,14,1,'active',?)",plan,customer,session,java.sql.Date.valueOf(today.minusDays(elapsed)));
        for(int day=1;day<=14;day++) db.update("INSERT INTO ql_habit_day_record(id,habit_plan_id,plan_day,status) VALUES(?,?,?,'pending')",UUID.randomUUID().toString(),plan,day);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:habit-test")).thenReturn(78L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(78L)).thenReturn(customer);
        return plan;
    }
    @SuppressWarnings("unchecked") Map<String,Object> currentHabit() { return (Map<String,Object>)mini.overview("habit-test").get("habit"); }
    QlMiniAppDailyRecordBo habitRecord(int day) {
        QlMiniAppDailyRecordBo bo=new QlMiniAppDailyRecordBo();bo.setRecordDate(new java.util.Date());bo.setRecordStage("habit");bo.setPlanDay(day);bo.setChoiceValue("done");return bo;
    }
    @Test void habitPauseRetainsRecordButExcludesDayAndSameDayResumeIsIdempotent() {
        String plan=habitFixture(2);
        assertEquals(3,((Number)currentHabit().get("currentDay")).intValue());
        mini.saveDailyRecord("habit-test",habitRecord(3));
        assertEquals(Collections.singletonList(3),currentHabit().get("completedDays"));
        mini.changeHabit("habit-test",plan,true);
        mini.changeHabit("habit-test",plan,true);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_daily_record d JOIN ql_habit_plan p ON p.customer_id=d.customer_id WHERE p.id=?",Integer.class,plan));
        assertTrue(((List<?>)currentHabit().get("completedDays")).isEmpty());
        assertThrows(CustomException.class,()->mini.saveDailyRecord("habit-test",habitRecord(3)));
        mini.changeHabit("habit-test",plan,false);
        mini.changeHabit("habit-test",plan,false);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_habit_pause WHERE habit_plan_id=?",Integer.class,plan));
        assertEquals(false,currentHabit().get("canRecordToday"));
        assertThrows(CustomException.class,()->mini.saveDailyRecord("habit-test",habitRecord(3)));
    }
    @Test void habitResumesFrozenDayAndExpiresWithoutInventingCheckIns() {
        String plan=habitFixture(6);
        java.time.LocalDate today=java.time.LocalDate.now(QlHabitPlanService.ZONE);
        db.update("INSERT INTO ql_habit_pause(habit_plan_id,start_date) VALUES(?,?)",plan,java.sql.Date.valueOf(today.minusDays(4)));
        db.update("UPDATE ql_habit_plan SET status='paused',paused_at=? WHERE id=?",java.sql.Timestamp.valueOf(today.minusDays(4).atStartOfDay()),plan);
        assertEquals(3,((Number)currentHabit().get("currentDay")).intValue());
        mini.changeHabit("habit-test",plan,false);
        assertEquals(true,currentHabit().get("canRecordToday"));
        mini.saveDailyRecord("habit-test",habitRecord(3));
        assertEquals(Collections.singletonList(3),currentHabit().get("completedDays"));
        String expired=habitFixture(14);
        assertEquals("completed",currentHabit().get("status"));
        assertTrue(((List<?>)currentHabit().get("completedDays")).isEmpty());
        assertThrows(CustomException.class,()->mini.changeHabit("habit-test",expired,true));
        assertThrows(CustomException.class,()->mini.saveDailyRecord("habit-test",habitRecord(14)));
    }
    @Test void habitPauseAndCheckInSerializeWithoutCountingExcludedDate() throws Exception {
        String plan=habitFixture(0);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> pause=pool.submit(()->mini.changeHabit("habit-test",plan,true));
            Future<?> record=pool.submit(()->{try{mini.saveDailyRecord("habit-test",habitRecord(1));}catch(CustomException expected){}});
            pause.get(10,TimeUnit.SECONDS); record.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals("paused",currentHabit().get("status"));
        assertTrue(((List<?>)currentHabit().get("completedDays")).isEmpty());
        assertThrows(CustomException.class,()->mini.changeHabit("habit-test",UUID.randomUUID().toString(),false));
    }    @Test void concurrentHabitStartCreatesOnePlanAndAllDays() throws Exception {
        String c=customer(),s=session(2),r=add(c,s,"confirmed");
        db.update("INSERT INTO ql_participation(id,customer_id,session_id,registration_id,attendance_status) VALUES(?,?,?,?,'completed')",UUID.randomUUID().toString(),c,s,r);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:habit-start")).thenReturn(79L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(79L)).thenReturn(c);
        QlMiniAppHabitBo bo=new QlMiniAppHabitBo();bo.setPlanLength(21);bo.setSessionId(s);bo.setStartedAt(new java.util.Date());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Map<String,Object>> a=pool.submit(()->mini.startHabit("habit-start",bo));
            Future<Map<String,Object>> b=pool.submit(()->mini.startHabit("habit-start",bo));
            assertEquals(a.get(10,TimeUnit.SECONDS).get("id"),b.get(10,TimeUnit.SECONDS).get("id"));
        } finally { pool.shutdownNow(); }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_habit_plan WHERE customer_id=?",Integer.class,c));
        assertEquals(21,db.queryForObject("SELECT COUNT(*) FROM ql_habit_day_record d JOIN ql_habit_plan p ON p.id=d.habit_plan_id WHERE p.customer_id=?",Integer.class,c));
    }    @Test void productionGuardRejectsMissingHabitMigration() {
        db.execute("RENAME TABLE ql_habit_pause TO ql_habit_pause_probe");
        try {
            IllegalStateException error=assertThrows(IllegalStateException.class,()->new com.yicai.web.config.ProductionHabitSchemaGuard(db).afterPropertiesSet());
            assertTrue(error.getMessage().contains("V1_3_12"));
        } finally { db.execute("RENAME TABLE ql_habit_pause_probe TO ql_habit_pause"); }
        new com.yicai.web.config.ProductionHabitSchemaGuard(db).afterPropertiesSet();
    }    @Test @SuppressWarnings("unchecked") void cashSettlementRevokesAndReconfirmsWithoutChangingRegistrationOrAttendance() {
        String c=customer(),s=session(2),r=add(c,s,"confirmed"),attendance=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_participation(id,customer_id,session_id,registration_id,attendance_status) VALUES(?,?,?,?,'completed')",attendance,c,s,r);
        BigDecimal quote=db.queryForObject("SELECT unit_price FROM ql_registration WHERE id=?",BigDecimal.class,r);
        settle(r,"75.00");String roundA=currentSettlementId(r);assertNotNull(roundA);
        assertThrows(CustomException.class,()->settle(r,"99.00"));
        int attendanceBefore=db.queryForObject("SELECT COUNT(*) FROM ql_participation WHERE registration_id=?",Integer.class,r);
        revoke(r,roundA);
        QlRegistration pending=registrations.getById(r);
        assertEquals("pending",pending.getSettlementStatus());assertNull(pending.getFinalAmount());assertNull(pending.getSettlementType());assertNull(pending.getCurrentSettlementId());
        assertEquals("confirmed",pending.getRegistrationStatus());assertEquals("unpaid",pending.getPaymentStatus());assertEquals(s,pending.getSessionId());assertEquals(0,quote.compareTo(pending.getUnitPrice()));
        assertEquals(attendanceBefore,db.queryForObject("SELECT COUNT(*) FROM ql_participation WHERE registration_id=?",Integer.class,r));
        assertEquals("completed",db.queryForObject("SELECT attendance_status FROM ql_participation WHERE id=?",String.class,attendance));
        Map<String,Object> staffRow=staff.registrations(s,null).stream().filter(row->r.equals(row.get("id"))).findFirst().orElseThrow(AssertionError::new);
        assertEquals("pending",staffRow.get("settlementStatus"));assertNull(staffRow.get("finalAmount"));
        when(context.getBean(RedisCache.class).getCacheObject("appToken:settlement-round-ui")).thenReturn(93L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(93L)).thenReturn(c);
        Map<String,Object> miniRow=miniRegistration("settlement-round-ui",r);
        assertEquals("pending",miniRow.get("settlementStatus"));assertNull(miniRow.get("finalAmount"));

        settle(r,"88.50");String roundB=currentSettlementId(r);assertNotNull(roundB);assertNotEquals(roundA,roundB);
        assertEquals("confirmed",miniRegistration("settlement-round-ui",r).get("settlementStatus"));
        assertEquals(0,new BigDecimal("88.50").compareTo(new BigDecimal(miniRegistration("settlement-round-ui",r).get("finalAmount").toString())));
        revoke(r,roundA); // Delayed retry for A is idempotent and cannot touch B.
        assertEquals(roundB,currentSettlementId(r));assertEquals(0,new BigDecimal("88.50").compareTo(registrations.getById(r).getFinalAmount()));
        revoke(r,roundB);
        assertEquals("pending",registrations.getById(r).getSettlementStatus());assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE registration_id=?",Integer.class,r));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));
        assertEquals(attendanceBefore,db.queryForObject("SELECT COUNT(*) FROM ql_participation WHERE registration_id=?",Integer.class,r));
    }

    @Test void paidCashSettlementRequiresExistingPaymentReversalFirst() {
        String r=add(customer(),session(1),"confirmed");settle(r,"90.00");String round=currentSettlementId(r);
        registrations.changePayment(r,payment("paid","90.00"),9L);
        CustomException blocked=assertThrows(CustomException.class,()->revoke(r,round));
        assertTrue(blocked.getMessage().contains("请先通过现有入口撤销收款登记，再撤销结算。"));
        assertEquals("confirmed",registrations.getById(r).getSettlementStatus());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=?",Integer.class,round));
        registrations.changePayment(r,payment("unpaid","0"),9L);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_id=? AND transaction_type='session' AND status='paid'",Integer.class,r));
        revoke(r,round);
        assertEquals("pending",registrations.getById(r).getSettlementStatus());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=?",Integer.class,round));
    }

    @Test void singlePassReversalReturnsExactUnitsToOriginalExpiredAccountAndKeepsDebit() {
        String c=customer(),r=add(c,session(1),"confirmed"),original=pass(c,5),other=pass(c,7);
        settlePass(r,original,2);String round=currentSettlementId(r);
        String debitId=db.queryForObject("SELECT id FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",String.class,round);
        db.update("UPDATE ql_pass_account SET valid_until=DATE_SUB(CURRENT_DATE(),INTERVAL 1 DAY) WHERE id=?",original);
        int expiredBalanceBefore=5;
        revoke(r,round);
        assertEquals(expiredBalanceBefore,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,original));
        assertEquals(7,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,other));
        assertEquals("active",db.queryForObject("SELECT status FROM ql_pass_account WHERE id=?",String.class,original));
        assertEquals(-1,db.queryForObject("SELECT DATEDIFF(valid_until,CURRENT_DATE()) FROM ql_pass_account WHERE id=?",Integer.class,original));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE id=? AND settlement_id=? AND entry_type='consume' AND quantity_delta=-2",Integer.class,debitId,round));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void' AND pass_account_id=? AND quantity_delta=2 AND registration_id=?",Integer.class,round,original,r));
        assertEquals(debitId,db.queryForObject("SELECT original_pass_ledger_id FROM ql_settlement_reversal WHERE settlement_id=?",String.class,round));
        assertEquals("pending",registrations.getById(r).getSettlementStatus());
    }

    @Test void voidPassAccountReceivesReturnWithoutReactivation() {
        String c=customer(),r=add(c,session(1),"confirmed"),account=pass(c,4);
        settlePass(r,account,1);String round=currentSettlementId(r);
        db.update("UPDATE ql_pass_account SET status='void' WHERE id=?",account);
        revoke(r,round);
        assertEquals(4,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals("void",db.queryForObject("SELECT status FROM ql_pass_account WHERE id=?",String.class,account));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void' AND quantity_delta=1",Integer.class,round));
    }

    @Test void batchPassReversalFromAnyParticipantIsAtomicAndRepeatableAcrossRounds() throws Exception {
        String buyer=customer(),companion=customer(),s=session(2),r1=add(buyer,s,"confirmed"),r2=add(companion,s,"confirmed");
        String batchId=batch(buyer,s,r1,r2),buyerAccount=pass(buyer,5),companionAccount=pass(companion,9);
        settlePass(r1,buyerAccount,3);String roundA=currentSettlementId(r1);
        assertEquals(2,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,buyerAccount));
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<?> first=pool.submit(()->{try{start.await();revoke(r1,roundA);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}});
            Future<?> second=pool.submit(()->{try{start.await();revoke(r2,roundA);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}});
            start.countDown();first.get(30,TimeUnit.SECONDS);second.get(30,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(5,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,buyerAccount));
        assertEquals(9,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,companionAccount));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",Integer.class,roundA));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",Integer.class,roundA));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=? AND registration_batch_id=?",Integer.class,roundA,batchId));
        assertEquals("confirmed",registrations.getById(r1).getRegistrationStatus());assertEquals("confirmed",registrations.getById(r2).getRegistrationStatus());
        assertEquals("pending",staff.registrations(s,null).stream().filter(row->r1.equals(row.get("id"))).findFirst().orElseThrow(AssertionError::new).get("settlementStatus"));

        settlePass(r2,buyerAccount,2);String roundB=currentSettlementId(r2);assertNotEquals(roundA,roundB);
        revoke(r1,roundA);assertEquals(roundB,currentSettlementId(r1));
        assertEquals(3,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,buyerAccount));
        revoke(r2,roundB);
        assertEquals(5,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,buyerAccount));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE registration_batch_id=? AND entry_type='consume'",Integer.class,batchId));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE registration_batch_id=? AND entry_type='void'",Integer.class,batchId));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE registration_batch_id=?",Integer.class,batchId));
        assertEquals("confirmed",registrations.getById(r1).getRegistrationStatus());assertEquals("confirmed",registrations.getById(r2).getRegistrationStatus());
    }

    @Test void settlementReversalRollsBackStateAndBalanceWhenLedgerOrAuditWriteFails() {
        String owner=customer(),r=add(owner,session(1),"confirmed"),account=pass(owner,4);
        settlePass(r,account,2);String round=currentSettlementId(r);
        int balanceBefore=2;
        db.execute("CREATE TRIGGER ql_it_fail_reversal_audit BEFORE INSERT ON ql_settlement_reversal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected reversal audit failure'");
        try {
            assertThrows(RuntimeException.class,()->revoke(r,round));
        } finally {db.execute("DROP TRIGGER IF EXISTS ql_it_fail_reversal_audit");}
        assertEquals("confirmed",registrations.getById(r).getSettlementStatus());assertEquals(round,currentSettlementId(r));
        assertEquals(balanceBefore,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",Integer.class,round));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=?",Integer.class,round));

        db.execute("CREATE TRIGGER ql_it_fail_pass_void BEFORE INSERT ON ql_pass_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected pass void failure'");
        try {
            assertThrows(RuntimeException.class,()->revoke(r,round));
        } finally {db.execute("DROP TRIGGER IF EXISTS ql_it_fail_pass_void");}
        assertEquals("confirmed",registrations.getById(r).getSettlementStatus());assertEquals(round,currentSettlementId(r));
        assertEquals(balanceBefore,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=?",Integer.class,round));
        revoke(r,round);
        assertEquals(4,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
    }

    @Test void unlinkedOrMissingHistoricalPassDebitIsNeverGuessedOrRecreated() {
        String c=customer(),r=add(c,session(1),"confirmed"),account=pass(c,4);
        settlePass(r,account,2);String round=currentSettlementId(r);
        db.update("UPDATE ql_registration SET current_settlement_id=NULL WHERE id=?",r);
        CustomException unlinked=assertThrows(CustomException.class,()->revoke(r,round));
        assertTrue(unlinked.getMessage().contains("缺少可靠关联"));
        assertEquals(2,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",Integer.class,round));
        db.update("UPDATE ql_registration SET current_settlement_id=? WHERE id=?",round,r);
        String debitId=db.queryForObject("SELECT id FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",String.class,round);
        db.update("DELETE FROM ql_pass_ledger WHERE id=?",debitId); // Inject an incomplete legacy record in this isolated fixture.
        CustomException missing=assertThrows(CustomException.class,()->revoke(r,round));
        assertTrue(missing.getMessage().contains("扣卡流水缺失"));
        assertEquals("confirmed",registrations.getById(r).getSettlementStatus());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",Integer.class,round));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=?",Integer.class,round));
    }

    private String chineseReason(int length) {
        char[] chars=new char[length];Arrays.fill(chars,'撤');return new String(chars);
    }

    private void assertPassReversalAcceptsReasonLength(int length) {
        String owner=customer(),r=add(owner,session(1),"confirmed"),account=pass(owner,5);
        settlePass(r,account,2);String round=currentSettlementId(r);
        String input="  \t"+chineseReason(length)+" \n ";String expected=input.trim();
        assertEquals(length,expected.length());
        registrations.revokeSettlement(r,round,input,9L);
        assertEquals("pending",registrations.getById(r).getSettlementStatus());
        assertEquals(5,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void' AND pass_account_id=?",Integer.class,round,account));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume' AND quantity_delta=-2",Integer.class,round));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(expected,db.queryForObject("SELECT reason FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",String.class,round));
        assertEquals(expected,db.queryForObject("SELECT reason FROM ql_settlement_reversal WHERE settlement_id=?",String.class,round));
    }

    private void assertPassReversalRejectsReasonWithoutChanges(String input) {
        String owner=customer(),r=add(owner,session(1),"confirmed"),account=pass(owner,5);
        settlePass(r,account,2);String round=currentSettlementId(r);
        assertThrows(CustomException.class,()->registrations.revokeSettlement(r,round,input,9L));
        assertEquals("confirmed",registrations.getById(r).getSettlementStatus());
        assertEquals(round,currentSettlementId(r));
        assertEquals(3,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='consume'",Integer.class,round));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",Integer.class,round));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE settlement_id=?",Integer.class,round));
    }

    @Test void passReversalAccepts495ChineseCharacterReason() { assertPassReversalAcceptsReasonLength(495); }
    @Test void passReversalAccepts496ChineseCharacterReason() { assertPassReversalAcceptsReasonLength(496); }
    @Test void passReversalAccepts500ChineseCharacterReason() { assertPassReversalAcceptsReasonLength(500); }
    @Test void passReversalRejects501ChineseCharactersWithoutChanges() { assertPassReversalRejectsReasonWithoutChanges(chineseReason(501)); }
    @Test void passReversalRejectsWhitespaceReasonWithoutChanges() { assertPassReversalRejectsReasonWithoutChanges(" \t \r\n "); }
}
