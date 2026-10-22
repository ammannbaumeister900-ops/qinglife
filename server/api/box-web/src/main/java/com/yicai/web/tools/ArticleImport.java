package com.yicai.web.tools;

import com.yicai.life.domain.bo.EssayImportBo;
import com.yicai.life.mapper.EssayImportMapper;
import com.yicai.life.service.*;
import cn.hutool.json.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit local CLI. No HTTP endpoint, migration/startup hooks, or remote database support. */
public final class ArticleImport {
    private ArticleImport(){}
    public static void main(String[] args)throws Exception {
        if(args.length<1 || args.length>2 || args.length==2 && !"--apply".equals(args[1]))
            throw new IllegalArgumentException("ArticleImport manifest.json [--apply]; default is read-only dry run");
        String url=System.getenv("QINGLIFE_ARTICLE_IMPORT_URL");
        validateTargetUrl(url);
        JSONObject manifest=JSONUtil.parseObj(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        String source=manifest.getStr("sourceSystem"),batch=manifest.getStr("batchId");
        List<EssayImportBo> articles=manifest.getJSONArray("articles").toList(EssayImportBo.class);
        DriverManagerDataSource ds=new DriverManagerDataSource(url,Objects.toString(System.getenv("QINGLIFE_ARTICLE_IMPORT_USER"),"root"),Objects.toString(System.getenv("QINGLIFE_ARTICLE_IMPORT_PASSWORD"),""));
        try(Connection guard=ds.getConnection()) {
            String schema=guard.getCatalog();
            if(!schema.matches("qinglife_it_article_[a-z0-9_]+"))throw new IllegalArgumentException("Article rehearsal schema required");
            try(PreparedStatement p=guard.prepareStatement("SELECT config_value FROM sys_config WHERE config_key='qinglife.article.import_isolation'")) {
                try(ResultSet rows=p.executeQuery()) {
                    if(!rows.next() || !(schema+":loopback-only").equals(rows.getString(1)))throw new IllegalStateException("Fresh article-isolation marker required");
                }
            }
            try(PreparedStatement lock=guard.prepareStatement("SELECT GET_LOCK(?,0)")){
                lock.setString(1,"article-import-"+schema);
                try(ResultSet r=lock.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Another article importer is active");}
            }
            try {
                // Published test rows are possible only inside this loopback-only environment, with per-record approval.
                EssayImportService service=new EssayImportService(new EssayImportMapper(new JdbcTemplate(ds)),new DataSourceTransactionManager(ds));
                List<EssayImportService.Result> result=service.run(source,batch,articles,args.length==1,manifest.getBool("readableInLocalTest",false));
                System.out.println(JSONUtil.toJsonStr(result));
                if(result.stream().anyMatch(r->Arrays.asList("FAILED","REJECTED","CONFLICT").contains(r.outcome)))throw new IllegalStateException("Batch contains blocked records; see result codes");
            } finally {
                try(PreparedStatement release=guard.prepareStatement("SELECT RELEASE_LOCK(?)")){release.setString(1,"article-import-"+schema);release.execute();}
            }
        }
    }
    public static void validateTargetUrl(String url) {
        if(url==null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/qinglife_it_article_[a-z0-9_]+\\?.*"))
            throw new IllegalArgumentException("Only a loopback article rehearsal database is supported");
        String query=url.substring(url.indexOf('?')+1);
        for(String option:query.split("&")) {
            String name=option.split("=",2)[0];
            if(!Arrays.asList("useSSL","allowPublicKeyRetrieval","serverTimezone","useUnicode","characterEncoding","connectionTimeZone").contains(name))
                throw new IllegalArgumentException("Unsupported JDBC option");
        }
    }
}
