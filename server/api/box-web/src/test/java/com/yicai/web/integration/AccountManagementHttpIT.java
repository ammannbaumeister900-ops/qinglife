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
 "qinglife.wechat.enabled=false","token.secret=synthetic-account-http-secret-0123456789abcdef",
 "spring.boot.admin.client.enabled=false","server.servlet.context-path=","ruoyi.imagePath=https://example.invalid/images/"})
@ActiveProfiles("dev")
class AccountManagementHttpIT {
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
        String url=System.getenv("QINGLIFE_HTTP_MYSQL_URL"),port=System.getenv("QINGLIFE_TEST_REDIS_PORT");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_http_[a-z0-9_]+\\?.*"))
            throw new IllegalStateException("Fresh loopback HTTP database required");
        if(port==null||!port.matches("[0-9]+"))throw new IllegalStateException("Fresh isolated Redis required");
        try(Connection c=DriverManager.getConnection(url,"root","")){
            DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());
            System.out.println("ACCOUNT_HTTP_ISOLATED_DATABASE="+c.getCatalog());
        }
        uploads=Files.createTempDirectory("qinglife-account-http-");
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
    void success(Map response) { assertNotNull(response); assertTrue(response.containsKey("code"),response.toString()); assertEquals(200, ((Number)response.get("code")).intValue(), response.toString()); }
    void denied(Map response) { assertNotNull(response); assertTrue(response.containsKey("code"),response.toString()); assertNotEquals(200, ((Number)response.get("code")).intValue(), response.toString()); }
    String login(String name, String password) {
        String uuid=id();cache.setCacheObject("captcha_codes:"+uuid,"synthetic",2,TimeUnit.MINUTES);
        Map response=http.postForObject("/login",map("username",name,"password",password,"code","synthetic","uuid",uuid),Map.class);
        success(response);return String.valueOf(((Map)response.get("data")).get("token"));
    }
    Map createUser(String name, Long... roles) {
        return map("userName",name,"nickName","Synthetic staff","password","Synthetic-123","status","0","roleIds",roles,"postIds",new Long[0]);
    }
    long addUser(Long... roles) {
        String name="staff-"+id().substring(0,8);success(call("/system/user",HttpMethod.POST,createUser(name,roles),admin(),true));
        return db.queryForObject("SELECT user_id FROM sys_user WHERE user_name=?",Long.class,name);
    }
    long addRole(Long... menus) {
        String key="role-"+id().substring(0,8);
        success(call("/system/role",HttpMethod.POST,map("roleName",key,"roleKey",key,"roleSort","50","status","0","menuIds",menus,"dataScope","1"),admin(),true));
        return db.queryForObject("SELECT role_id FROM sys_role WHERE role_key=?",Long.class,key);
    }
    String token(long user) {return login(db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user),"Synthetic-123");}
    long app(String token) {return ((Number)cache.getCacheObject("appToken:"+token)).longValue();}
    Map binding(long app,long user) {return map("appUserId",app,"sysUserId",user,"enabled",true,"canOperate",false,"canPayment",false);}

    @Test void navigationRegistersExistingPagesWithoutGrantingSeededRoles() throws Exception {
        admin();
        Map routes=call("/getRouters",HttpMethod.GET,null,admin(),true);success(routes);
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(routes);
        assertTrue(json.contains("/system/user"));assertTrue(json.contains("/system/role"));assertTrue(json.contains("微信工作台绑定"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE r.role_key IN ('ql_admin','ql_operator','ql_leader','ql_finance') AND m.perms IN ('system:user:add','system:role:edit')",Integer.class));
        try(Connection c=db.getDataSource().getConnection()){DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());}
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms='system:user:list'",Integer.class));
    }
    @Test void createsPersonalAccountAndRejectsDuplicateAndMalformedRequests() {
        long role=addRole(2291L,2430L,2431L),user=addUser(role);
        String hash=db.queryForObject("SELECT password FROM sys_user WHERE user_id=?",String.class,user);
        assertTrue(SecurityUtils.matchesPassword("Synthetic-123",hash));
        success(call("/system/user/list",HttpMethod.GET,null,token(user),true));
        Map detail=call("/system/user/"+user,HttpMethod.GET,null,admin(),true);success(detail);
        assertFalse(((Map)((Map)detail.get("data")).get("user")).containsKey("password"));
        String name=db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user);
        success(call("/system/user",HttpMethod.PUT,map("userId",user,"userName","injected-login-name","nickName","Updated staff","status","0","password","injected","roleIds",new Long[]{role},"postIds",new Long[0]),admin(),true));
        assertTrue(SecurityUtils.matchesPassword("Synthetic-123",db.queryForObject("SELECT password FROM sys_user WHERE user_id=?",String.class,user)));
        assertEquals(name,db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user));
        Map duplicate=createUser(db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user));
        denied(call("/system/user",HttpMethod.POST,duplicate,admin(),true));
        duplicate.put("userName","staff-"+id().substring(0,8));duplicate.put("userId",1);
        denied(call("/system/user",HttpMethod.POST,duplicate,admin(),true));
        duplicate.remove("userId");duplicate.put("password","");denied(call("/system/user",HttpMethod.POST,duplicate,admin(),true));
        duplicate.put("password","Synthetic-123");duplicate.put("roleIds",new Long[]{1L});denied(call("/system/user",HttpMethod.POST,duplicate,admin(),true));
    }
    @Test void bothRoleAssignmentDirectionsProtectReservedAndInactiveRoles() {
        long user=addUser(),role=addRole(2291L,2430L);
        success(call("/system/user/authRole?userId="+user+"&roleIds="+role,HttpMethod.PUT,null,admin(),true));
        denied(call("/system/user/authRole?userId=1&roleIds="+role,HttpMethod.PUT,null,admin(),true));
        denied(call("/system/user/authRole?userId="+user+"&roleIds=1",HttpMethod.PUT,null,admin(),true));
        denied(call("/system/role/authUser/selectAll?roleId=1&userIds="+user,HttpMethod.PUT,null,admin(),true));
        denied(call("/system/role/authUser/selectAll?roleId="+role+"&userIds=1",HttpMethod.PUT,null,admin(),true));
        denied(call("/system/role/authUser/cancel",HttpMethod.PUT,map("roleId",1,"userId",user),admin(),true));
        denied(call("/system/role/authUser/cancelAll?roleId=1&userIds="+user,HttpMethod.PUT,null,admin(),true));
        success(call("/system/role/authUser/cancelAll?roleId="+role+"&userIds="+user,HttpMethod.PUT,null,admin(),true));
        success(call("/system/role/authUser/selectAll?roleId="+role+"&userIds="+user,HttpMethod.PUT,null,admin(),true));
        success(call("/system/role/changeStatus",HttpMethod.PUT,map("roleId",role,"status","1"),admin(),true));
        denied(call("/system/user/authRole?userId="+user+"&roleIds="+role,HttpMethod.PUT,null,admin(),true));
        denied(call("/system/user/authRole?userId="+user+"&roleIds=999999",HttpMethod.PUT,null,admin(),true));
    }
    @Test void permissionChangesAndRoleDisableAffectAlreadyIssuedTokens() {
        long role=addRole(2291L,2430L,2431L),user=addUser(role);String token=token(user);
        success(call("/system/user/list",HttpMethod.GET,null,token,true));
        success(call("/system/role",HttpMethod.PUT,map("roleId",role,"roleName","Changed","roleKey","changed-"+id().substring(0,8),"roleSort","50","status","0","menuIds",new Long[]{2291L}),admin(),true));
        assertEquals(403,call("/system/user/list",HttpMethod.GET,null,token,true).get("code"));
        db.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(?,2430)",role);
        success(call("/system/user/list",HttpMethod.GET,null,token,true));
        success(call("/system/role/changeStatus",HttpMethod.PUT,map("roleId",role,"status","1"),admin(),true));
        assertEquals(403,call("/system/user/list",HttpMethod.GET,null,token,true).get("code"));
    }
    @Test void rebindAndUnbindReleaseBackendAccountWithoutDeletingEitherIdentity() {
        String[] one=account(),two=account();long first=app(one[0]),second=app(two[0]),user=addUser();
        success(call("/life/staff",HttpMethod.PUT,binding(first,1),admin(),true));
        success(get("/staff/me",one[0]));
        success(call("/life/staff",HttpMethod.PUT,binding(first,user),admin(),true));
        assertEquals(user,db.queryForObject("SELECT sys_user_id FROM ql_staff_access WHERE app_user_id=?",Long.class,first));
        success(call("/life/staff",HttpMethod.PUT,binding(second,1),admin(),true));
        denied(call("/life/staff",HttpMethod.PUT,binding(second,user),admin(),true));
        Map list=call("/life/staff/list",HttpMethod.GET,null,admin(),true);success(list);
        assertTrue(((List<Map>)list.get("data")).stream().anyMatch(row->row.containsKey("userName")&&row.containsKey("roleNames")));
        success(call("/life/staff/"+first,HttpMethod.DELETE,null,admin(),true));
        assertEquals(403,get("/staff/me",one[0]).get("code"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM app_user_info WHERE id=?",Integer.class,first));
        assertEquals("0",db.queryForObject("SELECT del_flag FROM sys_user WHERE user_id=?",String.class,user));
        success(call("/life/staff/"+second,HttpMethod.DELETE,null,admin(),true));
        success(call("/life/staff",HttpMethod.PUT,binding(second,user),admin(),true));
        success(get("/staff/me",two[0]));
    }
    @Test void disabledAccountBlocksBothSessionsAndStatusPayloadCannotRewriteOtherFields() {
        long role=addRole(2291L,2430L),user=addUser(role);String token=token(user),name=db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user);
        String[] wx=account();success(call("/life/staff",HttpMethod.PUT,binding(app(wx[0]),user),admin(),true));
        success(call("/system/user/changeStatus",HttpMethod.PUT,map("userId",user,"status","1","password","injected","userName","injected","delFlag","2"),admin(),true));
        assertEquals(name,db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user));
        assertEquals("0",db.queryForObject("SELECT del_flag FROM sys_user WHERE user_id=?",String.class,user));
        assertTrue(SecurityUtils.matchesPassword("Synthetic-123",db.queryForObject("SELECT password FROM sys_user WHERE user_id=?",String.class,user)));
        assertEquals(401,call("/system/user/list",HttpMethod.GET,null,token,true).get("code"));
        assertEquals(403,get("/staff/me",wx[0]).get("code"));
        denied(call("/system/user/changeStatus",HttpMethod.PUT,map("userId",1,"status","1"),admin(),true));
        denied(call("/system/user/1",HttpMethod.DELETE,null,admin(),true));
    }
    @Test void limitedManagerCannotEscalateUsingRoleMenusOrEitherAssignmentDirection() {
        long managerRole=addRole(2291L,2430L,2431L,2432L,2433L,2440L,2441L,2442L,2443L),manager=addUser(managerRole),target=addUser();String token=token(manager);
        long privileged=addRole(2290L); // The manager has no WeChat binding permission.
        denied(call("/system/user/authRole?userId="+target+"&roleIds="+privileged,HttpMethod.PUT,null,token,true));
        denied(call("/system/role/authUser/selectAll?roleId="+privileged+"&userIds="+target,HttpMethod.PUT,null,token,true));
        denied(call("/system/role",HttpMethod.POST,map("roleName","Escalate","roleKey","escalate","roleSort","50","status","0","menuIds",new Long[]{2290L}),token,true));
        denied(call("/system/role",HttpMethod.POST,map("roleName","Reserved","roleKey","admin","roleSort","50","status","0","menuIds",new Long[0]),admin(),true));
        assertEquals(403,call("/life/staff/list",HttpMethod.GET,null,token,true).get("code"));
        long ordinary=addUser();assertEquals(403,call("/system/user/list",HttpMethod.GET,null,token(ordinary),true).get("code"));
    }
    @Test void passwordStatusAndDataScopeRequestsOnlyUpdateTheirIntendedFields() {
        long role=addRole(2291L,2430L),user=addUser(role);
        String name=db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user);
        success(call("/system/user/resetPwd",HttpMethod.PUT,map("userId",user,"password","Synthetic-456","userName","injected","status","1","delFlag","2"),admin(),true));
        assertEquals(name,db.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user));
        assertEquals("0",db.queryForObject("SELECT status FROM sys_user WHERE user_id=?",String.class,user));
        assertTrue(SecurityUtils.matchesPassword("Synthetic-456",db.queryForObject("SELECT password FROM sys_user WHERE user_id=?",String.class,user)));
        String key=db.queryForObject("SELECT role_key FROM sys_role WHERE role_id=?",String.class,role);
        success(call("/system/role/changeStatus",HttpMethod.PUT,map("roleId",role,"status","1","roleKey","admin","delFlag","2"),admin(),true));
        success(call("/system/role/dataScope",HttpMethod.PUT,map("roleId",role,"dataScope","5","deptIds",new Long[0],"roleKey","admin","status","0","delFlag","2"),admin(),true));
        Map record=db.queryForMap("SELECT role_key,status,del_flag,data_scope FROM sys_role WHERE role_id=?",role);
        assertEquals(key,record.get("role_key"));assertEquals("1",record.get("status"));assertEquals("0",record.get("del_flag"));assertEquals("5",record.get("data_scope"));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=?",Integer.class,role));
    }
    @Test void browserCreatesRoleAccountAndWechatBindingUsingRealLocalApi() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("QINGLIFE_ACCOUNT_UI_TEST")), "Optional local browser environment required");
        Path repo=Paths.get(System.getProperty("qinglife.migrations")).toAbsolutePath().normalize().getParent().getParent();
        Path artifacts=repo.resolve(".tools/account-management-20261012");Files.createDirectories(artifacts);
        String name="ui-account-"+id().substring(0,8);String[] wx=account();long app=app(wx[0]);
        db.update("UPDATE app_user_info SET nick_name=? WHERE id=?",name,app);
        db.update("UPDATE ql_customer SET nickname=? WHERE id=?",name,wx[1]);
        Process process=new ProcessBuilder("node",repo.resolve("scripts/test-account-management-ui.cjs").toString())
            .directory(repo.toFile()).redirectErrorStream(true).redirectOutput(artifacts.resolve("browser-run.log").toFile()).start();
        try {
            process.getOutputStream().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(map("api","http://127.0.0.1:"+httpPort,"token",admin(),"name",name)));
            process.getOutputStream().close();
            assertTrue(process.waitFor(120,TimeUnit.SECONDS),"Browser workflow timed out; inspect local browser artifacts");
            assertEquals(0,process.exitValue(),"Browser workflow failed; inspect local browser artifacts");
        } finally { if(process.isAlive())process.destroyForcibly(); }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id WHERE u.user_name=? AND r.role_key=?",Integer.class,name,name));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_staff_access WHERE app_user_id=?",Integer.class,app));
        assertEquals(403,get("/staff/me",wx[0]).get("code"));
    }

}
