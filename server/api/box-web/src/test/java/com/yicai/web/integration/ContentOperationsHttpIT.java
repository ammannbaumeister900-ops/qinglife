package com.yicai.web.integration;
import com.yicai.common.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh MySQL + authenticated HTTP; inherited pricing checks protect existing endpoints. */
class ContentOperationsHttpIT extends RegistrationPricingHttpIT {
    @BeforeEach void resetContentFixture(){db.update("UPDATE ql_article_placement SET essay_id=NULL,enabled=FALSE,authorization_confirmed=FALSE,editorial_approved=FALSE,identity_confirmed=FALSE,article_hash=NULL,revision=0,evidence_ref=NULL");db.update("DELETE FROM ql_article_placement_audit");}
    String article(boolean enabled) {
        String title="SYNTHETIC content "+id();
        okStatus(call("/life/essay",HttpMethod.POST,map("title",title,"Introduction","合成测试摘要","content","<p>合成正文，非正式故事。</p>","authorName","合成原作者","contentOrigin","wechat","sourceUrl","https://mp.weixin.qq.com/s/synthetic-only","status",enabled?1:0,"homeFeatured",1,"orderNum",0,"labels",new Long[0]),admin(),true));
        return db.queryForObject("SELECT CAST(id AS CHAR) FROM essay WHERE title=?",String.class,title);
    }
    void okStatus(Map response){assertEquals(200,((Number)response.get("code")).intValue(),response.toString());}
    String hash(String id){return ok(call("/life/essay/"+id+"/content-review",HttpMethod.GET,null,admin(),true)).get("hash").toString();}
    Map body(String id,int revision){return map("essayId",id,"expectedRevision",revision,"expectedArticleHash",hash(id),"enabled",true,"authorizationConfirmed",true,"editorialApproved",true,"identityConfirmed",true,"evidenceRef","Synthetic verified authorization and complete editorial review");}
    Map save(String key,Map body){return call("/life/essay/placements/"+key,HttpMethod.PUT,body,admin(),true);}
    String viewer(){return contentActor("life:essay:list","life:essay:query");}
    String contentActor(String... permissions){
        long user=Math.abs(UUID.randomUUID().getMostSignificantBits()%100000000)+1000;String name="content-"+id().substring(0,12),password=id();
        db.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES(?,?,?,1,'0','0')",user,name,name);
        db.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,password,status,del_flag) VALUES(?,0,?,'Synthetic content reader','00',?,'0','0')",user,name,SecurityUtils.encryptPassword(password));
        db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",user,user);
        for(String permission:permissions)db.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,menu_id FROM sys_menu WHERE perms=?",user,permission);
        String key=id();cache.setCacheObject("captcha_codes:"+key,"synthetic",2,TimeUnit.MINUTES);
        Map login=http.postForObject("/login",map("username",name,"password",password,"code","synthetic","uuid",key),Map.class);okStatus(login);return ((Map)login.get("data")).get("token").toString();
    }
    @Test void anonymousContentLinksHideDraftsAndUnreviewedContentWithoutExposingEvidence() {
        String a=article(false);Map b=body(a,0);assertEquals(400,save("about.huahua-family",b).get("code"));
        assertNull(ok(get("/content-placements/about.huahua-family",null)).get("item"));
        db.update("UPDATE essay SET status=1 WHERE id=?",a);b=body(a,0);b.put("editorialApproved",false);assertEquals(400,save("about.huahua-family",b).get("code"));
        ok(save("about.huahua-family",body(a,0)));Map item=(Map)ok(get("/content-placements/about.huahua-family",null)).get("item");
        assertEquals(a,item.get("businessArticleId"));assertEquals("huahua-family",item.get("personKey"));assertFalse(item.toString().contains("Synthetic verified"));
        assertEquals(401,call("/life/essay/placements",HttpMethod.GET,null,null,true).get("code"));
    }
    @Test void stalePersonConfigAndChangedArticleCannotReusePriorReview() {
        String a=article(true);Map old=body(a,0);ok(save("about.leilei",old));assertEquals(409,save("about.leilei",old).get("code"));
        Map prior=body(a,1);db.update("UPDATE essay SET content='<p>New unreviewed synthetic text</p>' WHERE id=?",a);
        assertNull(ok(get("/content-placements/about.leilei",null)).get("item"));assertEquals(409,save("about.leilei",prior).get("code"));
        ok(save("about.leilei",body(a,1)));assertNotNull(ok(get("/content-placements/about.leilei",null)).get("item"));
        db.update("UPDATE essay SET status=0 WHERE id=?",a);assertNull(ok(get("/content-placements/about.leilei",null)).get("item"));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ql_article_placement_audit WHERE placement_key='about.leilei'",Integer.class));
    }
    @Test void metadataRetainsWeChatSourceAndBylineButRejectsUnsafeOrigins() {
        String a=article(true);Map detail=ok(call("/life/essay/"+a,HttpMethod.GET,null,admin(),true));assertEquals("wechat",detail.get("contentOrigin"));assertEquals("合成原作者",detail.get("authorName"));
        assertEquals("合成原作者",ok(get("/readings/"+a,null)).get("author"));
        for(String url:Arrays.asList("http://mp.weixin.qq.com/s/a","https://mp.weixin.qq.com.evil.invalid/s/a","https://user@mp.weixin.qq.com/s/a","https://mp.weixin.qq.com:443/s/a","javascript:alert(1)")) {
            assertEquals(400,call("/life/essay",HttpMethod.PUT,map("id",a,"contentOrigin","wechat","sourceUrl",url),admin(),true).get("code"));
        }
        // Old clients may omit labels; editing preserves their existing relations and no longer throws NPE.
        okStatus(call("/life/essay",HttpMethod.PUT,map("id",a,"title","SYNTHETIC updated "+id()),admin(),true));
    }
    @Test void homeFeaturedIsIndependentOfStoryTagAndDoesNotGrantWriteRights() {
        String a=article(true);Long tag=db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.reading.story_label_id'",Long.class);
        db.update("INSERT INTO essay_label(essay,label) VALUES(?,?)",a,tag);
        List<Map> featured=(List<Map>)ok(get("/readings/home",null)).get("featured");assertTrue(featured.stream().anyMatch(row->a.equals(row.get("id"))));
        assertEquals(403,call("/life/essay/placements/about.huahua-family",HttpMethod.PUT,body(a,0),viewer(),true).get("code"));
        assertEquals("false",db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.finance.participant_write_enabled'",String.class));
    }
    @Test void concurrentConfigUpdatesKeepOneReviewedRevisionAndImmutableFingerprint() throws Exception {
        String a=article(true);Map body=body(a,0);admin();ExecutorService pool=Executors.newFixedThreadPool(2);
        try{List<Future<Map>> results=pool.invokeAll(Arrays.asList(()->save("about.founders",body),()->save("about.founders",body)));List<Object> codes=Arrays.asList(results.get(0).get().get("code"),results.get(1).get().get("code"));assertTrue(codes.contains(200));assertTrue(codes.contains(409));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_article_placement_audit WHERE placement_key='about.founders'",Integer.class));assertTrue(db.queryForObject("SELECT snapshot_json FROM ql_article_placement_audit WHERE placement_key='about.founders'",String.class).contains(body.get("expectedArticleHash").toString()));}finally{pool.shutdownNow();}
    }
    @Test void explicitlyGrantedContentRoleCanEditAndReviewWithoutFinanceRights() {
        String operator=contentActor("life:essay:list","life:essay:query","life:essay:add","life:essay:edit");String title="SYNTHETIC operator "+id();
        okStatus(call("/life/essay",HttpMethod.POST,map("title",title,"Introduction","合成摘要","content","<p>合成正文</p>","status",1,"orderNum",0,"labels",new Long[0]),operator,true));
        String article=db.queryForObject("SELECT CAST(id AS CHAR) FROM essay WHERE title=?",String.class,title);
        okStatus(call("/life/essay/"+article+"/content-review",HttpMethod.GET,null,operator,true));
        okStatus(call("/life/essay/placements/about.huahua-family",HttpMethod.PUT,body(article,0),operator,true));
        assertEquals(403,call("/life/registration/list",HttpMethod.GET,null,operator,true).get("code"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id IN(SELECT role_id FROM sys_role WHERE role_key IN('ql_admin','ql_operator')) AND menu_id BETWEEN 2510 AND 2513",Integer.class));
    }
    @Test void realBrowserMaintainsArticleAndReviewedStoryLink() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("QINGLIFE_CONTENT_UI_TEST")),"Opt-in compiled admin/browser environment required");
        String a=article(false);Path repo=Paths.get(System.getProperty("qinglife.migrations")).toAbsolutePath().normalize().getParent().getParent(),artifacts=Paths.get(System.getenv("QINGLIFE_CONTENT_ARTIFACTS"));
        Process process=new ProcessBuilder(System.getenv().getOrDefault("QINGLIFE_NODE","node"),repo.resolve("scripts/test-content-operations-ui.cjs").toString()).directory(repo.toFile()).redirectErrorStream(true).redirectOutput(artifacts.resolve("browser-run.log").toFile()).start();
        try{process.getOutputStream().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(map("api","http://127.0.0.1:"+httpPort,"token",admin(),"id",a)));process.getOutputStream().close();assertTrue(process.waitFor(180,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"Inspect browser-run.log");}finally{if(process.isAlive())process.destroyForcibly();}
        assertEquals("false",db.queryForObject("SELECT config_value FROM sys_config WHERE config_key='qinglife.finance.participant_write_enabled'",String.class));
    }
}
