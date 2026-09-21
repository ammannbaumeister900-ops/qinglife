package com.yicai.web.integration;

import com.yicai.BoxApplication;
import com.yicai.web.service.QlWechatSessionClient;
import com.yicai.web.tools.DatabaseMigration;
import com.yicai.common.core.redis.RedisCache;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real HTTP, real Spring security/services, migrated MySQL and real isolated Redis. Only WeChat is mocked. */
@SpringBootTest(classes=BoxApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"logging.config=classpath:logback-http-test.xml","spring.main.banner-mode=off","qinglife.wechat.enabled=true","qinglife.wechat.app-id=synthetic","qinglife.wechat.app-secret=synthetic","token.secret=synthetic-http-test-secret-0123456789abcdef","spring.boot.admin.client.enabled=false"})
@ActiveProfiles("dev")
class LoginHttpIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired RedisCache cache;
    @Autowired RedissonClient redis;
    @MockBean QlWechatSessionClient wechat;
    static Path uploads;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception {
        String url=System.getenv("QINGLIFE_HTTP_MYSQL_URL"),port=System.getenv("QINGLIFE_TEST_REDIS_PORT");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_http_[a-z0-9_]+\\?.*"))throw new IllegalStateException("Fresh isolated HTTP test MySQL database required");
        if(port==null||!port.matches("[0-9]+"))throw new IllegalStateException("Isolated Redis tunnel port required");
        String user=System.getenv().getOrDefault("QINGLIFE_HTTP_MYSQL_USER","root");
        String password=System.getenv().getOrDefault("QINGLIFE_HTTP_MYSQL_PASSWORD","");
        try(Connection c=DriverManager.getConnection(url,user,password)){DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());}
        uploads=Files.createTempDirectory("qinglife-http-uploads-");
        p.add("spring.datasource.dynamic.datasource.master.url",()->url);
        p.add("spring.datasource.dynamic.datasource.master.username",()->user);
        p.add("spring.datasource.dynamic.datasource.master.password",()->password);
        p.add("spring.redis.host",()->"127.0.0.1");p.add("spring.redis.port",()->port);p.add("spring.redis.password",()->"");p.add("spring.redis.database",()->0);
        p.add("ruoyi.profile",()->uploads.toString());
    }
    @BeforeEach void mockWechat(){when(wechat.exchange(anyString())).thenAnswer(i->"http-test-"+i.getArgument(0));}
    @AfterAll static void cleanup()throws Exception{if(uploads!=null)Files.deleteIfExists(uploads);}
    Map<String,Object> get(String path,String token){HttpHeaders h=new HttpHeaders();if(token!=null)h.set("token",token);return http.exchange(path,HttpMethod.GET,new HttpEntity<>(h),Map.class).getBody();}
    Map<String,Object> login(String code){return http.postForObject("/app/qinglife/auth/login",Collections.singletonMap("code",code),Map.class);}
    String token(String code){Map<String,Object> response=login(code);assertEquals(200,((Number)response.get("code")).intValue());return String.valueOf(((Map<?,?>)response.get("data")).get("token"));}
    int appTokenCount(){int count=0;for(String ignored:redis.getKeys().getKeysByPattern("appToken:*"))count++;return count;}
    @Test void loginIssuesExpiringRedisTokenAndRealOverview() {
        String code=UUID.randomUUID().toString(),token=token(code);
        Object user=cache.getCacheObject("appToken:"+token);assertNotNull(user);assertTrue(redis.getBucket("appToken:"+token).remainTimeToLive()>0);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM app_user_info WHERE open_id=?",Integer.class,"http-test-"+code));
        assertEquals(200,((Number)get("/app/qinglife/me/overview",token).get("code")).intValue());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_customer_identifier WHERE legacy_app_user_id=?",Integer.class,user));
    }
    @Test void revokedAndExpiredTokensCannotReadPrivateOverview()throws Exception {
        String revoked=token(UUID.randomUUID().toString());cache.deleteObject("appToken:"+revoked);
        assertEquals(401,((Number)get("/app/qinglife/me/overview",revoked).get("code")).intValue());
        String expired=token(UUID.randomUUID().toString());cache.expire("appToken:"+expired,1,TimeUnit.MILLISECONDS);
        for(int i=0;i<20&&cache.getCacheObject("appToken:"+expired)!=null;i++)Thread.sleep(10);
        assertEquals(401,((Number)get("/app/qinglife/me/overview",expired).get("code")).intValue());
    }
    @Test void disabledAccountCannotReuseValidTokenOrLogin() {
        String code=UUID.randomUUID().toString(),token=token(code);
        db.update("UPDATE app_user_info SET status=0 WHERE open_id=?","http-test-"+code);
        assertEquals(401,((Number)get("/app/qinglife/me/overview",token).get("code")).intValue());
        int tokensBefore=appTokenCount();
        assertEquals(403,((Number)login(code).get("code")).intValue());
        assertEquals(tokensBefore,appTokenCount());
    }
    @Test void databaseFailureNeverIssuesToken() {
        int before=appTokenCount();String code=UUID.randomUUID().toString();
        db.execute("ALTER TABLE app_user_info ADD CONSTRAINT ql_http_refuse_insert CHECK (open_id <> 'http-test-"+code+"')");
        try {
            Map<String,Object> response=login(code);
            assertEquals(500,((Number)response.get("code")).intValue());
            assertEquals("服务暂不可用，请稍后重试",response.get("msg"));
            assertFalse(response.toString().contains("ql_http_refuse_insert"));
            assertFalse(response.toString().contains("app_user_info"));
            assertEquals(before,appTokenCount());
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM app_user_info WHERE open_id=?",Integer.class,"http-test-"+code));
        }
        finally{db.execute("ALTER TABLE app_user_info DROP CHECK ql_http_refuse_insert");}
    }
    @Test void onlyExplicitPublicFilesAreServedAnonymously() throws Exception {
        Path publicDir=Files.createDirectories(uploads.resolve("public"));
        Path publicFile=publicDir.resolve("http-contract.txt");
        Path privateFile=uploads.resolve("private.html");
        Files.write(publicFile,"public-content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(privateFile,"private-content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            ResponseEntity<String> allowed=http.getForEntity("/profile/public/http-contract.txt",String.class);
            assertEquals(200,allowed.getStatusCodeValue());
            assertEquals("public-content",allowed.getBody());
            ResponseEntity<String> denied=http.getForEntity("/data/static/private.html",String.class);
            assertFalse(denied.getBody().contains("private-content"));
            assertEquals(401,((Number)get("/data/static/private.html",null).get("code")).intValue());
        } finally {
            Files.deleteIfExists(publicFile);Files.deleteIfExists(privateFile);Files.deleteIfExists(publicDir);
        }
    }
    @Test void securityChainProtectsDocsFilesAndManagement() {
        for(String path:Arrays.asList("/doc.html","/common/download?fileName=synthetic.txt","/profile/private.txt","/data/static/private.html","/profile/download/private.xlsx","/actuator/env","/druid/index.html"))
            assertEquals(401,((Number)get(path,null).get("code")).intValue(),path);
        ResponseEntity<String> health=http.getForEntity("/actuator/health",String.class);assertEquals(200,health.getStatusCodeValue());assertFalse(health.getBody().contains("components"));
    }
}
