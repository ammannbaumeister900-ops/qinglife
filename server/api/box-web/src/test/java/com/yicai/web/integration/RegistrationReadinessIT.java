package com.yicai.web.integration;

import com.yicai.web.tools.DatabaseMigration;
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
    static int number=5000;
    static final Map<String,Object> access=new HashMap<>();
    @Configuration @EnableTransactionManagement
    @Import({QlRegistrationPolicy.class,QlRegistrationServiceImpl.class,QlSessionPricing.class,QlMiniAppServiceImpl.class,QlStaffWorkspaceService.class,QlAttendanceAudit.class})
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
            config.addMapper(QlRegistrationMapper.class);config.addMapper(QlSessionMapper.class);config.addMapper(QlMiniAppMapper.class);
            config.addMapper(QlRegistrationStatusLogMapper.class);config.addMapper(QlTransactionMapper.class);
            MybatisSqlSessionFactoryBean f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(config);
            f.setTypeAliasesPackage("com.yicai.life.domain");
            f.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/life/*.xml"));return f.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean QlRegistrationMapper registrations(SqlSessionTemplate s){return s.getMapper(QlRegistrationMapper.class);}
        @Bean QlSessionMapper sessions(SqlSessionTemplate s){return s.getMapper(QlSessionMapper.class);}
        @Bean QlMiniAppMapper mini(SqlSessionTemplate s){return s.getMapper(QlMiniAppMapper.class);}
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
        registrations=context.getBean(IQlRegistrationService.class);staff=context.getBean(QlStaffWorkspaceService.class);mini=context.getBean(IQlMiniAppService.class);
        access.put("sys_user_id",9L);access.put("can_operate",1);access.put("can_payment",1);
    }
    @AfterAll static void close(){if(context!=null)context.close();}
    String customer(){String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_customer(id,customer_no,nickname) VALUES(?,?,?)",id,"T"+id.replace("-","").substring(0,20),"Synthetic");return id;}
    String session(int capacity){String id=UUID.randomUUID().toString();db.update("INSERT INTO ql_session(id,session_number,name,start_date,end_date,capacity,status,standard_price,returning_price) VALUES(?,?,?,CURRENT_DATE(),DATE_ADD(CURRENT_DATE(),INTERVAL 2 DAY),?,'open',100,50)",id,++number,"Synthetic",capacity);return id;}
    String add(String customer,String session,String status){QlRegistrationBo b=new QlRegistrationBo();b.setCustomerId(customer);b.setSessionId(session);b.setRegistrationStatus(status);registrations.insertByBo(b,9L);return db.queryForObject("SELECT id FROM ql_registration WHERE customer_id=? AND session_id=?",String.class,customer,session);}
    void status(String id,String status){QlRegistrationBo b=new QlRegistrationBo();b.setId(id);b.setRegistrationStatus(status);registrations.updateByBo(b,9L);}
    QlPaymentBo payment(String state,String amount){QlPaymentBo b=new QlPaymentBo();b.setPaymentStatus(state);b.setAmount(new BigDecimal(amount));b.setPaymentMethod("cash");b.setChangeReason("Synthetic correction");return b;}
    @Test void migratedSchemaCanBeReplayedAndTamperingFails()throws Exception {
        try(Connection c=context.getBean(DataSource.class).getConnection()) {
            Path server=Paths.get(System.getProperty("qinglife.migrations")).getParent();DatabaseMigration.run(c,server);
            assertEquals(DatabaseMigration.files(server).size(),db.queryForObject("SELECT COUNT(*) FROM ql_migration_run WHERE state='complete'",Integer.class));
            String name="V1_3_11__wechat_login_identity.sql";String hash=db.queryForObject("SELECT sha256 FROM ql_migration_run WHERE name=?",String.class,name);
            db.update("UPDATE ql_migration_run SET sha256=? WHERE name=?",String.join("",Collections.nCopies(64,"0")),name);
            try{assertThrows(IllegalStateException.class,()->DatabaseMigration.run(c,server));}finally{db.update("UPDATE ql_migration_run SET sha256=? WHERE name=?",hash,name);}
        }
    }
    @Test void pendingSeatCanConfirmWhenFullButWaitlistCannot(){String s=session(1),r=add(customer(),s,"pending"),w=add(customer(),s,"waitlisted");staff.confirm(r,access);assertThrows(CustomException.class,()->status(w,"confirmed"));assertThrows(CustomException.class,()->add(customer(),s,"pending"));}
    @Test void paymentAndCancellationShareRules(){String s=session(2),r=add(customer(),s,"confirmed");assertThrows(CustomException.class,()->registrations.changePayment(r,payment("paid","99"),9L));registrations.changePayment(r,payment("paid","100"),9L);assertThrows(CustomException.class,()->staff.cancel(r,access));registrations.changePayment(r,payment("unpaid","0"),9L);staff.cancel(r,access);assertThrows(CustomException.class,()->status(r,"confirmed"));}
    @Test void freeSessionAndMethodValidationAreShared(){String s=session(1);db.update("UPDATE ql_session SET standard_price=0 WHERE id=?",s);String r=add(customer(),s,"confirmed");QlPaymentBo b=payment("paid","0");b.setPaymentMethod("unsupported");assertThrows(CustomException.class,()->registrations.changePayment(r,b,9L));b.setPaymentMethod("cash");staff.payment(r,b,access);assertEquals("paid",registrations.getById(r).getPaymentStatus());}
    @Test void threeEntrypointsCompeteForLastSeat()throws Exception {
        String s=session(1),buyer=customer(),adminCustomer=customer(),staffCustomer=customer();
        when(context.getBean(RedisCache.class).getCacheObject("appToken:synthetic")).thenReturn(77L);
        when(context.getBean(QlCustomerIdentityService.class).resolve(77L)).thenReturn(buyer);
        QlMiniAppRegistrationBo b=new QlMiniAppRegistrationBo();b.setSessionId(s);b.setClientRequestId(UUID.randomUUID().toString());b.setServiceConsent(true);
        QlMiniAppRegistrationBo.Participant p=new QlMiniAppRegistrationBo.Participant();p.setSelf(true);p.setName("Synthetic");p.setMinor(false);p.setPhone("13800000000");b.setParticipants(Collections.singletonList(p));
        List<Callable<Boolean>> actions=Arrays.asList(()->{add(adminCustomer,s,"pending");return true;},()->{staff.enroll(staffCustomer,s,access);return true;},()->{mini.register("synthetic",b);return true;});
        ExecutorService pool=Executors.newFixedThreadPool(3);CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
        try{for(Callable<Boolean> action:actions)futures.add(pool.submit(()->{start.await();try{return action.call();}catch(CustomException expected){return false;}}));start.countDown();int success=0;for(Future<Boolean> f:futures)if(f.get(30,TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE session_id=?",Integer.class,s));}finally{pool.shutdownNow();}
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
        String s=session(1),r=add(customer(),s,"confirmed");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
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
}
