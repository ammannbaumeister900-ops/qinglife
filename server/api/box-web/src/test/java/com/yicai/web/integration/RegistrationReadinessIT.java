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
    @org.junit.jupiter.api.io.TempDir static Path mediaDirectory;
    static String priorProfile,priorImagePath;
    static final Map<String,Object> access=new HashMap<>();
    @Configuration @EnableTransactionManagement
    @Import({QlSessionServiceImpl.class,QlRegistrationPolicy.class,QlSessionAdmissionPolicy.class,QlRegistrationServiceImpl.class,QlSessionPricing.class,QlMiniAppServiceImpl.class,QlStaffWorkspaceService.class,QlAttendanceAudit.class,QlHabitPlanService.class,QlPassService.class,QlAdminSelectionService.class,QlFriendAssessmentService.class,QlCampReflectionService.class,com.yicai.system.service.AccountPagePermissionService.class})
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
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor pagination=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
            f.setPlugins(pagination);
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
        @Bean QlReflectionVoiceService reflectionVoice(){return mock(QlReflectionVoiceService.class);}
        @Bean RedisCache redis(){return mock(RedisCache.class);}
        @Bean RedissonClient redisson(){return mock(RedissonClient.class);}
        @Bean QlCustomerIdentityService identity(){return mock(QlCustomerIdentityService.class);}
    }
    @BeforeAll static void setup()throws Exception {
        priorProfile=com.yicai.common.config.RuoYiConfig.getProfile();priorImagePath=com.yicai.common.config.RuoYiConfig.getImagePath();
        com.yicai.common.config.RuoYiConfig mediaConfig=new com.yicai.common.config.RuoYiConfig();
        mediaConfig.setProfile(mediaDirectory.toString());mediaConfig.setImagePath("https://example.invalid/api/profile/public/");
        context=new AnnotationConfigApplicationContext(Config.class);db=context.getBean(JdbcTemplate.class);
        try(Connection c=context.getBean(DataSource.class).getConnection()){DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());}
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,status,del_flag) VALUES(9,0,'synthetic','Synthetic','00','0','0')");
        registrations=context.getBean(IQlRegistrationService.class);staff=context.getBean(QlStaffWorkspaceService.class);mini=context.getBean(IQlMiniAppService.class);passes=context.getBean(QlPassService.class);
        access.put("sys_user_id",9L);access.put("can_operate",1);access.put("can_payment",1);
    }
    @AfterAll static void close(){if(context!=null)context.close();com.yicai.common.config.RuoYiConfig c=new com.yicai.common.config.RuoYiConfig();c.setProfile(priorProfile);c.setImagePath(priorImagePath);}
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
    QlCampReflectionBo reflectionBo(String registration,String phase,int revision,String note,boolean share) { QlCampReflectionBo b=new QlCampReflectionBo();b.setRegistrationId(registration);b.setPhase(phase);b.setRevision(revision);b.setNote(note);b.setAction(share?"submit":"private");b.setShareConsent(share);return b; }
    QlReflectionModerationBo moderation(int revision,String excerpt) { QlReflectionModerationBo b=new QlReflectionModerationBo();b.setRevision(revision);b.setExcerpt(excerpt);return b; }
    QlPaymentConfirmationBo confirmation(String amount,String method) { QlPaymentConfirmationBo b=new QlPaymentConfirmationBo();b.setPaymentMethod(method);b.setAmount(amount==null?null:new BigDecimal(amount));b.setNote("Synthetic confirmation");return b; }
    @Test void unifiedPaymentRequiresConfirmedRegistrationAmountAndMethodAndNeverDuplicates() {
        String s=session(1),r=add(customer(),s,"pending");QlPaymentConfirmationBo b=confirmation("88.50","cash");
        assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));status(r,"confirmed");
        b.setAmount(null);assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));b.setAmount(new BigDecimal("88.50"));b.setPaymentMethod("");assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));b.setPaymentMethod("cash");
        assertEquals("pending",registrations.getById(r).getSettlementStatus());assertTrue(registrations.confirmPayment(r,b,9L));
        assertEquals("paid",registrations.getById(r).getPaymentStatus());assertEquals(new BigDecimal("88.50"),registrations.queryById(r).getFinalAmount());
        assertEquals("cash",db.queryForObject("SELECT payment_method FROM ql_transaction WHERE registration_id=?",String.class,r));
        assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_id=?",Integer.class,r));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));
    }
    @Test void unifiedCashFailureRollsBackNewSettlementAndReceiptTogether() {
        String r=add(customer(),session(1),"confirmed");QlPaymentConfirmationBo b=confirmation("100","transfer");
        db.execute("CREATE TRIGGER ql_it_fail_unified_receipt BEFORE INSERT ON ql_transaction FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic receipt failure'");
        try {assertThrows(RuntimeException.class,()->registrations.confirmPayment(r,b,9L));} finally {db.execute("DROP TRIGGER ql_it_fail_unified_receipt");}
        assertEquals("pending",registrations.getById(r).getSettlementStatus());assertEquals("unpaid",registrations.getById(r).getPaymentStatus());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_id=?",Integer.class,r));
    }
    @Test void unifiedGroupCashRequiresEveryParticipantAndCreatesOnlyOneBuyerReceipt() {
        String buyer=customer(),companion=customer(),s=session(2),r1=add(buyer,s,"confirmed"),r2=add(companion,s,"pending"),order=batch(buyer,s,r1,r2);
        QlPaymentConfirmationBo b=confirmation("150","wechat_scan");assertFalse(registrations.queryById(r1).getBatchReadyForPayment());assertThrows(CustomException.class,()->registrations.confirmPayment(r1,b,9L));
        status(r2,"confirmed");assertTrue(registrations.queryById(r1).getBatchReadyForPayment());assertTrue(registrations.confirmPayment(r2,b,9L));
        assertEquals("paid",registrations.getById(r1).getPaymentStatus());assertEquals("paid",registrations.getById(r2).getPaymentStatus());
        assertEquals(buyer,db.queryForObject("SELECT customer_id FROM ql_transaction WHERE registration_batch_id=?",String.class,order));assertEquals(new BigDecimal("150.00"),db.queryForObject("SELECT amount FROM ql_transaction WHERE registration_batch_id=?",BigDecimal.class,order));
        assertThrows(CustomException.class,()->registrations.confirmPayment(r1,b,9L));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_batch_id=?",Integer.class,order));
    }
    @Test void unifiedGroupPassUsesInitiatorAndDoesNotCreateCashReceipt() {
        String owner=customer(),participant=customer(),s=session(1),r=add(participant,s,"confirmed"),order=batch(owner,s,r),owned=pass(owner,3),wrong=pass(participant,4);
        QlPaymentConfirmationBo b=confirmation(null,"pass");b.setPassAccountId(wrong);b.setPassUnits(1);assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));assertEquals("pending",registrations.queryById(r).getSettlementStatus());
        b.setPassAccountId(owned);assertTrue(registrations.confirmPayment(r,b,9L));assertEquals(2,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,owned));assertEquals(4,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,wrong));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_batch_id=?",Integer.class,order));assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE registration_batch_id=? AND entry_type='consume'",Integer.class,order));
    }
    @Test void unifiedPaymentProtectsExistingSettlementAmountAndRound() {
        String r=add(customer(),session(1),"confirmed");settle(r,"60");String round=currentSettlementId(r);QlPaymentConfirmationBo b=confirmation("60","alipay_scan");
        assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));b.setExpectedSettlementId(round);b.setAmount(new BigDecimal("61"));assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));
        b.setAmount(new BigDecimal("60"));assertTrue(registrations.confirmPayment(r,b,9L));assertEquals(round,currentSettlementId(r));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));
        registrations.changePayment(r,payment("unpaid","60"),9L);registrations.revokeSettlement(r,round,"Synthetic correction",9L);
        assertThrows(CustomException.class,()->registrations.confirmPayment(r,b,9L));assertEquals("pending",registrations.getById(r).getSettlementStatus());
        b.setExpectedSettlementId(null);b.setAmount(BigDecimal.ZERO);assertTrue(registrations.confirmPayment(r,b,9L));assertEquals("paid",registrations.getById(r).getPaymentStatus());assertEquals(BigDecimal.ZERO.setScale(2),registrations.queryById(r).getFinalAmount());
    }
    @Test void concurrentUnifiedCashConfirmationCreatesExactlyOneReceipt() throws Exception {
        String r=add(customer(),session(1),"confirmed");QlPaymentConfirmationBo b=confirmation("100","cash");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> run=()->{start.await();try{return registrations.confirmPayment(r,b,9L);}catch(CustomException e){return false;}};
        try {Future<Boolean> one=pool.submit(run),two=pool.submit(run);start.countDown();assertEquals(1,(one.get(30,TimeUnit.SECONDS)?1:0)+(two.get(30,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE registration_id=?",Integer.class,r));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_id=?",Integer.class,r));
    }
    @Test void unifiedPaymentProgressFiltersIncludePassesAndExcludeCancelledRows() {
        String s=session(4),cash=add(customer(),s,"confirmed"),card=add(customer(),s,"confirmed"),waiting=add(customer(),s,"pending"),cancelled=add(customer(),s,"pending");
        registrations.confirmPayment(cash,confirmation("100","cash"),9L);String account=pass(registrations.getById(card).getCustomerId(),2);QlPaymentConfirmationBo b=confirmation(null,"pass");b.setPassUnits(1);b.setPassAccountId(account);registrations.confirmPayment(card,b,9L);status(cancelled,"cancelled");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        try {QlRegistrationBo filter=new QlRegistrationBo();filter.setSessionNumber(db.queryForObject("SELECT session_number FROM ql_session WHERE id=?",Integer.class,s));filter.setPaymentProgress("completed");assertEquals(2,registrations.queryPageList(filter).getTotal());filter.setPaymentProgress("pending");assertEquals(waiting,registrations.queryPageList(filter).getRows().get(0).getId());filter.setPaymentProgress("cancelled");assertEquals(cancelled,registrations.queryPageList(filter).getRows().get(0).getId());}finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void statisticsSessionShortcutUsesBookingStartAndCourseEndWithMinimalFields() {
        String s=session(1);db.update("UPDATE ql_session SET registration_open_at='2026-09-05 00:00:00',registration_close_at='2026-09-25 23:59:59',start_date='2026-10-01',end_date='2026-10-03' WHERE id=?",s);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);Map<String,Object> row=service.statisticsSessions().stream().filter(x->s.equals(x.get("id"))).findFirst().get();
        assertEquals("2026-09-05",row.get("registrationStartDate"));assertEquals("2026-10-03",row.get("endDate"));assertEquals(new HashSet<>(Arrays.asList("id","sessionNumber","name","registrationStartDate","endDate")),row.keySet());
        db.update("UPDATE ql_session SET registration_open_at=NULL WHERE id=?",s);assertNull(service.statisticsSessions().stream().filter(x->s.equals(x.get("id"))).findFirst().get().get("registrationStartDate"));
    }

    @Test void openingNoteAndImageAreOptionalAndMalformedPhotoIsAtomic() {
        String owner=customer();QlPassAccountBo b=new QlPassAccountBo();b.setCustomerId(owner);b.setPassType("Optional notes");b.setInitialUnits(3);
        String account=passes.open(b,9L);assertNull(db.queryForObject("SELECT reason FROM ql_pass_ledger WHERE pass_account_id=?",String.class,account));
        assertThrows(CustomException.class,()->passes.image(account));
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE ql_pass_account SET image_data=? WHERE id=?",new byte[]{1},account));
        QlPassAccountBo.Image image=new QlPassAccountBo.Image();image.setMime("image/png");image.setData("not-base64");b.setImage(image);
        assertThrows(CustomException.class,()->passes.open(b,9L));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_account WHERE customer_id=?",Integer.class,owner));
        try {java.awt.image.BufferedImage pixel=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(pixel,"png",bytes);image.setData(Base64.getEncoder().encodeToString(bytes.toByteArray()));String withImage=passes.open(b,9L);assertArrayEquals(bytes.toByteArray(),(byte[])passes.image(withImage).get("data"));Map<String,Object> row=passes.list(owner).stream().filter(x->withImage.equals(x.get("id"))).findFirst().get();assertEquals(1,((Number)row.get("hasImage")).intValue());assertFalse(row.containsKey("image_data"));}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    @Test void validityOnlyAndCombinedAdjustmentsPreserveUnitsAndAuditDates() {
        String account=pass(customer(),5);QlPassAdjustmentBo b=new QlPassAdjustmentBo();b.setQuantityDelta(0);b.setReason("延长有效期");b.setChangeValidity(true);b.setValidFrom(java.sql.Date.valueOf("2026-01-01"));b.setValidUntil(java.sql.Date.valueOf("2026-12-31"));
        assertEquals(5,passes.adjust(account,b,9L));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        Map<String,Object> log=db.queryForMap("SELECT * FROM ql_pass_validity_log WHERE pass_account_id=?",account);assertNull(log.get("previous_valid_from"));assertEquals(java.sql.Date.valueOf("2026-12-31"),log.get("valid_until"));
        assertTrue(passes.ledger(account).stream().anyMatch(r->"validity".equals(r.get("entryType"))));
        assertThrows(CustomException.class,()->passes.adjust(account,b,9L));
        b.setValidUntil(java.sql.Date.valueOf("2025-12-31"));b.setQuantityDelta(2);assertThrows(CustomException.class,()->passes.adjust(account,b,9L));assertEquals(5,db.queryForObject("SELECT SUM(quantity_delta) FROM ql_pass_ledger WHERE pass_account_id=?",Integer.class,account));
        b.setValidUntil(java.sql.Date.valueOf("2027-12-31"));assertEquals(7,passes.adjust(account,b,9L));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_pass_validity_log WHERE pass_account_id=?",Integer.class,account));
        b.setChangeValidity(false);b.setValidFrom(null);b.setValidUntil(null);b.setQuantityDelta(-1);assertEquals(6,passes.adjust(account,b,9L));assertEquals(java.sql.Date.valueOf("2027-12-31"),db.queryForObject("SELECT valid_until FROM ql_pass_account WHERE id=?",java.sql.Date.class,account));
    }
    @Test @SuppressWarnings("unchecked") void accountRestrictionsIntersectRolesAndRevokeIssuedPermissionImmediately() {
        com.yicai.system.service.AccountPagePermissionService p=context.getBean(com.yicai.system.service.AccountPagePermissionService.class);
        long user=100000L+(++number);db.update("INSERT INTO sys_user(user_id,user_name,nick_name,status,del_flag) VALUES(?,?,?,'0','0')",user,"account"+user,"Synthetic account");db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,role_id FROM sys_role WHERE role_key='ql_operator'",user);
        assertTrue(p.allowsPermission(user,"life:registration:payment"));Map<String,Object> details=p.detail(user);assertEquals(Boolean.TRUE,details.get("inherit"));assertFalse(((Map<String,Object>)details.get("user")).containsKey("password"));
        com.yicai.system.domain.bo.AccountPagePermissionBo b=new com.yicai.system.domain.bo.AccountPagePermissionBo();b.setInherit(false);b.setPageIds(Collections.singletonList(2295L));p.save(user,b,9L);
        Set<String> cached=new HashSet<>(Arrays.asList("life:pass:list","life:pass:add","life:pass:adjust","life:registration:payment"));
        assertEquals(new HashSet<>(Arrays.asList("life:pass:list","life:pass:add","life:pass:adjust")),p.filterPermissions(user,cached));assertFalse(p.allowsPermission(user,"life:registration:payment"));assertTrue(p.allowsPermission(user,"life:pass:add"));
        List<com.yicai.common.core.domain.entity.SysMenu> menus=new ArrayList<>();for(Map<String,Object> m:db.queryForList("SELECT menu_id,parent_id FROM sys_menu WHERE menu_id IN (2200,2295,2209)")){com.yicai.common.core.domain.entity.SysMenu menu=new com.yicai.common.core.domain.entity.SysMenu();menu.setMenuId(((Number)m.get("menu_id")).longValue());menu.setParentId(((Number)m.get("parent_id")).longValue());menus.add(menu);}assertEquals(2,p.filterMenus(user,menus).size());
        QlAdminSelectionService selection=context.getBean(QlAdminSelectionService.class);
        String owner=customer(),participant=customer(),period=session(1),reg=add(participant,period,"confirmed"),batchId=batch(owner,period,reg),owned=pass(owner,3);pass(participant,4);
        List<Map<String,Object>> options=selection.registrationPasses(reg,user);assertTrue(options.stream().anyMatch(a->owned.equals(a.get("id"))));assertFalse(options.stream().anyMatch(a->a.containsKey("realName")));
        assertTrue(selection.customers(user).stream().anyMatch(a->owner.equals(a.get("id"))));assertTrue(selection.sessions(user).stream().anyMatch(a->period.equals(a.get("id"))));
        b.setPageIds(Collections.emptyList());p.save(user,b,9L);assertTrue(p.filterPermissions(user,cached).isEmpty());assertFalse(p.allowsPermission(user,"life:pass:list"));
        b.setInherit(true);p.save(user,b,9L);assertEquals(cached,p.filterPermissions(user,cached));assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM ql_admin_page_permission_log WHERE user_id=?",Integer.class,user));
        b.setInherit(false);b.setPageIds(Collections.singletonList(2420L));assertThrows(CustomException.class,()->p.save(user,b,9L));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_admin_page_policy WHERE user_id=?",Integer.class,user));
    }
    @Test void accountPagePolicyNeverRestoresLostRoleGrantsAndOnlyAdminCanManageIt() {
        com.yicai.system.service.AccountPagePermissionService p=context.getBean(com.yicai.system.service.AccountPagePermissionService.class);long user=100000L+(++number);
        db.update("INSERT INTO sys_user(user_id,user_name,nick_name,status,del_flag) VALUES(?,?,?,'0','0')",user,"lost"+user,"Synthetic");db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,role_id FROM sys_role WHERE role_key='ql_operator'",user);
        com.yicai.system.domain.bo.AccountPagePermissionBo b=new com.yicai.system.domain.bo.AccountPagePermissionBo();b.setInherit(false);b.setPageIds(Collections.singletonList(2295L));p.save(user,b,9L);db.update("DELETE FROM sys_user_role WHERE user_id=?",user);
        assertThrows(CustomException.class,()->context.getBean(QlAdminSelectionService.class).customers(user));assertFalse(p.allowsPermission(user,"life:pass:add"));assertTrue(p.filterPermissions(user,new HashSet<>(Collections.singletonList("life:pass:add"))).isEmpty());
        assertEquals(Collections.singletonList("ql_admin"),db.queryForList("SELECT r.role_key FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE rm.menu_id=2421",String.class));
        db.update("INSERT IGNORE INTO sys_user(user_id,user_name,nick_name,status,del_flag) VALUES(1,'synthetic-superadmin','Synthetic superadmin','0','0')");assertThrows(CustomException.class,()->p.save(1L,b,9L));assertTrue(p.allowsPermission(1L,"life:pass:add"));
    }
    @Test @SuppressWarnings("unchecked") void ownProfileKeepsNicknameSeparateFromRealNameAndAllowsMissingNickname() {
        String owner=customer(),other=customer();
        db.update("UPDATE ql_customer SET nickname='小禾',real_name='测试实名' WHERE id=?",owner);
        db.update("UPDATE ql_customer SET nickname='其他轻友',real_name='其他实名' WHERE id=?",other);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:nickname-owner")).thenReturn(81991L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(81991L)).thenReturn(owner);
        Map<String,Object> overview=mini.overview("nickname-owner"),profile=(Map<String,Object>)overview.get("profile");
        assertEquals(owner,overview.get("customerId"));assertEquals("小禾",profile.get("nickname"));assertEquals("测试实名",profile.get("name"));
        db.update("UPDATE ql_customer SET nickname='' WHERE id=?",owner);
        profile=(Map<String,Object>)mini.overview("nickname-owner").get("profile");
        assertEquals("",profile.get("nickname"));assertEquals("测试实名",profile.get("name"));
        assertEquals("其他轻友",db.queryForObject("SELECT nickname FROM ql_customer WHERE id=?",String.class,other));
    }
    @Test void campReflectionConsentSnapshotAndWithdrawalHaveRealDatabaseBoundaries() {
        QlCampReflectionService voices=context.getBean(QlCampReflectionService.class);String owner=customer(),other=customer(),period=session(3),reg=add(owner,period,"confirmed");
        db.update("UPDATE ql_session SET start_date=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00')),end_date=DATE_ADD(DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00')),INTERVAL 2 DAY) WHERE id=?",period);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:reflection-owner")).thenReturn(81001L);when(context.getBean(QlCustomerIdentityService.class).resolve(81001L)).thenReturn(owner);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:reflection-other")).thenReturn(81002L);when(context.getBean(QlCustomerIdentityService.class).resolve(81002L)).thenReturn(other);
        voices.save("reflection-owner",reflectionBo(reg,"before",0,"私人草稿",false));
        assertEquals(0L,voices.list(period,null,null,1,10).get("total"));assertTrue(voices.publicVoices(period).isEmpty());
        assertThrows(CustomException.class,()->voices.save("reflection-other",reflectionBo(reg,"before",1,"不能代写",true)));
        QlCampReflectionBo denied=reflectionBo(reg,"before",1,"期待慢下来",true);denied.setShareConsent(false);assertThrows(CustomException.class,()->voices.save("reflection-owner",denied));
        voices.save("reflection-owner",reflectionBo(reg,"before",1,"期待慢下来",true));String id=db.queryForObject("SELECT id FROM ql_camp_reflection WHERE registration_id=?",String.class,reg);
        assertThrows(CustomException.class,()->voices.moderate(id,"publish",moderation(2,null),9L));assertThrows(CustomException.class,()->voices.moderate(id,"approve",moderation(1,null),9L));assertThrows(CustomException.class,()->voices.moderate(id,"approve",moderation(2,"伪造摘句"),9L));
        voices.moderate(id,"approve",moderation(2,"期待慢下来"),9L);voices.moderate(id,"publish",moderation(3,null),9L);
        Map<String,Object> publicRow=voices.publicVoices(period).get(0);assertEquals(new HashSet<>(Arrays.asList("id","phase","excerpt","note","author")),publicRow.keySet());assertEquals("本期轻友",publicRow.get("author"));assertTrue(voices.publicVoices(session(1)).isEmpty());
        voices.save("reflection-owner",reflectionBo(reg,"before",4,"更新后的私人文字",false));assertEquals("期待慢下来",voices.publicVoices(period).get(0).get("note"));
        voices.save("reflection-owner",reflectionBo(reg,"before",5,"新的自愿分享",true));assertTrue(voices.publicVoices(period).isEmpty());voices.moderate(id,"approve",moderation(6,null),9L);voices.moderate(id,"publish",moderation(7,null),9L);
        voices.withdraw("reflection-owner",period,"before");assertTrue(voices.publicVoices(period).isEmpty());assertEquals(0L,voices.list(period,null,null,1,10).get("total"));assertEquals("新的自愿分享",db.queryForObject("SELECT draft_note FROM ql_camp_reflection WHERE id=?",String.class,id));assertThrows(CustomException.class,()->voices.moderate(id,"publish",moderation(8,null),9L));
        assertEquals(9,db.queryForObject("SELECT COUNT(*) FROM ql_camp_reflection_log WHERE reflection_id=?",Integer.class,id));
    }
    @Test @SuppressWarnings("unchecked") void reflectionNicknameAndAnonymousSnapshotsRequireFreshReview() {
        QlCampReflectionService voices=context.getBean(QlCampReflectionService.class);String owner=customer(),period=session(3),reg=add(owner,period,"confirmed");
        db.update("UPDATE ql_session SET start_date=?,end_date=?,status='in_progress' WHERE id=?",java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))),java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2)),period);
        db.update("UPDATE ql_customer SET nickname='小禾',real_name='不应公开的姓名' WHERE id=?",owner);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:named-owner")).thenReturn(81101L);when(context.getBean(QlCustomerIdentityService.class).resolve(81101L)).thenReturn(owner);
        assertEquals("小禾",voices.mine("named-owner",period).get("authorName"));
        QlCampReflectionBo bo=reflectionBo(reg,"before",0,"以小名分享这一刻",true);bo.setAnonymous(false);voices.save("named-owner",bo);
        Map<String,Object> review=((List<Map<String,Object>>)voices.list(period,null,null,1,10).get("rows")).get(0);String id=String.valueOf(review.get("id"));assertEquals("小禾",review.get("author"));assertEquals(0,((Number)review.get("anonymous")).intValue());assertTrue(voices.publicVoices(period).isEmpty());
        voices.moderate(id,"approve",moderation(1,null),9L);voices.moderate(id,"publish",moderation(2,null),9L);assertEquals("小禾",voices.publicVoices(period).get(0).get("author"));
        db.update("UPDATE ql_customer SET nickname='小溪' WHERE id=?",owner);assertEquals("小禾",voices.publicVoices(period).get(0).get("author"));
        bo=reflectionBo(reg,"before",3,"改为匿名分享",true);bo.setAnonymous(true);voices.save("named-owner",bo);assertTrue(voices.publicVoices(period).isEmpty());assertNull(db.queryForObject("SELECT shared_author FROM ql_camp_reflection WHERE id=?",String.class,id));
        voices.moderate(id,"approve",moderation(4,null),9L);voices.moderate(id,"publish",moderation(5,null),9L);assertEquals("本期轻友",voices.publicVoices(period).get(0).get("author"));
        bo=reflectionBo(reg,"before",6,"只修改自己的草稿",false);bo.setAnonymous(false);voices.save("named-owner",bo);assertEquals("本期轻友",voices.publicVoices(period).get(0).get("author"));
        bo=reflectionBo(reg,"before",7,"再次以小名分享",true);bo.setAnonymous(false);voices.save("named-owner",bo);assertTrue(voices.publicVoices(period).isEmpty());voices.moderate(id,"approve",moderation(8,null),9L);voices.moderate(id,"publish",moderation(9,null),9L);assertEquals("小溪",voices.publicVoices(period).get(0).get("author"));voices.withdraw("named-owner",period,"before");assertTrue(voices.publicVoices(period).isEmpty());
    }
    @Test void namedReflectionWithoutNicknameFailsAndLegacyColumnsDefaultAnonymous() {
        QlCampReflectionService voices=context.getBean(QlCampReflectionService.class);String owner=customer(),period=session(3),reg=add(owner,period,"confirmed");
        db.update("UPDATE ql_session SET start_date=?,end_date=? WHERE id=?",java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))),java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2)),period);db.update("UPDATE ql_customer SET nickname='' WHERE id=?",owner);
        when(context.getBean(RedisCache.class).getCacheObject("appToken:blank-name")).thenReturn(81102L);when(context.getBean(QlCustomerIdentityService.class).resolve(81102L)).thenReturn(owner);
        QlCampReflectionBo named=reflectionBo(reg,"before",0,"小名未设置",true);named.setAnonymous(false);assertThrows(CustomException.class,()->voices.save("blank-name",named));
        voices.save("blank-name",reflectionBo(reg,"before",0,"旧客户端匿名分享",true));String id=db.queryForObject("SELECT id FROM ql_camp_reflection WHERE registration_id=?",String.class,reg);voices.moderate(id,"approve",moderation(1,null),9L);voices.moderate(id,"publish",moderation(2,null),9L);assertEquals("本期轻友",voices.publicVoices(period).get(0).get("author"));
        assertEquals("1",db.queryForObject("SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ql_camp_reflection' AND COLUMN_NAME='share_anonymous'",String.class));
    }
    @Test void campReflectionPermissionsDoNotExpandFinanceOrLeaderGrants() {
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE rm.menu_id IN (2410,2411,2412) AND r.role_key IN ('ql_finance','ql_leader')",Integer.class));
        assertEquals(6,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE rm.menu_id IN (2410,2411,2412) AND r.role_key IN ('ql_admin','ql_operator')",Integer.class));
    }
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
    @Test void sharedSensitiveGrantUpgradesExistingRolesIdempotently() throws Exception {
        Path migration=Paths.get(System.getProperty("qinglife.migrations"),"V1_4_4__shared_staff_assessment_view.sql");
        try(Connection connection=context.getBean(DataSource.class).getConnection()) {
            connection.setAutoCommit(false);
            try {
                JdbcTemplate upgraded=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                upgraded.update("DELETE rm FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE r.role_key IN ('ql_operator','ql_leader','ql_finance') AND rm.menu_id=2232");
                upgraded.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status) VALUES(99001,'Synthetic unrelated','synthetic_unrelated',99,'0')");
                List<Map<String,Object>> existing=upgraded.queryForList("SELECT role_id,menu_id FROM sys_role_menu ORDER BY role_id,menu_id");
                assertEquals(Collections.singletonList("ql_admin"),upgraded.queryForList(
                        "SELECT r.role_key FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE rm.menu_id=2232 ORDER BY r.role_key",String.class));
                for(int attempt=0;attempt<2;attempt++) {
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                            new org.springframework.core.io.support.EncodedResource(
                                    new org.springframework.core.io.FileSystemResource(migration),"UTF-8"));
                    assertEquals(Arrays.asList("ql_admin","ql_finance","ql_leader","ql_operator"),upgraded.queryForList(
                            "SELECT r.role_key FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE rm.menu_id=2232 ORDER BY r.role_key",String.class));
                    assertEquals(existing,upgraded.queryForList(
                            "SELECT rm.role_id,rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE NOT (r.role_key IN ('ql_operator','ql_leader','ql_finance') AND rm.menu_id=2232) ORDER BY rm.role_id,rm.menu_id"));
                    assertEquals(1,upgraded.queryForObject("SELECT COUNT(*) FROM ql_schema_migration WHERE version='1.4.4'",Integer.class));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test void realReadingsFilterPublicationTagsAndStableStoryIdentity() throws Exception {
        long tag=Long.parseLong(db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.reading.story_label_id'",String.class));
        long extendedTag=1900719925474110000L;
        java.util.List<Long> ids=new java.util.ArrayList<>();
        try {
            for(int i=0;i<11;i++) {
                long id=9007199254741000L+i;ids.add(id);
                db.update("INSERT INTO essay(id,title,Introduction,content,status,home_featured,order_num,title_url,author) VALUES(?,?,'Synthetic story summary','<p>Synthetic public story</p>',?,?,?,'',9)",id,"Synthetic reading "+i,i==10?0:1,i==9?0:1,i);
                db.update("INSERT INTO essay_label(essay,label) VALUES(?,?)",id,tag);
            }
            long general=9007199254741100L;ids.add(general);
            db.update("INSERT INTO essay(id,title,Introduction,content,status,home_featured,order_num,author) VALUES(?,'Synthetic general','General summary','<p>General</p>',1,1,50,9)",general);
            QlReadingQueryBo query=new QlReadingQueryBo();query.setPageSize(4);
            assertEquals(11L,mini.listReadings(query).get("total"));assertEquals(Boolean.TRUE,mini.listReadings(query).get("hasMore"));
            @SuppressWarnings("unchecked") java.util.List<Map<String,Object>> first=(java.util.List<Map<String,Object>>)mini.listReadings(query).get("list");
            assertEquals("9007199254741000",first.get(0).get("id"));assertEquals(4,first.size());
            query.setStories(true);assertEquals(10L,mini.listReadings(query).get("total"));
            query.setPageNum(3);assertEquals(Boolean.FALSE,mini.listReadings(query).get("hasMore"));
            query.setPageNum(1);query.setLabelId(String.valueOf(tag));query.setQuery("%");assertEquals(0L,mini.listReadings(query).get("total"));
            query.setQuery("Synthetic reading 9");assertEquals(1L,mini.listReadings(query).get("total"));
            @SuppressWarnings("unchecked") java.util.List<Map<String,Object>> stories=(java.util.List<Map<String,Object>>)mini.homeReadings().get("stories");
            assertEquals(8,stories.size());assertEquals(Boolean.TRUE,stories.get(0).get("isStory"));
            @SuppressWarnings("unchecked") java.util.List<Map<String,Object>> featured=(java.util.List<Map<String,Object>>)mini.homeReadings().get("featured");
            assertEquals(1,featured.size());assertEquals(String.valueOf(general),featured.get(0).get("id"));
            assertThrows(CustomException.class,()->mini.readingDetail(9007199254741010L));
            db.update("UPDATE label SET name='Synthetic renamed' WHERE id=?",tag);
            Path migration=Paths.get(System.getProperty("qinglife.migrations"),"V1_4_6__reading_story_tag.sql");
            try(Connection connection=context.getBean(DataSource.class).getConnection()) { org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.support.EncodedResource(new org.springframework.core.io.FileSystemResource(migration.toFile()),java.nio.charset.StandardCharsets.UTF_8)); }
            assertEquals(tag,Long.parseLong(db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.reading.story_label_id'",String.class)));
            assertEquals(8,((java.util.List<?>)mini.homeReadings().get("stories")).size());
            db.update("UPDATE label SET status=0 WHERE id=?",tag);assertTrue(((java.util.List<?>)mini.homeReadings().get("stories")).isEmpty());
            assertEquals(0L,mini.listReadings(query).get("total"));
            db.update("UPDATE label SET status=1 WHERE id=?",tag);
            db.update("INSERT INTO label(id,name,status,level) VALUES(?,'Synthetic long tag',1,0)",extendedTag);
            db.update("INSERT INTO essay_label(essay,label) VALUES(?,?)",ids.get(0),extendedTag);
            query.setStories(false);query.setQuery(null);query.setLabelId(String.valueOf(extendedTag));
            assertEquals(1L,mini.listReadings(query).get("total"));
            db.update("UPDATE sys_config SET config_value=? WHERE config_key='qinglife.reading.story_label_id'",String.valueOf(extendedTag));
            assertEquals(1,((java.util.List<?>)mini.homeReadings().get("stories")).size());
            query.setPageSize(51);assertThrows(CustomException.class,()->mini.listReadings(query));
        } finally {
            for(Long id:ids){db.update("DELETE FROM essay_label WHERE essay=?",id);db.update("DELETE FROM essay WHERE id=?",id);}
            db.update("UPDATE sys_config SET config_value=? WHERE config_key='qinglife.reading.story_label_id'",String.valueOf(tag));
            db.update("DELETE FROM label WHERE id=?",extendedTag);
            db.update("UPDATE label SET name='轻友故事',status=1 WHERE id=?",tag);
        }
    }
    @Test void automaticCoverSurvivesCreateNumberChangeAndManualUploadSwitch() {
        IQlSessionService sessions=context.getBean(IQlSessionService.class);
        QlSessionBo bo=new QlSessionBo();bo.setSessionNumber(88000);bo.setName("Synthetic automatic cover");bo.setTheme("Synthetic");bo.setStartDate(new java.util.Date());bo.setEndDate(new java.util.Date(System.currentTimeMillis()+2L*86400000));
        bo.setCoverUrl("");bo.setCapacity(10);bo.setCity("Synthetic");bo.setStatus("draft");
        assertTrue(sessions.insertByBo(bo,9L));
        String id=db.queryForObject("SELECT id FROM ql_session WHERE session_number=88000",String.class);
        String first=sessions.queryById(id).getCoverUrl();assertTrue(first.endsWith("/session-covers/v1-88000.png"));
        bo.setId(id);bo.setSessionNumber(88001);bo.setCoverUrl(first);assertTrue(sessions.updateByBo(bo,9L));
        assertTrue(sessions.queryById(id).getCoverUrl().endsWith("/session-covers/v1-88001.png"));
        bo.setCoverUrl("https://example.invalid/manual.png");assertTrue(sessions.updateByBo(bo,9L));
        assertEquals(bo.getCoverUrl(),sessions.queryById(id).getCoverUrl());
        bo.setCoverUrl("");assertTrue(sessions.updateByBo(bo,9L));
        assertTrue(Files.isRegularFile(mediaDirectory.resolve("public/session-covers/v1-88001.png")));
    }
    @Test void registrationDigitsFilterPreservesExactSelectionAndSessionOptionsUseNewestFive() {
        int[] periods={70801,71801,72801,73801,74801,75801,76801};
        for(int period:periods){String s=session(3);db.update("UPDATE ql_session SET session_number=? WHERE id=?",period,s);add(customer(),s,"pending");}
        org.springframework.mock.web.MockHttpServletRequest request=new org.springframework.mock.web.MockHttpServletRequest();
        request.setParameter("pageSize","5");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            QlRegistrationBo filter=new QlRegistrationBo();filter.setSessionNumberKeyword("801");
            assertEquals(7,registrations.queryPageList(filter).getTotal());
            filter.setSessionNumber(72801);assertEquals(1,registrations.queryPageList(filter).getTotal());
            filter.setSessionNumber(null);filter.setSessionNumberKeyword("0009");assertEquals(0,registrations.queryPageList(filter).getTotal());
            for(String invalid:Arrays.asList("%","1 OR 1=1","12345678901")){filter.setSessionNumberKeyword(invalid);assertThrows(CustomException.class,()->registrations.queryPageList(filter));}
            QlSessionBo options=new QlSessionBo();options.setName("Synthetic");
            java.util.List<com.yicai.life.domain.vo.QlSessionVo> rows=context.getBean(IQlSessionService.class).queryPageList(options).getRows();
            assertEquals(5,rows.size());
            for(int i=1;i<rows.size();i++)assertTrue(rows.get(i-1).getSessionNumber()>rows.get(i).getSessionNumber());
        } finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void sharedServiceRecordGrantIncludesNavigationAndPreservesActions() throws Exception {
        Path migration=Paths.get(System.getProperty("qinglife.migrations"),"V1_4_5__shared_staff_service_records.sql");
        try(Connection connection=context.getBean(DataSource.class).getConnection()) {
            connection.setAutoCommit(false);
            try {
                JdbcTemplate upgraded=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                upgraded.update("DELETE rm FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE (r.role_key='ql_finance' AND rm.menu_id=2292) OR (r.role_key IN ('ql_finance','ql_leader') AND rm.menu_id=2054)");
                List<Map<String,Object>> existing=upgraded.queryForList("SELECT role_id,menu_id FROM sys_role_menu ORDER BY role_id,menu_id");
                for(int attempt=0;attempt<2;attempt++) {
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                            new org.springframework.core.io.support.EncodedResource(
                                    new org.springframework.core.io.FileSystemResource(migration),"UTF-8"));
                    for(int menuId:Arrays.asList(2054,2292)) {
                        assertEquals(Arrays.asList("ql_admin","ql_finance","ql_leader","ql_operator"),upgraded.queryForList(
                                "SELECT r.role_key FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE rm.menu_id=? AND r.role_key IN ('ql_admin','ql_finance','ql_leader','ql_operator') ORDER BY r.role_key",String.class,menuId));
                    }
                    assertEquals(existing,upgraded.queryForList(
                            "SELECT rm.role_id,rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id WHERE NOT ((r.role_key='ql_finance' AND rm.menu_id=2292) OR (r.role_key IN ('ql_finance','ql_leader') AND rm.menu_id=2054)) ORDER BY rm.role_id,rm.menu_id"));
                    assertEquals(1,upgraded.queryForObject("SELECT COUNT(*) FROM ql_schema_migration WHERE version='1.4.5'",Integer.class));
                }
            } finally { connection.rollback(); }
        }
    }
    @Test void friendAssessmentIsAppendOnlyIdempotentAndSharedWithStaff() {
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ql_friend_assessment'",Integer.class));
        assertEquals(Arrays.asList(2230L,2231L,2232L),db.queryForList("SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 2230 AND 2232 ORDER BY menu_id",Long.class));
        assertEquals(Arrays.asList("ql_admin","ql_finance","ql_leader","ql_operator"),db.queryForList(
                "SELECT r.role_key FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms='life:assessment:sensitive' ORDER BY r.role_key",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id WHERE r.role_key IN ('ql_operator','ql_leader','ql_finance') AND rm.menu_id=2231",Integer.class));
        String customer=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:friend-token")).thenReturn(99L);when(identity.resolve(99L)).thenReturn(customer);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);QlFriendAssessmentBo form=assessment();
        Map<String,Object> first=service.submit("friend-token",form),retry=service.submit("friend-token",form);
        assertEquals(first.get("assessmentId"),retry.get("assessmentId"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,customer));
        form.setClientRequestId(UUID.randomUUID().toString());service.submit("friend-token",form);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,customer));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_consent_record WHERE customer_id=? AND consent_type='sensitive_profile'",Integer.class,customer));
    }
    @Test void originalArtifactFingerprintRemainsCompatibleForLegacySubmissionRetries() {
        String owner=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:legacy-contract")).thenReturn(706L);when(identity.resolve(706L)).thenReturn(owner);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);
        QlFriendAssessmentBo original=assessment();original.setClientRequestId("legacy_contract_r3");
        String id=String.valueOf(service.submit("legacy-contract",original).get("assessmentId"));
        // Generated with the actual previous reading-r4 artifact, not the new fingerprint helper.
        String oldFingerprint="bf753dd08c06e8900398a3edc7bbff959ad9dd75b7c41d8107dd0bead89fa99f";
        assertEquals(oldFingerprint,db.queryForObject("SELECT request_fingerprint FROM ql_friend_assessment WHERE id=?",String.class,id));
        assertEquals(id,service.submit("legacy-contract",original).get("assessmentId"));
    }
    @Test void assessmentWindowIgnoresDatabaseSessionTimeZone() throws Exception {
        String owner=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:amend-zone")).thenReturn(707L);when(identity.resolve(707L)).thenReturn(owner);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);
        String id=String.valueOf(service.submit("amend-zone",assessment()).get("assessmentId"));
        try(Connection connection=context.getBean(DataSource.class).getConnection()) {
            JdbcTemplate scoped=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            String original=scoped.queryForObject("SELECT @@session.time_zone",String.class);
            QlFriendAssessmentService reader=new QlFriendAssessmentService(cache,identity,scoped);
            try {
                for(String zone:Arrays.asList("+00:00","+08:00")) {
                    scoped.update("SET SESSION time_zone=?",zone);
                    assertEquals(true,reader.mine("amend-zone").get(0).get("canEdit"),zone);
                    scoped.update("UPDATE ql_friend_assessment SET submitted_at=? WHERE id=?",new Timestamp(System.currentTimeMillis()-TimeUnit.HOURS.toMillis(48)),id);
                    assertEquals(false,reader.mine("amend-zone").get(0).get("canEdit"),zone);
                    scoped.update("UPDATE ql_friend_assessment SET submitted_at=? WHERE id=?",new Timestamp(System.currentTimeMillis()),id);
                }
            } finally {scoped.update("SET SESSION time_zone=?",original);}
        }
    }
    @Test void assessmentAmendmentIsOwnedIdempotentAndKeepsOriginalDeadline() {
        String owner=customer();
        RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:amend-owner")).thenReturn(701L);when(identity.resolve(701L)).thenReturn(owner);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);
        QlFriendAssessmentBo original=assessment();original.setProvince("上海市");original.setCity("上海市");
        String id=String.valueOf(service.submit("amend-owner",original).get("assessmentId"));
        Timestamp submitted=db.queryForObject("SELECT submitted_at FROM ql_friend_assessment WHERE id=?",Timestamp.class,id);
        QlFriendAssessmentBo correction=assessment();correction.setProvince("浙江省");correction.setCity("杭州市");correction.setWeightKg(new BigDecimal("54.5"));correction.setRevision(1);
        service.amend("amend-owner",id,correction);
        assertEquals(2,((Number)service.amend("amend-owner",id,correction).get("revision")).intValue());
        assertEquals(submitted,db.queryForObject("SELECT submitted_at FROM ql_friend_assessment WHERE id=?",Timestamp.class,id));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment WHERE customer_id=?",Integer.class,owner));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment_edit_log WHERE assessment_id=?",Integer.class,id));
        assertEquals("55.5",db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(previous_values,'$.weight_kg')) FROM ql_friend_assessment_edit_log WHERE assessment_id=?",String.class,id));
        assertEquals("浙江省",service.myProfile("amend-owner").get("province"));
        correction.setWeightKg(new BigDecimal("53.5"));assertThrows(CustomException.class,()->service.amend("amend-owner",id,correction));
        correction.setClientRequestId(UUID.randomUUID().toString());assertThrows(CustomException.class,()->service.amend("amend-owner",id,correction));
        String other=customer();when(cache.getCacheObject("appToken:amend-other")).thenReturn(702L);when(identity.resolve(702L)).thenReturn(other);
        assertTrue(service.mine("amend-other").isEmpty());assertThrows(CustomException.class,()->service.amend("amend-other",id,correction));
        assertThrows(CustomException.class,()->service.amend(null,id,correction));
        assertEquals(new BigDecimal("54.5"),db.queryForObject("SELECT weight_kg FROM ql_friend_assessment WHERE id=?",BigDecimal.class,id));
    }
    @Test void assessmentExpiryUsesSubmissionTimeRatherThanLatestEditAndRejectsFutureRecords() {
        String owner=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:amend-expiry")).thenReturn(703L);when(identity.resolve(703L)).thenReturn(owner);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);
        String id=String.valueOf(service.submit("amend-expiry",assessment()).get("assessmentId"));
        QlFriendAssessmentBo correction=assessment();correction.setRevision(1);
        db.update("UPDATE ql_friend_assessment SET submitted_at=?,updated_at=? WHERE id=?",new Timestamp(System.currentTimeMillis()-TimeUnit.HOURS.toMillis(48)),new Timestamp(System.currentTimeMillis()),id);
        assertEquals(false,service.mine("amend-expiry").get(0).get("canEdit"));
        assertThrows(CustomException.class,()->service.amend("amend-expiry",id,correction));
        db.update("UPDATE ql_friend_assessment SET submitted_at=? WHERE id=?",new Timestamp(System.currentTimeMillis()+TimeUnit.HOURS.toMillis(1)),id);
        assertEquals(false,service.mine("amend-expiry").get(0).get("canEdit"));
        assertThrows(CustomException.class,()->service.amend("amend-expiry",id,correction));
        db.update("UPDATE ql_friend_assessment SET submitted_at=? WHERE id=?",new Timestamp(System.currentTimeMillis()-TimeUnit.HOURS.toMillis(47)),id);
        assertEquals(true,service.mine("amend-expiry").get(0).get("canEdit"));
        service.amend("amend-expiry",id,correction);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment_edit_log WHERE assessment_id=?",Integer.class,id));
    }
    @Test void earlierAssessmentCorrectionPreservesLatestProfileAndOtherSnapshots() {
        String owner=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:amend-history")).thenReturn(704L);when(identity.resolve(704L)).thenReturn(owner);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);
        QlFriendAssessmentBo first=assessment();first.setName("较早登记");first.setProvince("上海市");first.setCity("上海市");
        String firstId=String.valueOf(service.submit("amend-history",first).get("assessmentId"));
        db.update("UPDATE ql_friend_assessment SET submitted_at=? WHERE id=?",new Timestamp(System.currentTimeMillis()-TimeUnit.HOURS.toMillis(1)),firstId);
        QlFriendAssessmentBo second=assessment();second.setName("最新登记");second.setProvince("浙江省");second.setCity("杭州市");
        String secondId=String.valueOf(service.submit("amend-history",second).get("assessmentId"));
        QlFriendAssessmentBo correction=assessment();correction.setName("早期更正");correction.setPhone("13800009999");correction.setRevision(1);
        service.amend("amend-history",firstId,correction);
        assertEquals("最新登记",service.myProfile("amend-history").get("name"));
        assertEquals("13800001234",service.myProfile("amend-history").get("phone"));
        List<Map<String,Object>> history=service.mine("amend-history");
        assertEquals(secondId,history.get(0).get("id"));assertEquals("最新登记",history.get(0).get("name"));
        assertEquals("早期更正",history.get(1).get("name"));assertEquals("13800009999",history.get(1).get("phone"));
        Map<String,Object> masked=service.history(owner,false).get(1);
        assertEquals("138****9999",masked.get("phone"));assertFalse(masked.containsKey("profileSnapshot"));
        assertEquals(null,masked.get("medications"));assertFalse(masked.toString().contains("13800009999"));
    }
    @Test void legacyAssessmentSnapshotIsExplicitAndAnAuditFailureRollsBackEverything() {
        String owner=customer();RedisCache cache=context.getBean(RedisCache.class);QlCustomerIdentityService identity=context.getBean(QlCustomerIdentityService.class);
        when(cache.getCacheObject("appToken:amend-rollback")).thenReturn(705L);when(identity.resolve(705L)).thenReturn(owner);
        QlFriendAssessmentService service=context.getBean(QlFriendAssessmentService.class);
        String id=String.valueOf(service.submit("amend-rollback",assessment()).get("assessmentId"));
        db.update("UPDATE ql_friend_assessment SET profile_snapshot=NULL WHERE id=?",id);
        assertEquals(false,service.mine("amend-rollback").get(0).get("profileSnapshotAvailable"));
        QlFriendAssessmentBo correction=assessment();correction.setRevision(1);correction.setName("拒绝保存");
        db.execute("CREATE TRIGGER reject_assessment_edit BEFORE INSERT ON ql_friend_assessment_edit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic rollback check'");
        try {assertThrows(RuntimeException.class,()->service.amend("amend-rollback",id,correction));}
        finally {db.execute("DROP TRIGGER reject_assessment_edit");}
        assertEquals("测试轻友",service.myProfile("amend-rollback").get("name"));
        assertEquals(1,db.queryForObject("SELECT revision FROM ql_friend_assessment WHERE id=?",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_friend_assessment_edit_log WHERE assessment_id=?",Integer.class,id));
    }

    void bindR10(String token,long appId,String customer) {
        when(context.getBean(RedisCache.class).getCacheObject("appToken:"+token)).thenReturn(appId);
        when(context.getBean(QlCustomerIdentityService.class).resolve(appId)).thenReturn(customer);
    }
    String formCode(QlFriendAssessmentService forms,String session) {
        String path=String.valueOf(forms.createAssessmentInvitation(session,9L).get("path"));return path.substring(path.indexOf("invite=")+7);
    }
    QlMiniAppRegistrationBo referralSignup(String session,String code) {
        QlMiniAppRegistrationBo b=new QlMiniAppRegistrationBo();b.setSessionId(session);b.setInvitationCode(code);b.setClientRequestId(UUID.randomUUID().toString());b.setServiceConsent(true);b.setContactName("Synthetic");b.setContactPhone("13800000000");
        QlMiniAppRegistrationBo.Participant p=new QlMiniAppRegistrationBo.Participant();p.setSelf(true);p.setName("Synthetic");p.setMinor(false);b.setParticipants(Collections.singletonList(p));return b;
    }
    @Test void sharedPeriodFormsAppendToAuthenticatedOwnerAndRetryAfterClose() {
        String owner=customer(),other=customer(),firstPeriod=session(2),secondPeriod=session(2);bindR10("r10-forms",91001L,owner);bindR10("r10-other",91002L,other);
        QlFriendAssessmentService forms=context.getBean(QlFriendAssessmentService.class);forms.submit("r10-forms",assessment());
        String firstCode=formCode(forms,firstPeriod),secondCode=formCode(forms,secondPeriod);QlFriendAssessmentBo first=assessment();first.setInvitationCode(firstCode);
        Map<String,Object> saved=forms.submit("r10-forms",first);assertEquals(firstPeriod,db.queryForObject("SELECT session_id FROM ql_friend_assessment WHERE id=?",String.class,saved.get("assessmentId")));
        assertEquals(firstPeriod,forms.assessmentInvitation("r10-other",firstCode).get("sessionId"));assertTrue(forms.mine("r10-other").isEmpty());
        QlFriendAssessmentBo second=assessment();second.setInvitationCode(secondCode);forms.submit("r10-forms",second);
        assertEquals(3,forms.mine("r10-forms").size());assertEquals(2,forms.mine("r10-forms").stream().filter(r->r.get("sessionId")!=null).count());
        db.update("UPDATE ql_session SET status='completed' WHERE id=?",firstPeriod);
        assertEquals(saved.get("assessmentId"),forms.submit("r10-forms",first).get("assessmentId"));assertEquals(3,forms.mine("r10-forms").size());
        first.setClientRequestId(UUID.randomUUID().toString());assertThrows(CustomException.class,()->forms.submit("r10-forms",first));
    }
    @Test void assessmentLinksRequireLoginCorrectPurposeAndAnUnfinishedPublishedPeriod() {
        String owner=customer(),s=session(2);bindR10("r10-gates",91003L,owner);QlFriendAssessmentService forms=context.getBean(QlFriendAssessmentService.class);String code=formCode(forms,s);
        assertThrows(CustomException.class,()->forms.assessmentInvitation(null,code));
        for(String bad:Arrays.asList("customer=private-data",String.join("",Collections.nCopies(32,"0"))))assertThrows(CustomException.class,()->forms.assessmentInvitation("r10-gates",bad));
        String signupCode=String.valueOf(mini.createInvitation(null,s,true).get("code"));assertThrows(CustomException.class,()->forms.assessmentInvitation("r10-gates",signupCode));assertThrows(CustomException.class,()->mini.resolveInvitation(code));
        assertThrows(CustomException.class,()->mini.register("r10-gates",referralSignup(s,code)));
        db.update("UPDATE ql_session SET status='in_progress',registration_close_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",s);
        QlFriendAssessmentBo b=assessment();b.setInvitationCode(code);forms.submit("r10-gates",b); // Enrolment cutoff does not block the period's own form.
        assertEquals(s,forms.mine("r10-gates").get(0).get("sessionId"));
        for(String status:Arrays.asList("draft","cancelled","completed")){db.update("UPDATE ql_session SET status=? WHERE id=?",status,s);assertThrows(CustomException.class,()->forms.createAssessmentInvitation(s,9L));assertThrows(CustomException.class,()->forms.assessmentInvitation("r10-gates",code));}
        db.update("UPDATE ql_session SET status='open',end_date=DATE_SUB(CURRENT_DATE(),INTERVAL 1 DAY),start_date=DATE_SUB(CURRENT_DATE(),INTERVAL 2 DAY) WHERE id=?",s);assertThrows(CustomException.class,()->forms.assessmentInvitation("r10-gates",code));
    }
    @Test void amendmentsPreservePeriodAndAnotherAccountCannotEditTheRecord() {
        String owner=customer(),other=customer(),s=session(3),t=session(3);bindR10("r10-edit",91004L,owner);bindR10("r10-foreign",91005L,other);QlFriendAssessmentService forms=context.getBean(QlFriendAssessmentService.class);
        QlFriendAssessmentBo first=assessment();first.setInvitationCode(formCode(forms,s));String id=String.valueOf(forms.submit("r10-edit",first).get("assessmentId"));
        QlFriendAssessmentBo correction=assessment();correction.setRevision(1);correction.setInvitationCode(formCode(forms,t));assertThrows(CustomException.class,()->forms.amend("r10-edit",id,correction));
        correction.setInvitationCode(null);assertThrows(CustomException.class,()->forms.amend("r10-foreign",id,correction));forms.amend("r10-edit",id,correction);
        assertEquals(s,db.queryForObject("SELECT session_id FROM ql_friend_assessment WHERE id=?",String.class,id));assertEquals(1,forms.mine("r10-edit").size());
        Map<String,Object> publicHistory=forms.history(owner,false).get(0);assertFalse(publicHistory.containsKey("invitationCode"));assertNull(publicHistory.get("medications"));
    }
    @Test void referralRecordsAreOwnedAndKeepFirstAndPerPeriodAttributionWithoutRewards() {
        String referrer=customer(),other=customer(),newFriend=customer(),s=session(10),prior=session(2);bindR10("r10-inviter",91006L,referrer);bindR10("r10-invitee",91007L,newFriend);bindR10("r10-unrelated",91008L,other);
        assertNotNull(mini.createInvitation("r10-inviter",s,false).get("code"));
        String completed=add(referrer,prior,"confirmed");db.update("UPDATE ql_session SET status='completed' WHERE id=?",prior);
        db.update("INSERT INTO ql_participation(id,customer_id,session_id,registration_id,attendance_status) VALUES(?,?,?,?,'completed')",UUID.randomUUID().toString(),referrer,prior,completed);
        String code=String.valueOf(mini.createInvitation("r10-inviter",s,false).get("code"));int money=db.queryForObject("SELECT COUNT(*) FROM ql_transaction",Integer.class),cards=db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger",Integer.class);
        QlMiniAppRegistrationBo signup=referralSignup(s,code);mini.register("r10-invitee",signup);mini.register("r10-invitee",signup);
        assertEquals(referrer,db.queryForObject("SELECT referrer_customer_id FROM ql_customer WHERE id=?",String.class,newFriend));assertEquals(1,mini.myReferrals("r10-inviter").size());assertTrue(mini.myReferrals("r10-unrelated").isEmpty());assertThrows(CustomException.class,()->mini.myReferrals(null));
        Map<String,Object> row=mini.myReferrals("r10-inviter").get(0);assertFalse(row.containsKey("phone"));assertFalse(row.containsKey("realName"));assertFalse(row.containsKey("paymentStatus"));
        String next=session(5);String otherCode=String.valueOf(mini.createInvitation(null,next,true).get("code"));db.update("UPDATE ql_invitation SET owner_customer_id=?,source='referral' WHERE code=?",other,otherCode);mini.register("r10-invitee",referralSignup(next,otherCode));
        assertEquals(referrer,db.queryForObject("SELECT referrer_customer_id FROM ql_customer WHERE id=?",String.class,newFriend));assertEquals(other,db.queryForObject("SELECT session_referrer_customer_id FROM ql_registration WHERE customer_id=? AND session_id=?",String.class,newFriend,next));
        status(String.valueOf(row.get("registrationId")),"confirmed");assertEquals("confirmed",mini.myReferrals("r10-inviter").get(0).get("registrationStatus"));
        assertEquals(money,db.queryForObject("SELECT COUNT(*) FROM ql_transaction",Integer.class));assertEquals(cards,db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger",Integer.class));
    }
    @Test void everyLoggedInFriendMayInviteWithoutChangingReturningEligibility() {
        String owner=customer(),s=session(3);bindR10("r11-new",91101L,owner);
        assertEquals(Boolean.TRUE,mini.overview("r11-new").get("invitationEligible"));
        assertEquals(Boolean.FALSE,mini.overview("r11-new").get("returningEligible"));
        assertNotNull(mini.createInvitation("r11-new",s,false).get("code"));
        db.update("UPDATE ql_customer SET birth_date=DATE_SUB(CURRENT_DATE(),INTERVAL 12 YEAR) WHERE id=?",owner);
        assertNotNull(mini.createInvitation("r11-new",s,false).get("code"));
        assertThrows(CustomException.class,()->mini.createInvitation(null,s,false));
        assertThrows(CustomException.class,()->mini.createInvitation("r11-missing",s,false));
        db.update("UPDATE ql_session SET status='closed' WHERE id=?",s);
        assertThrows(CustomException.class,()->mini.createInvitation("r11-new",s,false));
    }
    @Test void internalAccountSearchUsesCanonicalNicknameAndMasksPhoneWithoutMatchingAccountNumbers() {
        String owner=customer(),name="R11小禾";
        db.update("UPDATE ql_customer SET nickname=?,real_name='R11私有实名' WHERE id=?",name,owner);
        db.update("INSERT INTO app_user_info(id,nick_name,status,last_login_time) VALUES(91102,'R11微信称呼',1,NOW()),(91103,'R11未登记',1,NOW()),(91104,'R11停用',0,NOW())");
        for(int i=0;i<1;i++)db.update("INSERT INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,is_primary,verification_status,legacy_app_user_id) VALUES(?,?,'wechat_openid',?,?,?,'verified',91102)",UUID.randomUUID().toString(),owner,"r11-link-"+i,String.format("%064d",911020+i),i==0?1:0);
        db.update("INSERT INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,is_primary,verification_status) VALUES(?,?,'phone','13855551234',?,1,'verified')",UUID.randomUUID().toString(),owner,String.format("%064d",911022));
        List<Map<String,Object>> rows=staff.appUsers(name);assertEquals(1,rows.size());
        assertEquals(name,rows.get(0).get("name"));assertEquals("138****1234",rows.get(0).get("phoneHint"));assertEquals(91102L,((Number)rows.get(0).get("id")).longValue());
        assertFalse(rows.toString().contains("13855551234"));assertFalse(rows.toString().contains("R11私有实名"));
        assertEquals(1,staff.appUsers("55551234").size());assertTrue(staff.appUsers("91102").isEmpty());assertTrue(staff.appUsers("R11停用").isEmpty());
        assertTrue(staff.appUsers("R11%").isEmpty());assertTrue(staff.appUsers("R11_").isEmpty());
        assertNull(staff.appUsers("R11未登记").get(0).get("phoneHint"));
        db.update("UPDATE ql_customer_identifier SET valid_to=NOW() WHERE customer_id=? AND identifier_type='phone'",owner);
        assertTrue(staff.appUsers("55551234").isEmpty());assertNull(staff.appUsers(name).get(0).get("phoneHint"));
        assertThrows(CustomException.class,()->staff.appUsers(String.join("",Collections.nCopies(65,"a"))));
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
