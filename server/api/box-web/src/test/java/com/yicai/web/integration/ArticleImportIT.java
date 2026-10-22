package com.yicai.web.integration;

import com.yicai.web.tools.*;
import com.yicai.life.domain.bo.EssayImportBo;
import com.yicai.life.mapper.*;
import com.yicai.life.service.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh, loopback-only article database. No production, UAT, identity, unread or finance writes. */
class ArticleImportIT {
    static DataSource ds;static JdbcTemplate db;static EssayImportService imports;static QlMiniAppMapper readings;
    static EssayImportBo input(String id) {
        EssayImportBo i=new EssayImportBo();i.setSourceId(id);i.setSourceUrl("https://legacy.invalid/essay/"+id);
        i.setTitle("SYNTHETIC 原题 "+id);i.setIntroduction("非真实资料");i.setContent("<p>完整首段</p><p>完整尾段</p>");
        i.setAuthorName("SYNTHETIC 原作者");i.setPublishedAt("2020-05-13 07:59:37");i.setTimeBasis("legacy_insert_time");
        i.setStatus(1);i.setOrderNum(1);i.setCover("");i.setApprovalRef("synthetic-it");
        EssayImportBo.LabelInput l=new EssayImportBo.LabelInput();l.setSourceId("73");l.setName("复食计划&好转反应");l.setStatus(1);l.setLevel(2);i.getLabels().add(l);
        return i;
    }
    @BeforeAll static void setup() throws Exception {
        String url=System.getenv("QINGLIFE_ARTICLE_IMPORT_URL");ArticleImport.validateTargetUrl(url);
        ds=new DriverManagerDataSource(url,"root","");db=new JdbcTemplate(ds);
        String schema;try(Connection c=ds.getConnection()){DatabaseMigration.run(c,Paths.get(System.getProperty("qinglife.migrations")).getParent());schema=c.getCatalog();}
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM essay",Integer.class));
        db.update("INSERT INTO sys_config(config_name,config_key,config_value,config_type) VALUES('Article isolation','qinglife.article.import_isolation',?,'N')",schema+":loopback-only");
        imports=new EssayImportService(new EssayImportMapper(db),new DataSourceTransactionManager(ds));
        MybatisConfiguration config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(QlMiniAppMapper.class);
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/life/QlMiniAppMapper.xml"));
        readings=new SqlSessionTemplate(factory.getObject()).getMapper(QlMiniAppMapper.class);
        // An existing article with the old source number must not be overwritten.
        db.update("INSERT INTO essay(id,title,content,status,home_featured,author_name) VALUES(1,'SYNTHETIC existing','<p>existing</p>',1,0,'Existing')");
    }
    static EssayImportService.Result run(String source,EssayImportBo i,boolean dry,boolean readable) {
        return imports.run(source,"synthetic-20261016",Collections.singletonList(i),dry,readable).get(0);
    }
    @Test void dryRunHasZeroWritesAndApplyPreservesFieldsLabelsAndAvoidsUnreadFanout() {
        EssayImportBo i=input("1");i.setContent("<p>前</p><img src='https://media.invalid/a.jpg'><video src='https://media.invalid/v.mp4'></video><p>后</p>");
        int before=db.queryForObject("SELECT COUNT(*) FROM essay",Integer.class);
        int attempts=db.queryForObject("SELECT COUNT(*) FROM ql_article_import_attempt",Integer.class);
        assertEquals("WOULD_IMPORT",run("synthetic-main",i,true,false).outcome);
        assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM essay",Integer.class));
        assertEquals(attempts,db.queryForObject("SELECT COUNT(*) FROM ql_article_import_attempt",Integer.class));
        EssayImportService.Result result=run("synthetic-main",i,false,true);assertEquals("IMPORTED",result.outcome);assertNotEquals(1L,result.targetId.longValue());
        Map<String,Object> row=readings.selectPublicReading(result.targetId);
        assertEquals(i.getTitle(),row.get("title"));assertEquals(i.getAuthorName(),row.get("author"));assertTrue(row.get("content").toString().contains("后"));
        assertEquals("复食计划&好转反应",row.get("category"));assertEquals(Timestamp.valueOf("2020-05-13 07:59:37"),db.queryForObject("SELECT insert_time FROM essay WHERE id=?",Timestamp.class,result.targetId));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM app_essay_user",Integer.class));
        assertEquals("UNCHANGED",run("synthetic-main",i,false,true).outcome);
        assertEquals(before+1,db.queryForObject("SELECT COUNT(*) FROM essay",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM essay_label WHERE essay=?",Integer.class,result.targetId));
        assertEquals("SYNTHETIC existing",db.queryForObject("SELECT title FROM essay WHERE id=1",String.class));
    }
    @Test void pendingAndDisabledRemainPrivateAndTargetEditsAreNotOverwritten() {
        EssayImportBo i=input("pending");EssayImportService.Result r=run("synthetic-private",i,false,false);
        assertEquals("IMPORTED",r.outcome);assertNull(readings.selectPublicReading(r.targetId));
        db.update("UPDATE essay SET title='SYNTHETIC operator edit' WHERE id=?",r.targetId);
        EssayImportService.Result replay=run("synthetic-private",i,false,false);
        assertEquals("TARGET_CHANGED",replay.errorCode);assertEquals("CONFLICT",replay.outcome);
        i=input("disabled");i.setStatus(0);r=run("synthetic-private",i,false,true);assertNull(readings.selectPublicReading(r.targetId));
    }
    @Test void sourceChangesAndFailedLabelTransactionsLeaveNoPartialArticleAndCanRetry() {
        EssayImportBo first=input("original");assertEquals("IMPORTED",run("synthetic-conflict",first,false,false).outcome);
        first.setContent("<p>源修订</p>");assertEquals("SOURCE_CHANGED",run("synthetic-conflict",first,false,false).errorCode);
        EssayImportBo failed=input("label-fail");failed.getLabels().get(0).setName("冲突的新名称");
        int before=db.queryForObject("SELECT COUNT(*) FROM essay",Integer.class);
        assertEquals("REJECTED",run("synthetic-conflict",failed,true,false).outcome);
        assertEquals("REJECTED",run("synthetic-conflict",failed,false,false).outcome);
        assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM essay",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ql_article_source WHERE source_system='synthetic-conflict' AND source_id='label-fail'",Integer.class));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM ql_article_import_attempt WHERE source_system='synthetic-conflict' AND outcome='REJECTED'",Integer.class)>0);
        failed.getLabels().get(0).setName("复食计划&好转反应");assertEquals("IMPORTED",run("synthetic-conflict",failed,false,false).outcome);
    }
    @Test void concurrentSameSourceCreatesOneTargetAndOneLabelAssociation()throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<EssayImportService.Result> a=pool.submit(()->run("synthetic-race",input("same"),false,false));
            Future<EssayImportService.Result> b=pool.submit(()->run("synthetic-race",input("same"),false,false));
            List<String> states=Arrays.asList(a.get().outcome,b.get().outcome);
            assertTrue(states.contains("IMPORTED"));assertTrue(states.contains("UNCHANGED"));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ql_article_source WHERE source_system='synthetic-race'",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void cliRejectsRemoteTargetsOtherSchemasAndConnectionOverrides() {
        for(String url:Arrays.asList("jdbc:mysql://remote:3306/qinglife_it_article_a?useSSL=false","jdbc:mysql://127.0.0.1:3306/qinglife?useSSL=false","jdbc:mysql://127.0.0.1:3306/qinglife_it_article_a?socketFactory=bad"))
            assertThrows(IllegalArgumentException.class,()->ArticleImport.validateTargetUrl(url));
    }
}
