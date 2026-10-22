package com.yicai.web.integration;

import com.yicai.BoxApplication;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.service.QlCustomerIdentityService;
import com.yicai.web.tools.DatabaseMigration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/security/services/MySQL/Redis, no Mockito or fake payment-success event.
 * Synthetic app sessions are seeded locally; real WeChat code exchange is outside this test. */
@SpringBootTest(classes=BoxApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"logging.config=classpath:logback-http-test.xml","spring.main.banner-mode=off",
 "qinglife.wechat.enabled=false","token.secret=synthetic-pricing-http-secret-0123456789abcdef",
 "spring.boot.admin.client.enabled=false","server.servlet.context-path=","ruoyi.imagePath=https://example.invalid/images/"})
@ActiveProfiles("dev")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RegistrationPricingHttpIT {
    @Autowired TestRestTemplate http;
    @org.springframework.boot.web.server.LocalServerPort int httpPort;
    @org.springframework.beans.factory.annotation.Value("${server.servlet.context-path:}") String contextPath;
    @Autowired JdbcTemplate db;
    @Autowired RedisCache cache;
    @Autowired QlCustomerIdentityService identities;
    static Path uploads;
    static int number=930000;
    static final java.util.concurrent.atomic.AtomicInteger phoneNumber=new java.util.concurrent.atomic.AtomicInteger(10000000);
    String admin;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception {
        String base=System.getenv("QINGLIFE_HTTP_MYSQL_URL"),port=System.getenv("QINGLIFE_TEST_REDIS_PORT");
        if(base==null||!base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_http_[a-z0-9_]+\\?.*"))
            throw new IllegalStateException("Fresh loopback HTTP database required");
        if(port==null||!port.matches("[0-9]+"))throw new IllegalStateException("Fresh isolated Redis required");
        // Derived HTTP suites inherit the same methods. Each class needs its own
        // migrated facts, otherwise a prior class changes friend history/phone ownership.
        String schema="qinglife_it_http_class_"+UUID.randomUUID().toString().replace("-", "").substring(0,20);
        String url=base.replaceFirst("/qinglife_it_http_[a-z0-9_]+", "/"+schema);
        try(Connection c=DriverManager.getConnection(base,"root","");Statement statement=c.createStatement()){statement.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");}
        try(Connection c=DriverManager.getConnection(url,"root","")){
            DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());
            System.out.println("PRICING_HTTP_ISOLATED_DATABASE="+c.getCatalog());
        }
        uploads=Files.createTempDirectory("qinglife-pricing-http-");
        p.add("spring.datasource.dynamic.datasource.master.url",()->url);
        p.add("spring.datasource.dynamic.datasource.master.username",()->"root");
        p.add("spring.datasource.dynamic.datasource.master.password",()->"");
        p.add("spring.redis.host",()->"127.0.0.1");p.add("spring.redis.port",()->port);
        p.add("spring.redis.password",()->"");p.add("spring.redis.database",()->0);
        p.add("ruoyi.profile",()->uploads.toString());
    }
    @AfterAll static void cleanup()throws Exception {if(uploads!=null)Files.deleteIfExists(uploads);}
    static String id(){return UUID.randomUUID().toString();}
    static Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i].toString(),pairs[i+1]);return m;}
    Map<String,Object> call(String route,HttpMethod method,Object body,String token,boolean backend){
        HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);
        if(token!=null)h.set(backend?"Authorization":"token",backend?"Bearer "+token:token);
        return http.exchange(route,method,new HttpEntity<>(body,h),Map.class).getBody();
    }
    Map<String,Object> post(String route,Object body,String token){return call("/app/qinglife"+route,HttpMethod.POST,body,token,false);}
    Map<String,Object> get(String route,String token){return call("/app/qinglife"+route,HttpMethod.GET,null,token,false);}
    Map<String,Object> ok(Map<String,Object> response){assertNotNull(response);assertEquals(200,((Number)response.get("code")).intValue(),response.toString());return (Map<String,Object>)response.get("data");}
    String[] account(){
        String open="price-"+id(),token=id();
        db.update("INSERT INTO app_user_info(open_id,nick_name,status) VALUES(?, 'Synthetic self',1)",open);
        Long app=db.queryForObject("SELECT id FROM app_user_info WHERE open_id=?",Long.class,open);
        String customer=identities.resolve(app);cache.setCacheObject("appToken:"+token,app,30,TimeUnit.MINUTES);
        return new String[]{token,customer};
    }
    String admin(){
        if(admin!=null)return admin;
        String password=id();
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,password,status,del_flag) VALUES(1,0,'synthetic-http-admin','Synthetic admin','00',?,'0','0') ON DUPLICATE KEY UPDATE password=VALUES(password)",SecurityUtils.encryptPassword(password));
        String uuid=id();cache.setCacheObject("captcha_codes:"+uuid,"synthetic",2,TimeUnit.MINUTES);
        Map<String,Object> login=http.postForObject("/login",map("username","synthetic-http-admin","password",password,"code","synthetic","uuid",uuid),Map.class);
        assertEquals(200,((Number)login.get("code")).intValue(),login.toString());admin=String.valueOf(((Map)login.get("data")).get("token"));return admin;
    }
    String session(){
        String s=id();
        db.update("INSERT INTO ql_session(id,session_number,name,start_date,end_date,status,capacity,standard_price,returning_price,registration_open_at,registration_close_at) VALUES(?,?,'Synthetic pricing camp',DATE_ADD(CURRENT_DATE(),INTERVAL 2 DAY),DATE_ADD(CURRENT_DATE(),INTERVAL 4 DAY),'open',50,3800,2500,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY))",s,++number);return s;
    }
    Map<String,Object> self(String name){return map("self",true,"name",name,"relation","本人","phone","139"+phoneNumber.incrementAndGet(),"minor",false);}
    Map<String,Object> child(String name){return map("self",false,"name",name,"relation","子女","phone","","minor",true);}
    Map<String,Object> signup(String s,Map<String,Object>... people){return map("sessionId",s,"clientRequestId",id(),"contactName","Synthetic contact","contactPhone","13800000001","serviceConsent",true,"motivation","Latest motivation","participants",new ArrayList<>(Arrays.asList(people)));}
    Map<String,Object> quote(Map<String,Object> bo,String token){return ok(post("/registrations/quote",map("sessionId",bo.get("sessionId"),"participants",bo.get("participants")),token));}
    void amount(Object expected,Object actual){assertEquals(0,new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())));}
    List<Map<String,Object>> rows(String batch){return db.queryForList("SELECT * FROM ql_registration WHERE batch_id=? ORDER BY participant_name_snapshot",batch);}
    void cancel(String batch){
        Map<String,Object> response=call("/life/registration/batch/"+batch+"/cancel",HttpMethod.PUT,map("reason","Synthetic operational cancellation"),admin(),true);
        assertEquals(200,((Number)response.get("code")).intValue(),response.toString());
    }
    void completed(String customer){
        String s=session(),r=id();db.update("UPDATE ql_session SET status='completed',start_date='2000-01-01',end_date='2000-01-03' WHERE id=?",s);
        // Explicit historical participation fixture, not a claim of provider payment completion.
        db.update("INSERT INTO ql_registration(id,customer_id,session_id,registration_status,payment_status,registration_source,registered_at) VALUES(?,?,?,'confirmed','unpaid','web_admin',NOW())",r,customer,s);
        db.update("INSERT INTO ql_participation(id,registration_id,customer_id,session_id,attendance_status) VALUES(?,?,?,?,'completed')",id(),r,customer,s);
    }
    @Test void firstSignupQuoteAndBackendReadbackAgree(){
        String[] a=account();String s=session();Map<String,Object> person=self("Submitted self");person.put("phone","13800000001");Map<String,Object> bo=signup(s,person);Map<String,Object> q=quote(bo,a[0]);amount(3800,q.get("quotedAmount"));
        assertEquals("new",((List<Map>)q.get("participants")).get(0).get("friendType"));
        Map<String,Object> saved=ok(post("/registrations",bo,a[0]));amount(q.get("quotedAmount"),saved.get("quotedAmount"));assertEquals("pending",saved.get("registrationStatus"));
        Map<String,Object> row=rows(String.valueOf(saved.get("id"))).get(0);assertEquals("unpaid",row.get("payment_status"));assertEquals("Submitted self",row.get("participant_name_snapshot"));
        Map<String,Object> backend=ok(call("/life/registration/"+row.get("id"),HttpMethod.GET,null,admin(),true));
        amount(3800,backend.get("standardPrice"));assertEquals("Submitted self",backend.get("nickname"));assertEquals("13800000001",backend.get("participantPhone"));
    }
    @Test void quoteUsesEachParticipantsHistoryAndReflectsChanges(){
        String[] a=account();completed(a[1]);String s=session();Map<String,Object> bo=signup(s,self("Returning self"),child("New child"));
        Map<String,Object> q=quote(bo,a[0]);amount(6300,q.get("quotedAmount"));List<Map> prices=(List<Map>)q.get("participants");assertEquals("returning",prices.get(0).get("friendType"));assertEquals("new",prices.get(1).get("friendType"));
        ((List)bo.get("participants")).remove(1);amount(2500,quote(bo,a[0]).get("quotedAmount"));
        ((List)bo.get("participants")).add(child("New child"));Map<String,Object> saved=ok(post("/registrations",bo,a[0]));amount(6300,saved.get("quotedAmount"));
        for(Map<String,Object> row:rows(saved.get("id").toString()))amount(row.get("customer_id").equals(a[1])?2500:3800,row.get("unit_price"));
    }
    @Test void cancelAndReapplyCreateFreshPendingRowsWithLatestDetails(){
        String[] a=account();String s=session();Map<String,Object> first=signup(s,self("Original name"),child("Original child"));Map<String,Object> old=ok(post("/registrations",first,a[0]));
        String oldBatch=old.get("id").toString();cancel(oldBatch);List<Map<String,Object>> history=rows(oldBatch);
        Map<String,Object> oldChild=history.stream().filter(r->!r.get("customer_id").equals(a[1])).findFirst().get();
        db.update("UPDATE ql_session SET standard_price=3900 WHERE id=?",s);
        Map<String,Object> p=child("Latest child");p.put("customerId",oldChild.get("customer_id"));p.put("relation","历史关系文本");p.put("phone","13900000007");
        Map<String,Object> newer=signup(s,self("Latest self"),p);newer.put("contactName","Latest contact");newer.put("contactPhone","13900000008");newer.put("motivation","New motivation");
        amount(7800,quote(newer,a[0]).get("quotedAmount"));Map<String,Object> saved=ok(post("/registrations",newer,a[0]));assertNotEquals(oldBatch,saved.get("id"));amount(7800,saved.get("quotedAmount"));
        assertEquals(history,rows(oldBatch));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration_status_log WHERE registration_id IN (SELECT id FROM ql_registration WHERE batch_id=?) AND to_status='cancelled'",Integer.class,oldBatch));
        for(Map<String,Object> r:rows(saved.get("id").toString())){assertEquals("pending",r.get("registration_status"));assertEquals("unpaid",r.get("payment_status"));assertNull(r.get("confirmed_at"));assertNull(r.get("current_settlement_id"));assertEquals("New motivation",r.get("motivation"));amount(3900,r.get("unit_price"));}
        Map<String,Object> childRow=rows(saved.get("id").toString()).stream().filter(r->!r.get("customer_id").equals(a[1])).findFirst().get();assertEquals("Latest child",childRow.get("participant_name_snapshot"));assertEquals("13900000007",childRow.get("participant_phone_snapshot"));assertEquals("历史关系文本",childRow.get("relation_snapshot"));
        Map<String,Object> view=ok(get("/me/overview",a[0]));assertEquals(4,((List)view.get("registrations")).size());
        amount(0,((List<Map>)okListSessions()).stream().filter(r->s.equals(r.get("id"))).findFirst().get().get("registeredCount"));
    }
    List okListSessions(){Map response=get("/sessions",null);assertEquals(200,((Number)response.get("code")).intValue());return (List)response.get("data");}
    @Test void activeDuplicatesAndDifferentOwnerAreRejectedAtomically(){
        String[] a=account(),b=account();String s=session();Map<String,Object> saved=ok(post("/registrations",signup(s,self("Self"),child("Child")),a[0]));
        Map<String,Object> oldChild=rows(saved.get("id").toString()).stream().filter(r->!r.get("customer_id").equals(a[1])).findFirst().get();
        assertEquals(400,((Number)post("/registrations",signup(s,self("Duplicate")),a[0]).get("code")).intValue());
        Map<String,Object> foreign=child("Foreign");foreign.put("customerId",oldChild.get("customer_id"));
        assertEquals(403,((Number)post("/registrations/quote",map("sessionId",s,"participants",Arrays.asList(foreign)),b[0]).get("code")).intValue());
        assertEquals(403,((Number)post("/registrations",signup(s,foreign),b[0]).get("code")).intValue());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration_batch WHERE session_id=?",Integer.class,s));
    }
    @Test void partialCancellationOnlyReappliesThatParticipant(){
        String[] a=account();String s=session();Map saved=ok(post("/registrations",signup(s,self("Valid self"),child("Cancelled child")),a[0]));String batch=saved.get("id").toString();
        Map<String,Object> oldChild=rows(batch).stream().filter(r->!r.get("customer_id").equals(a[1])).findFirst().get();
        // Synthetic legacy partial cancellation; current admin API remains whole-order cancellation.
        db.update("UPDATE ql_registration SET registration_status='cancelled' WHERE id=?",oldChild.get("id"));
        Map<String,Object> p=child("Child reapplied");p.put("customerId",oldChild.get("customer_id"));Map bo=signup(s,p);amount(3800,quote(bo,a[0]).get("quotedAmount"));ok(post("/registrations",bo,a[0]));
        assertEquals("pending",db.queryForObject("SELECT registration_status FROM ql_registration WHERE batch_id=? AND customer_id=?",String.class,batch,a[1]));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE customer_id=? AND session_id=? AND active_registration_slot=1",Integer.class,a[1],s));
        Map invalid=signup(s,self("Still active"),p);assertNotEquals(200,post("/registrations",invalid,a[0]).get("code"));
    }
    @Test void financialConflictsRejectWithoutRewritingFacts(){
        for(String status:Arrays.asList("paid","pending_confirmation","refund_pending")){
            String[] a=account();String s=session();Map old=ok(post("/registrations",signup(s,self("Old")),a[0]));String batch=old.get("id").toString();cancel(batch);
            String tx=id();db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source) VALUES(?,?,?,?,'session',3800,'cash',?,NOW(),'legacy_excel')",tx,a[1],s,batch,status);
            Map<String,Object> fact=db.queryForMap("SELECT * FROM ql_transaction WHERE id=?",tx);
            assertEquals(409,((Number)post("/registrations",signup(s,self("New")),a[0]).get("code")).intValue());assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE id=?",tx));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration_batch WHERE session_id=?",Integer.class,s));
        }
    }
    @Test void refundedHistoryAndCancellationRemainTraceable(){
        String[] a=account();String s=session();Map old=ok(post("/registrations",signup(s,self("Old")),a[0]));String batch=old.get("id").toString();cancel(batch);
        String tx=id();db.update("INSERT INTO ql_transaction(id,customer_id,session_id,registration_batch_id,transaction_type,amount,payment_method,status,transaction_at,record_source) VALUES(?,?,?,?,'session',3800,'cash','refunded',NOW(),'legacy_excel')",tx,a[1],s,batch);
        db.update("UPDATE ql_registration_batch SET payment_status='refunded' WHERE id=?",batch);db.update("UPDATE ql_registration SET payment_status='refunded' WHERE batch_id=?",batch);
        Map fact=db.queryForMap("SELECT * FROM ql_transaction WHERE id=?",tx);ok(post("/registrations",signup(s,self("New")),a[0]));assertEquals(fact,db.queryForMap("SELECT * FROM ql_transaction WHERE id=?",tx));assertEquals("refunded",rows(batch).get(0).get("payment_status"));
    }
    @Test void unresolvedPassConsumptionRejectsReapplication(){
        String[] a=account();String s=session();Map old=ok(post("/registrations",signup(s,self("Old")),a[0]));String batch=old.get("id").toString();cancel(batch);
        String pass=id(),ledger=id();db.update("INSERT INTO ql_pass_account(id,customer_id,pass_type,status) VALUES(?,?,'session_card','active')",pass,a[1]);
        db.update("INSERT INTO ql_pass_ledger(id,pass_account_id,session_id,registration_batch_id,entry_type,quantity_delta,reason,occurred_at) VALUES(?,?,?,?,'consume',-1,'Synthetic unresolved legacy debit',NOW())",ledger,pass,s,batch);
        Map fact=db.queryForMap("SELECT * FROM ql_pass_ledger WHERE id=?",ledger);assertEquals(409,((Number)post("/registrations",signup(s,self("New")),a[0]).get("code")).intValue());assertEquals(fact,db.queryForMap("SELECT * FROM ql_pass_ledger WHERE id=?",ledger));
    }
    @Test void concurrentReapplicationAndIdempotencyDoNotDuplicate(){
        String[] a=account();String s=session();Map old=ok(post("/registrations",signup(s,self("Old")),a[0]));cancel(old.get("id").toString());ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Map>> calls=new ArrayList<>();for(int i=0;i<2;i++){Map bo=signup(s,self("Concurrent"));calls.add(pool.submit(()->{start.await();return post("/registrations",bo,a[0]);}));}
            start.countDown();int success=0;for(Future<Map> f:calls)try{if(((Number)f.get(20,TimeUnit.SECONDS).get("code")).intValue()==200)success++;}catch(Exception e){throw new AssertionError(e);}
            assertEquals(1,success);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_registration WHERE customer_id=? AND session_id=? AND active_registration_slot=1",Integer.class,a[1],s));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_registration_batch WHERE session_id=?",Integer.class,s));
        } finally {pool.shutdownNow();}
        String other=session();Map bo=signup(other,self("Retry"));Map first=ok(post("/registrations",bo,a[0]));assertEquals(first.get("id"),ok(post("/registrations",bo,a[0])).get("id"));cancel(first.get("id").toString());assertEquals(first.get("id"),ok(post("/registrations",bo,a[0])).get("id"));
    }
    @Test void inconsistentLegacyFinancialStatesFailClosedAndReversedAuditAllowsFreshOrder(){
        for(String mode:Arrays.asList("registration-payment","batch-payment","unreversed-settlement","orphan-receipt","reversed-settlement")){
            String[] a=account();String s=session();Map old=ok(post("/registrations",signup(s,self("Old finance state")),a[0]));String batch=old.get("id").toString();cancel(batch);
            if(mode.equals("registration-payment"))db.update("UPDATE ql_registration SET payment_status='paid' WHERE batch_id=?",batch);
            if(mode.equals("batch-payment"))db.update("UPDATE ql_registration_batch SET payment_status='pending_confirmation' WHERE id=?",batch);
            if(mode.endsWith("settlement")){
                String settlement=id();db.update("INSERT INTO ql_settlement_log(id,registration_batch_id,quoted_amount,final_amount,settlement_type,operator_id,confirmed_at) VALUES(?,?,3800,3800,'money',1,NOW())",settlement,batch);
                if(mode.equals("reversed-settlement"))db.update("INSERT INTO ql_settlement_reversal(settlement_id,registration_batch_id,original_final_amount,original_settlement_type,reason,operator_id,reversed_at) VALUES(?,?,3800,'money','Synthetic historical reversal',1,NOW())",settlement,batch);
            }
            if(mode.equals("orphan-receipt"))db.update("INSERT INTO ql_transaction(id,customer_id,session_id,transaction_type,amount,payment_method,status,transaction_at,record_source) VALUES(?,?,?,'session',3800,'cash','paid',NOW(),'legacy_excel')",id(),a[1],s);
            List<Map<String,Object>> history=rows(batch),receipts=db.queryForList("SELECT * FROM ql_transaction WHERE session_id=?",s),settlements=db.queryForList("SELECT * FROM ql_settlement_log WHERE registration_batch_id=?",batch);
            Map response=post("/registrations",signup(s,self("Fresh finance state")),a[0]);
            assertEquals(mode.equals("reversed-settlement")?200:409,((Number)response.get("code")).intValue(),mode);
            assertEquals(history,rows(batch));assertEquals(receipts,db.queryForList("SELECT * FROM ql_transaction WHERE session_id=?",s));assertEquals(settlements,db.queryForList("SELECT * FROM ql_settlement_log WHERE registration_batch_id=?",batch));
        }
    }
    @Test void actualMiniappHandlersQuoteAndReapplyAgainstRealHttp()throws Exception {
        String[] a=account();completed(a[1]);String s=session();Map old=ok(post("/registrations",signup(s,self("Cancelled before page test")),a[0]));cancel(old.get("id").toString());
        Path helper=Paths.get(System.getProperty("qinglife.migrations")).toAbsolutePath().normalize().getParent().getParent().resolve("apps/miniapp/tests/helpers/registration-real-http.cjs");
        Process process=new ProcessBuilder(System.getenv().getOrDefault("QINGLIFE_NODE","node"),helper.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String phone="139"+phoneNumber.incrementAndGet();
        Map<String,Object> input=map("base","http://127.0.0.1:"+httpPort+contextPath,"token",a[0],"session",s,"name","Latest via page","phone",phone,"total","6300.00");
        process.getOutputStream().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(input));process.getOutputStream().close();
        assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Miniapp real HTTP adapter timed out");
        String output=new String(readAll(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);assertEquals(0,process.exitValue(),output);
        Map proof=new com.fasterxml.jackson.databind.ObjectMapper().readValue(output,Map.class);assertEquals(true,proof.get("actualHttp"));assertEquals(false,proof.get("mockedBusinessResponses"));
        System.out.println("PRICING_REAL_MINIAPP_HTTP="+output);
        String registration=db.queryForObject("SELECT id FROM ql_registration WHERE customer_id=? AND session_id=? AND active_registration_slot=1",String.class,a[1],s);
        Map backend=ok(call("/life/registration/"+registration,HttpMethod.GET,null,admin(),true));assertEquals("Latest via page",backend.get("nickname"));assertEquals(phone,backend.get("participantPhone"));amount(2500,backend.get("standardPrice"));amount(6300,backend.get("quotedAmount"));
        assertEquals("cancelled",rows(old.get("id").toString()).get(0).get("registration_status"));
    }
    static byte[] readAll(java.io.InputStream input)throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int size;
        while((size=input.read(buffer))!=-1)out.write(buffer,0,size);return out.toByteArray();
    }
    @Test void quoteAuthenticationContractAndMissingHistoryFailClosed(){
        String[] a=account();String s=session();Map bo=signup(s,self("Self"));
        assertEquals(401,((Number)post("/registrations/quote",map("sessionId",s,"participants",bo.get("participants")),null).get("code")).intValue());
        assertNotEquals(200,((Number)post("/registrations/quote",map("sessionId",s,"participants",Collections.emptyList()),a[0]).get("code")).intValue());
        Map p=child("Unknown existing phone");p.put("phone","13900000009");
        db.update("INSERT INTO ql_customer(id,customer_no,nickname) VALUES('synthetic-foreign-phone','SYN-PHONE','Foreign')");
        db.update("INSERT INTO ql_customer_identifier(id,customer_id,identifier_type,identifier_value,normalized_hash,verification_status,valid_from) VALUES(?, 'synthetic-foreign-phone','phone','13900000009',?,'unverified',NOW())",id(),cn.hutool.crypto.digest.DigestUtil.sha256Hex("13900000009"));
        assertEquals(409,((Number)post("/registrations/quote",map("sessionId",s,"participants",Arrays.asList(p)),a[0]).get("code")).intValue());
        db.update("UPDATE ql_session SET status='cancelled' WHERE id=?",s);assertEquals(400,((Number)post("/registrations/quote",map("sessionId",s,"participants",bo.get("participants")),a[0]).get("code")).intValue());
    }
}
