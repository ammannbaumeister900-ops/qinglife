package com.yicai.web.integration;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.*;
import com.yicai.life.domain.vo.QlSessionVo;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.*;
import com.yicai.web.tools.DatabaseMigration;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All rows and identities are synthetic in a newly created loopback database. No payment provider is simulated. */
class SessionRulesIT {
    static String url, database;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate db;
    static IQlSessionService sessions;
    static IQlRegistrationService registrations;
    static IQlMiniAppService mini;
    static int number = 910000;
    @org.junit.jupiter.api.io.TempDir static Path media;
    static String previousProfile, previousImage;
    static final List<Long> GUIDES = Arrays.asList(9007199254740993L,9007199254740994L,9007199254740995L);
    @Configuration @Import(RegistrationReadinessIT.Config.class)
    static class Config {
        @Bean @Primary DataSource taskDataSource() {
            return new DriverManagerDataSource(url,"root",Objects.toString(System.getenv("QINGLIFE_TEST_MYSQL_PASSWORD"),""));
        }
    }
    @BeforeAll static void setup() throws Exception {
        String base = System.getenv("QINGLIFE_TEST_MYSQL_URL");
        if(base==null || !base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_[a-z0-9_]+\\?.*")) throw new IllegalStateException("New loopback test database required");
        database = "qinglife_it_rules_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        String host = base.substring(0,base.indexOf("/qinglife_it_"));
        String parameters = base.substring(base.indexOf("?"));
        try(Connection c=DriverManager.getConnection(host+"/"+parameters,"root",Objects.toString(System.getenv("QINGLIFE_TEST_MYSQL_PASSWORD"),""));Statement s=c.createStatement()) { s.execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4"); }
        url=host+"/"+database+parameters;
        previousProfile=com.yicai.common.config.RuoYiConfig.getProfile(); previousImage=com.yicai.common.config.RuoYiConfig.getImagePath();
        com.yicai.common.config.RuoYiConfig configuration=new com.yicai.common.config.RuoYiConfig();
        configuration.setProfile(media.toString());configuration.setImagePath("https://example.invalid/api/profile/public/");
        context=new AnnotationConfigApplicationContext(Config.class); db=context.getBean(JdbcTemplate.class);
        try(Connection c=context.getBean("taskDataSource",DataSource.class).getConnection()) { DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent()); }
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,status,del_flag) VALUES(9,0,'synthetic-operator','Synthetic operator','00','0','0')");
        String[] names={"公主","大海","军军"};
        for(int i=0;i<3;i++) db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,status,del_flag) VALUES(?,0,?,?,'00','0','0')",GUIDES.get(i),"synthetic-guide-"+i,names[i]);
        sessions=context.getBean(IQlSessionService.class);registrations=context.getBean(IQlRegistrationService.class);mini=context.getBean(IQlMiniAppService.class);
        System.out.println("SESSION_RULES_ISOLATED_DATABASE="+database);
    }
    @AfterAll static void cleanup() {
        if(context!=null)context.close();
        com.yicai.common.config.RuoYiConfig configuration=new com.yicai.common.config.RuoYiConfig();
        configuration.setProfile(previousProfile);configuration.setImagePath(previousImage);
    }
    QlSessionBo newSession() {
        QlSessionBo bo=new QlSessionBo();bo.setSessionNumber(++number);bo.setStartDate(java.sql.Date.valueOf("2099-01-15"));bo.setEndDate(java.sql.Date.valueOf("2099-01-17"));bo.setCoverUrl("https://example.invalid/synthetic.png");return bo;
    }
    String save(QlSessionBo bo) {
        assertTrue(sessions.insertByBo(bo,9L));
        return db.queryForObject("SELECT id FROM ql_session WHERE session_number=?",String.class,bo.getSessionNumber());
    }
    QlSessionBo editing(String id) { return cn.hutool.core.bean.BeanUtil.toBean(context.getBean(com.yicai.life.mapper.QlSessionMapper.class).selectById(id),QlSessionBo.class); }
    String customer() {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_customer(id,customer_no,nickname,status) VALUES(?,?,?,'active')",id,"T"+id.replace("-","").substring(0,20),"Synthetic");return id;
    }
    String openSession(int capacity) {
        QlSessionBo bo=newSession();bo.setStatus("open");bo.setCapacity(capacity);bo.setStandardPrice(new BigDecimal("100"));bo.setReturningPrice(new BigDecimal("50"));
        String id=save(bo);db.update("UPDATE ql_session SET start_date=DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY),end_date=DATE_ADD(CURRENT_DATE(),INTERVAL 3 DAY),registration_open_at=DATE_SUB(NOW(),INTERVAL 1 DAY),registration_close_at=DATE_ADD(NOW(),INTERVAL 6 HOUR) WHERE id=?",id);return id;
    }
    // Historic fixture only. This deliberately bypasses the new-registration API and cannot prove new payment completion.
    String legacyConfirmed(String owner,String session) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_registration(id,customer_id,session_id,registration_status,payment_status,registration_source,registered_at,unit_price,confirmed_at,confirmed_by) VALUES(?,?,?,'confirmed','unpaid','web_admin',NOW(),100,NOW(),9)",id,owner,session);return id;
    }
    void attendance(String owner,String session,String registration,String status) {
        db.update("INSERT INTO ql_participation(id,customer_id,session_id,registration_id,attendance_status) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),owner,session,registration,status);
    }
    void history(String owner,String status,boolean arrived) {
        String session=openSession(9),registration=legacyConfirmed(owner,session);
        db.update("UPDATE ql_session SET status=?,start_date='2000-01-01',end_date='2000-01-03' WHERE id=?",status,session);
        if(arrived)attendance(owner,session,registration,"completed");
    }
    void bind(String token,String owner,long app) {
        when(context.getBean(RedisCache.class).getCacheObject("appToken:"+token)).thenReturn(app);
        when(context.getBean(QlCustomerIdentityService.class).resolve(app)).thenReturn(owner);
        // A bound mini-program friend has passed WeChat phone authorization.
        db.update("INSERT IGNORE INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,display_hint,app_scope,is_primary,verification_status,valid_from) VALUES(?,?,'phone','13900000000',SHA2('13900000000',256),'139****0000','wechat-phone',0,'verified',NOW(3))",UUID.randomUUID().toString(),owner);
    }
    QlMiniAppRegistrationBo signup(String session, QlMiniAppRegistrationBo.Participant... people) {
        QlMiniAppRegistrationBo bo=new QlMiniAppRegistrationBo();bo.setSessionId(session);bo.setClientRequestId(UUID.randomUUID().toString());bo.setServiceConsent(true);bo.setContactName("Synthetic contact");bo.setContactPhone("13800000000");bo.setParticipants(Arrays.asList(people));return bo;
    }
    QlMiniAppRegistrationBo.Participant person(boolean self,String id) {
        QlMiniAppRegistrationBo.Participant p=new QlMiniAppRegistrationBo.Participant();p.setSelf(self);p.setName(self?"Synthetic self":"Synthetic companion");p.setCustomerId(id);p.setRelation("家人");return p;
    }
    QlPaymentConfirmationBo cash() { QlPaymentConfirmationBo bo=new QlPaymentConfirmationBo();bo.setPaymentMethod("cash");bo.setAmount(new BigDecimal("100"));bo.setNote("Synthetic legacy receipt");return bo; }
    String stateAt(String id,String instant) {
        String expression=QlSessionAdmissionPolicy.STATUS_SQL.replace(QlSessionAdmissionPolicy.CLOCK_SQL,"CAST('"+instant+"' AS DATETIME(3))");
        return db.queryForObject("SELECT "+expression+" FROM ql_session s WHERE s.id=?",String.class,id);
    }

    @Test void defaultsOnlyApplyToNewSessionsAndThemeIsOptional() {
        QlSessionBo bo=newSession();String id=save(bo);QlSessionVo row=sessions.queryById(id);
        assertEquals("生机清体营",row.getName());assertEquals("上海市安亭驿岛酒店",row.getPublicVenue());assertEquals(50,row.getCapacity());
        assertEquals(new BigDecimal("3800.00"),row.getStandardPrice());assertEquals(new BigDecimal("2500.00"),row.getReturningPrice());assertEquals("auto",row.getRegistrationConfirmMode());
        assertNull(row.getIntro());assertNull(row.getCancelPolicy());assertNull(row.getTheme());
        assertEquals("2099-01-01 00:00:00",db.queryForObject("SELECT DATE_FORMAT(registration_open_at,'%Y-%m-%d %H:%i:%s') FROM ql_session WHERE id=?",String.class,id));
        assertEquals("2099-01-14 23:59:59",db.queryForObject("SELECT DATE_FORMAT(registration_close_at,'%Y-%m-%d %H:%i:%s') FROM ql_session WHERE id=?",String.class,id));
        assertEquals(Arrays.asList("9007199254740993","9007199254740994","9007199254740995"),row.getGuideIds());
    }
    @Test void explicitNewOverridesAndDeliberateNullDatesAreKept() {
        QlSessionBo bo=newSession();bo.setCapacity(7);bo.setStandardPrice(BigDecimal.ZERO);bo.setReturningPrice(new BigDecimal("88"));bo.setRegistrationConfirmMode("manual");
        bo.setGuideIds(Collections.singletonList(GUIDES.get(1)));bo.setRegistrationOpenManual(true);bo.setRegistrationCloseManual(true);
        String id=save(bo);QlSessionVo row=sessions.queryById(id);
        assertNull(row.getRegistrationOpenAt());assertNull(row.getRegistrationCloseAt());assertEquals(7,row.getCapacity());assertEquals("manual",row.getRegistrationConfirmMode());assertEquals(BigDecimal.ZERO.setScale(2),row.getStandardPrice());
        assertEquals("大海",row.getLeaderName());
    }
    @Test void historicalFreeTextUnknownGuidesAndNullDatesSurviveEditing() {
        String id=save(newSession());db.update("UPDATE ql_session SET name='历史名称',public_venue=NULL,theme='历史主题',leader_name='历史人员甲',guide_user_ids=NULL,capacity=19,standard_price=12,returning_price=9,registration_confirm_mode='manual',registration_open_at=NULL,registration_close_at=NULL,cover_url='public/session-covers/v1-12.png' WHERE id=?",id);
        QlSessionBo edit=editing(id);edit.setStartDate(java.sql.Date.valueOf("2099-02-01"));edit.setEndDate(java.sql.Date.valueOf("2099-02-03"));edit.setCoverUrl(sessions.queryById(id).getCoverUrl());
        assertTrue(sessions.updateByBo(edit,9L));QlSessionVo row=sessions.queryById(id);
        assertEquals("历史名称",row.getName());assertNull(row.getPublicVenue());assertEquals("历史主题",row.getTheme());assertEquals("历史人员甲",row.getLeaderName());assertNull(row.getGuideIds());
        assertNull(row.getRegistrationOpenAt());assertNull(row.getRegistrationCloseAt());assertEquals(19,row.getCapacity());assertEquals("manual",row.getRegistrationConfirmMode());assertTrue(row.getCoverUrl().endsWith("/v1-12.png"));
    }
    @Test void dictionaryAddEditDisableAndDeleteDoNotRewriteHistoricalValues() {
        String id=save(newSession());
        db.update("INSERT INTO sys_dict_data(dict_label,dict_value,dict_type,status) VALUES('新选项','新选项','ql_session_name','0')");
        try {
            QlSessionBo edit=editing(id);edit.setName("新选项");assertTrue(sessions.updateByBo(edit,9L));
            db.update("UPDATE sys_dict_data SET dict_label='重命名标签',dict_value='改后的键值' WHERE dict_type='ql_session_name' AND dict_value='新选项'");
            assertTrue(sessions.updateByBo(edit,9L)); // Historical value is now absent from the dictionary.
            edit.setName("改后的键值");assertTrue(sessions.updateByBo(edit,9L));
            db.update("UPDATE sys_dict_data SET status='1' WHERE dict_type='ql_session_name' AND dict_value='改后的键值'");
            QlSessionBo fresh=newSession();fresh.setName("改后的键值");assertThrows(CustomException.class,()->save(fresh));
            assertTrue(sessions.updateByBo(edit,9L));db.update("DELETE FROM sys_dict_data WHERE dict_type='ql_session_name' AND dict_value='改后的键值'");assertTrue(sessions.updateByBo(edit,9L));
        } finally {db.update("DELETE FROM sys_dict_data WHERE dict_type='ql_session_name' AND dict_value IN ('新选项','改后的键值')");}
    }
    @Test void staffIdsAreAuthoritativeAndMissingAmbiguousOrInactiveDefaultsFailClosed() {
        QlSessionBo bo=newSession();bo.setGuideIds(Collections.singletonList(GUIDES.get(0)));bo.setLeaderName("伪造姓名");assertEquals("公主",sessions.queryById(save(bo)).getLeaderName());
        bo=newSession();bo.setGuideIds(Collections.singletonList(123456789L));QlSessionBo invalid=bo;assertThrows(CustomException.class,()->save(invalid));
        db.update("UPDATE sys_user SET status='1' WHERE user_id=?",GUIDES.get(2));
        try {assertThrows(CustomException.class,()->save(newSession()));}finally{db.update("UPDATE sys_user SET status='0' WHERE user_id=?",GUIDES.get(2));}
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,status,del_flag) VALUES(123456789,0,'synthetic-duplicate','公主','00','0','0')");
        try {assertThrows(CustomException.class,()->save(newSession()));}finally{db.update("DELETE FROM sys_user WHERE user_id=123456789");}
    }
    @Test void clockBoundariesAndManualOverridesUseOneSqlRule() {
        String id=openSession(3);db.update("UPDATE ql_session SET start_date='2026-11-15',end_date='2026-11-17',registration_open_at='2026-11-01 00:00:00',registration_close_at='2026-11-14 23:59:59' WHERE id=?",id);
        assertEquals("closed",stateAt(id,"2026-10-31 23:59:59.999"));assertEquals("open",stateAt(id,"2026-11-01 00:00:00.000"));
        assertEquals("open",stateAt(id,"2026-11-14 23:59:59.000"));assertEquals("closed",stateAt(id,"2026-11-14 23:59:59.001"));
        assertEquals("in_progress",stateAt(id,"2026-11-15 00:00:00.000"));assertEquals("in_progress",stateAt(id,"2026-11-17 23:59:59.999"));assertEquals("completed",stateAt(id,"2026-11-18 00:00:00.000"));
        for(String special:Arrays.asList("draft","closed","cancelled","completed")) {db.update("UPDATE ql_session SET status=? WHERE id=?",special,id);assertEquals(special,stateAt(id,"2026-11-15 12:00:00.000"));}
    }
    @Test void listFilterDetailAndPublicApiReflectTimeWithoutPersistingDerivedStatus() {
        String id=openSession(3);db.update("UPDATE ql_session SET start_date='2000-01-01',end_date='2000-01-03' WHERE id=?",id);
        assertEquals("completed",sessions.queryById(id).getStatus());assertEquals("open",sessions.queryById(id).getConfiguredStatus());assertEquals("completed",mini.sessionDetail(id).get("status"));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        try {QlSessionBo filter=new QlSessionBo();filter.setSessionNumber(sessions.queryById(id).getSessionNumber());filter.setStatus("completed");assertEquals(1,sessions.queryPageList(filter).getTotal());filter.setStatus("open");assertEquals(0,sessions.queryPageList(filter).getTotal());}finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
        assertEquals("open",db.queryForObject("SELECT status FROM ql_session WHERE id=?",String.class,id));
    }
    @Test @SuppressWarnings("unchecked") void eachParticipantUsesTheirOwnActualHistoryNotTheBuyers() {
        String buyer=customer();history(buyer,"open",true);bind("price-owner",buyer,51001L);
        Map<String,Object> result=mini.register("price-owner",signup(openSession(1),person(true,null),person(false,null)));
        List<Map<String,Object>> rows=(List<Map<String,Object>>)result.get("registrations");assertEquals(new BigDecimal("150.00"),new BigDecimal(result.get("quotedAmount").toString()).setScale(2));
        assertEquals(new BigDecimal("50.00"),db.queryForObject("SELECT unit_price FROM ql_registration WHERE id=?",BigDecimal.class,rows.get(0).get("registrationId")));
        String child=String.valueOf(rows.get(1).get("customerId"));assertEquals(new BigDecimal("100.00"),db.queryForObject("SELECT unit_price FROM ql_registration WHERE id=?",BigDecimal.class,rows.get(1).get("registrationId")));
        history(child,"completed",true);
        Map<String,Object> next=mini.register("price-owner",signup(openSession(1),person(true,null),person(false,child)));
        assertEquals(new BigDecimal("100.00"),new BigDecimal(next.get("quotedAmount").toString()).setScale(2));
    }
    @Test void noAttendanceCancelledOrPendingHistoryCannotGrantReturningPrice() {
        QlSessionPricing price=context.getBean(QlSessionPricing.class);String owner=customer();history(owner,"completed",false);history(owner,"cancelled",true);
        assertEquals(new BigDecimal("3800"),price.price(owner,new BigDecimal("3800"),new BigDecimal("2500")));
        String s=openSession(2),r=legacyConfirmed(owner,s);db.update("UPDATE ql_session SET status='completed' WHERE id=?",s);attendance(owner,s,r,"completed");db.update("UPDATE ql_registration SET registration_status='pending' WHERE id=?",r);
        assertEquals(new BigDecimal("3800"),price.price(owner,new BigDecimal("3800"),new BigDecimal("2500")));
    }
    @Test @SuppressWarnings("unchecked") void autoAndZeroPriceSubmissionStayUnpaidPendingIdempotentAndSeatFree() {
        String owner=customer(),s=openSession(1);db.update("UPDATE ql_session SET standard_price=0,returning_price=0,registration_confirm_mode='auto' WHERE id=?",s);bind("unpaid-owner",owner,51002L);
        QlMiniAppRegistrationBo bo=signup(s,person(true,null));Map<String,Object> result=mini.register("unpaid-owner",bo);
        assertEquals("pending",result.get("registrationStatus"));assertEquals("unpaid",result.get("paymentStatus"));assertEquals(result.get("id"),mini.register("unpaid-owner",bo).get("id"));
        assertEquals(0,context.getBean(QlMiniAppMapper.class).selectOccupiedRegistrations(s).size());
        assertEquals(0,((Number)mini.sessionDetail(s).get("registeredCount")).intValue());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=? AND confirmed_at IS NOT NULL",Integer.class,s));
        String r=String.valueOf(((List<Map<String,Object>>)result.get("registrations")).get(0).get("registrationId"));
        QlRegistrationBo edit=new QlRegistrationBo();edit.setId(r);edit.setRegistrationStatus("confirmed");assertThrows(CustomException.class,()->registrations.updateByBo(edit,9L));assertThrows(CustomException.class,()->registrations.confirmPayment(r,cash(),9L));
        QlRegistrationBo forged=new QlRegistrationBo();forged.setCustomerId(customer());forged.setSessionId(s);forged.setRegistrationStatus("confirmed");assertThrows(CustomException.class,()->registrations.insertByBo(forged,9L));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,s));
    }
    @Test void concurrentLegacyReceiptsCompeteForOnePaidSeatAndLoserRollsBack() throws Exception {
        String s=openSession(1),a=legacyConfirmed(customer(),s),b=legacyConfirmed(customer(),s);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        List<Future<Boolean>> futures=new ArrayList<>();
        try {for(String r:Arrays.asList(a,b))futures.add(pool.submit(()->{go.await();try{return registrations.confirmPayment(r,cash(),9L);}catch(CustomException e){return false;}}));go.countDown();int successes=0;for(Future<Boolean> f:futures)if(f.get(30,TimeUnit.SECONDS))successes++;assertEquals(1,successes);}finally{pool.shutdownNow();}
        assertEquals(1,context.getBean(QlMiniAppMapper.class).selectOccupiedRegistrations(s).size());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE session_id=? AND status='paid'",Integer.class,s));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log l JOIN ql_registration r ON r.id=l.registration_id WHERE r.session_id=?",Integer.class,s));
    }
    @Test void legacyGroupReceiptsAreAtomicUnderCapacityContention() throws Exception {
        String s=openSession(2),buyer=customer(),a=legacyConfirmed(buyer,s),b=legacyConfirmed(customer(),s),single=legacyConfirmed(customer(),s),batch=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_registration_batch(id,order_no,session_id,submitted_by_customer_id,source,participant_count,submitted_at,quoted_amount,payable_amount,payment_status) VALUES(?,?,?,?,'mini_program',2,NOW(),200,0,'unpaid')",batch,"T"+batch.replace("-","").substring(0,20),s,buyer);
        db.update("UPDATE ql_registration SET batch_id=? WHERE id IN (?,?)",batch,a,b);
        QlPaymentConfirmationBo group=cash();group.setAmount(new BigDecimal("200"));
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {Future<Boolean> one=pool.submit(()->{go.await();try{return registrations.confirmPayment(a,group,9L);}catch(CustomException e){return false;}}),two=pool.submit(()->{go.await();try{return registrations.confirmPayment(single,cash(),9L);}catch(CustomException e){return false;}});go.countDown();assertEquals(1,(one.get(30,TimeUnit.SECONDS)?1:0)+(two.get(30,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}
        int occupied=context.getBean(QlMiniAppMapper.class).selectOccupiedRegistrations(s).size();assertTrue(occupied==1||occupied==2);
        int groupPaid=db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE batch_id=? AND payment_status='paid'",Integer.class,batch);assertTrue(groupPaid==0||groupPaid==2);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_transaction WHERE session_id=? AND status='paid'",Integer.class,s));
    }

    @Test @SuppressWarnings("unchecked") void cancelledOrderRemainsImmutableAndReapplyCreatesFreshPendingOrder() {
        String owner=customer(),s=openSession(5);bind("cancel-owner",owner,52001L);
        QlMiniAppRegistrationBo original=signup(s,person(true,null),person(false,null));
        Map<String,Object> first=mini.register("cancel-owner",original);
        String oldBatch=String.valueOf(first.get("id"));
        List<Map<String,Object>> oldRows=(List<Map<String,Object>>)first.get("registrations");
        String child=String.valueOf(oldRows.get(1).get("customerId"));
        assertTrue(registrations.cancelBatch(oldBatch,"Synthetic operator cancellation",9L));
        assertEquals("open",mini.sessionDetail(s).get("status"));
        assertEquals(0,context.getBean(QlMiniAppMapper.class).countRegistration(owner,s));
        assertEquals(first.get("id"),mini.register("cancel-owner",original).get("id"),"Old request remains idempotent");
        Map<String,Object> next=mini.register("cancel-owner",signup(s,person(true,null),person(false,child)));
        assertNotEquals(first.get("id"),next.get("id"));assertEquals("pending",next.get("registrationStatus"));assertEquals("unpaid",next.get("paymentStatus"));
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,s));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE batch_id=? AND registration_status='cancelled' AND payment_status='unpaid'",Integer.class,oldBatch));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration_status_log WHERE registration_id IN (SELECT id FROM ql_registration WHERE batch_id=?) AND to_status='cancelled'",Integer.class,oldBatch));
        assertEquals(new BigDecimal("200.00"),db.queryForObject("SELECT quoted_amount FROM ql_registration_batch WHERE id=?",BigDecimal.class,oldBatch));
        assertEquals(0,context.getBean(QlMiniAppMapper.class).selectOccupiedRegistrations(s).size());
        assertEquals(4,context.getBean(QlMiniAppMapper.class).selectCustomerRegistrations(owner).stream().filter(r->s.equals(r.get("sessionId"))).count());
        assertThrows(CustomException.class,()->mini.register("cancel-owner",signup(s,person(true,null))));
    }
    @Test @SuppressWarnings("unchecked") void partialHistoricalCancellationReappliesOnlyThatParticipant() {
        String owner=customer(),s=openSession(5);bind("partial-owner",owner,52002L);
        Map<String,Object> first=mini.register("partial-owner",signup(s,person(true,null),person(false,null)));
        List<Map<String,Object>> rows=(List<Map<String,Object>>)first.get("registrations");
        String child=String.valueOf(rows.get(1).get("customerId")),old=String.valueOf(rows.get(1).get("registrationId"));
        // Existing partial history fixture; no new partial-cancellation or payment workflow is introduced.
        db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",old);
        assertEquals(1,context.getBean(QlMiniAppMapper.class).countRegistration(owner,s));
        assertEquals(0,context.getBean(QlMiniAppMapper.class).countRegistration(child,s));
        assertThrows(CustomException.class,()->mini.register("partial-owner",signup(s,person(true,null),person(false,child))));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,s),"Failed group rolls back all rows");
        mini.register("partial-owner",signup(s,person(false,child)));
        assertEquals("pending",registrations.getById(String.valueOf(rows.get(0).get("registrationId"))).getRegistrationStatus());
        assertEquals("cancelled",registrations.getById(old).getRegistrationStatus());
        assertEquals(1,context.getBean(QlMiniAppMapper.class).countRegistration(child,s));
    }
    @Test void closedCancelledDeadlineAndPaidFullSessionStillRejectReapplication() {
        for(String mode:Arrays.asList("closed","cancelled","deadline","full")) {
            String owner=customer(),s=openSession(1),old=legacyConfirmed(owner,s);bind("gate-"+mode,owner,52010L+mode.length());
            db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",old);
            if(mode.equals("deadline"))db.update("UPDATE ql_session SET registration_close_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",s);
            else if(mode.equals("full")) {String occupied=legacyConfirmed(customer(),s);db.update("UPDATE ql_registration SET payment_status='paid' WHERE id=?",occupied);}
            else db.update("UPDATE ql_session SET status=? WHERE id=?",mode,s);
            assertThrows(CustomException.class,()->mini.register("gate-"+mode,signup(s,person(true,null))),mode);
            assertEquals(0,context.getBean(QlMiniAppMapper.class).countRegistration(owner,s));
        }
    }
    @Test void concurrentReapplicationsAndDatabaseConstraintKeepExactlyOneActiveRecord() throws Exception {
        String owner=customer(),s=openSession(5),old=legacyConfirmed(owner,s);bind("race-owner",owner,52030L);
        db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",old);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(int i=0;i<2;i++) jobs.add(pool.submit(()->{go.await();try{mini.register("race-owner",signup(s,person(true,null)));return true;}catch(CustomException e){return false;}}));
            go.countDown();int success=0;for(Future<Boolean> job:jobs)if(job.get(30,TimeUnit.SECONDS))success++;assertEquals(1,success);
        }finally{pool.shutdownNow();}
        assertEquals(1,context.getBean(QlMiniAppMapper.class).countRegistration(owner,s));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE customer_id=? AND session_id=?",Integer.class,owner,s));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->legacyConfirmed(owner,s));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->db.update("UPDATE ql_registration SET registration_status='pending' WHERE id=?",old));
        assertEquals("cancelled",registrations.getById(old).getRegistrationStatus());
    }
    @Test @SuppressWarnings("unchecked") void oldReflectionLinksCannotBorrowNewRegistrationAndPrivateHistoryIsKept() {
        String owner=customer(),s=openSession(5),old=legacyConfirmed(owner,s);bind("reflection-history",owner,52040L);
        db.update("UPDATE ql_session SET start_date=CURRENT_DATE() WHERE id=?",s);
        String reflection=UUID.randomUUID().toString();
        db.update("INSERT INTO ql_camp_reflection(id,registration_id,customer_id,session_id,phase,draft_note,draft_anonymous,created_at,updated_at) VALUES(?,?,?,?,'before','Synthetic private historic draft',1,NOW(),NOW())",reflection,old,owner,s);
        db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",old);
        QlCampReflectionService service=context.getBean(QlCampReflectionService.class);
        assertEquals(false,service.mine("reflection-history",s).get("canBefore"));
        String fresh=String.valueOf(((List<Map<String,Object>>)mini.register("reflection-history",signup(s,person(true,null))).get("registrations")).get(0).get("registrationId"));
        assertEquals(fresh,service.mine("reflection-history",s).get("registrationId"));assertEquals(false,service.mine("reflection-history",s).get("canBefore"));
        // Confirmed fixture tests the old-link boundary only, never a simulated payment event.
        db.update("UPDATE ql_registration SET registration_status='confirmed' WHERE id=?",fresh);
        assertEquals(true,service.mine("reflection-history",s).get("canBefore"));
        assertTrue(((List<?>)service.mine("reflection-history",s).get("records")).isEmpty());
        QlCampReflectionBo b=new QlCampReflectionBo();b.setRegistrationId(old);b.setPhase("before");b.setNote("Should be denied");b.setAction("private");b.setRevision(0);b.setAnonymous(true);
        assertEquals(403,assertThrows(CustomException.class,()->service.save("reflection-history",b)).getCode());
        QlReflectionVoiceBo voice=new QlReflectionVoiceBo();voice.setRegistrationId(old);voice.setPhase("before");voice.setVoiceConsent(true);
        assertEquals(403,assertThrows(CustomException.class,()->service.transcribe("reflection-history",voice)).getCode());
        b.setRegistrationId(fresh);service.save("reflection-history",b);
        service.withdraw("reflection-history",s,"before");
        assertEquals("Synthetic private historic draft",db.queryForObject("SELECT draft_note FROM ql_camp_reflection WHERE id=?",String.class,reflection));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_camp_reflection WHERE customer_id=? AND session_id=?",Integer.class,owner,s));
        db.update("UPDATE ql_session SET start_date=DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY) WHERE id=?",s);
        assertEquals(false,service.mine("reflection-history",s).get("canBefore"));
    }


    @Test void reapplicationNeverBorrowsCancelledOrAmbiguousLegacyAttendance() {
        String owner=customer(),s=openSession(5),old=legacyConfirmed(owner,s);
        attendance(owner,s,old,"completed");db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",old);
        String fresh=legacyConfirmed(owner,s);
        db.update("UPDATE ql_session SET status='completed',start_date='2000-01-01',end_date='2000-01-03' WHERE id=?",s);
        QlMiniAppMapper mapper=context.getBean(QlMiniAppMapper.class);
        assertEquals(0,mapper.countCompletedSessions(owner));assertEquals(0,mapper.countAttendedSession(owner,s));
        db.update("UPDATE ql_participation SET registration_id=NULL WHERE customer_id=? AND session_id=?",owner,s);
        assertEquals(0,mapper.countCompletedSessions(owner),"Ambiguous unlinked history cannot establish attendance");
        db.update("UPDATE ql_participation SET registration_id=? WHERE customer_id=? AND session_id=?",fresh,owner,s);
        assertEquals(1,mapper.countCompletedSessions(owner));assertEquals(1,mapper.countAttendedSession(owner,s));
        assertEquals(new BigDecimal("50"),context.getBean(QlSessionPricing.class).price(owner,new BigDecimal("100"),new BigDecimal("50")));
    }

    @Test void receiptFailureRollsBackSeatSettlementAndAuditThenRetryWorks() {
        String s=openSession(1),r=legacyConfirmed(customer(),s);
        db.execute("CREATE TRIGGER ql_task_fail_receipt BEFORE INSERT ON ql_transaction FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic receipt failure'");
        try {assertThrows(RuntimeException.class,()->registrations.confirmPayment(r,cash(),9L));}finally{db.execute("DROP TRIGGER ql_task_fail_receipt");}
        assertEquals("unpaid",registrations.getById(r).getPaymentStatus());assertEquals("pending",registrations.getById(r).getSettlementStatus());assertEquals(0,context.getBean(QlMiniAppMapper.class).selectOccupiedRegistrations(s).size());
        assertTrue(registrations.confirmPayment(r,cash(),9L));assertEquals(1,context.getBean(QlMiniAppMapper.class).selectOccupiedRegistrations(s).size());
    }
}
