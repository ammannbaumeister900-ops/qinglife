package com.yicai.life.mapper;

import com.yicai.life.domain.bo.EssayImportBo;
import com.yicai.life.service.EssayImportPolicy;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import java.sql.*;
import java.util.*;

/** Article import persistence only. Never accesses customer, unread, order, or payment tables. */
public class EssayImportMapper {
    private final JdbcTemplate db;
    public EssayImportMapper(JdbcTemplate db){this.db=db;}
    public Map<String,Object> source(String system,String id,boolean lock) {
        List<Map<String,Object>> rows=db.queryForList("SELECT essay_id,source_hash,target_hash FROM ql_article_source WHERE source_system=? AND source_id=?"+(lock?" FOR UPDATE":""),system,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void reserve(String system,String batch,EssayImportBo i) {
        db.update("INSERT IGNORE INTO ql_article_source(source_system,source_id,source_url,source_status,approval_ref,batch_id) VALUES(?,?,?,?,?,?)",
                system,i.getSourceId(),i.getSourceUrl(),i.getStatus(),i.getApprovalRef(),batch);
    }
    public long insert(EssayImportPolicy.Prepared p,boolean readable) {
        EssayImportBo i=p.input;
        return generated("INSERT INTO essay(title,content,Introduction,status,home_featured,order_num,insert_time,author_name,title_url,is_video) VALUES(?,?,?,?,0,?,?,?,?,?)",
                i.getTitle(),p.content,i.getIntroduction(),readable?i.getStatus():0,i.getOrderNum(),Timestamp.valueOf(p.publishedAt),i.getAuthorName(),p.cover,p.isVideo);
    }
    public long label(String system,String batch,EssayImportBo.LabelInput input) {
        db.update("INSERT IGNORE INTO ql_article_label_source(source_system,source_id,batch_id) VALUES(?,?,?)",system,input.getSourceId(),batch);
        Map<String,Object> map=db.queryForMap("SELECT label_id,source_name,source_status FROM ql_article_label_source WHERE source_system=? AND source_id=? FOR UPDATE",system,input.getSourceId());
        Long target=existingLabel(input,map);
        long id=target==null?generated("INSERT INTO label(name,status,level,remark) VALUES(?,?,?,?)",input.getName(),input.getStatus(),input.getLevel(),"Article import "+batch):target;
        db.update("UPDATE ql_article_label_source SET label_id=?,source_name=?,source_status=? WHERE source_system=? AND source_id=?",id,input.getName(),input.getStatus(),system,input.getSourceId());
        return id;
    }
    public void validateLabels(String system,List<EssayImportBo.LabelInput> labels) {
        for(EssayImportBo.LabelInput input:labels) {
            List<Map<String,Object>> rows=db.queryForList("SELECT label_id,source_name,source_status FROM ql_article_label_source WHERE source_system=? AND source_id=?",system,input.getSourceId());
            existingLabel(input,rows.isEmpty()?null:rows.get(0));
        }
    }
    private Long existingLabel(EssayImportBo.LabelInput input,Map<String,Object> map) {
        if(map!=null && map.get("label_id")!=null) {
            EssayImportPolicy.require(input.getName().equals(map.get("source_name")) && input.getStatus().equals(((Number)map.get("source_status")).intValue()),"LABEL_SOURCE_CHANGED");
            long id=((Number)map.get("label_id")).longValue();
            List<Map<String,Object>> target=db.queryForList("SELECT name,status+0 AS status FROM label WHERE id=?",id);
            EssayImportPolicy.require(target.size()==1 && input.getName().equals(target.get(0).get("name")) && input.getStatus().equals(((Number)target.get(0).get("status")).intValue()),"LABEL_TARGET_CHANGED");
            return id;
        }
        List<Map<String,Object>> matches=db.queryForList("SELECT id,status+0 AS status FROM label WHERE BINARY name=BINARY ? ORDER BY id",input.getName());
        EssayImportPolicy.require(matches.size()<=1,"AMBIGUOUS_TARGET_LABEL");
        if(matches.isEmpty())return null;
        EssayImportPolicy.require(input.getStatus().equals(((Number)matches.get(0).get("status")).intValue()),"LABEL_STATUS_CONFLICT");
        return ((Number)matches.get(0).get("id")).longValue();
    }
    public void link(long article,long label){db.update("INSERT INTO essay_label(essay,label) VALUES(?,?)",article,label);}
    public void complete(String system,String id,long target,EssayImportPolicy.Prepared p) {
        db.update("UPDATE ql_article_source SET essay_id=?,source_hash=?,target_hash=?,media_map=? WHERE source_system=? AND source_id=?",
                target,p.sourceHash,targetHash(target),JSONUtil.toJsonStr(new TreeMap<>(p.input.getMediaMap())),system,id);
    }
    public String targetHash(long id) {
        List<Map<String,Object>> rows=db.query("SELECT title,content,Introduction,status,home_featured,order_num,insert_time,author_name,title_url,is_video FROM essay WHERE id=?",(rs,n)->{
            Map<String,Object> values=new LinkedHashMap<>();
            for(int c=1;c<=10;c++)values.put(rs.getMetaData().getColumnLabel(c),Objects.toString(rs.getString(c),""));
            return values;
        },id);
        if(rows.isEmpty())return null;
        List<String> labels=db.query("SELECT CONCAT(l.id,':',l.name,':',l.status,':',COALESCE(l.level,0)) AS value FROM essay_label el JOIN label l ON l.id=el.label WHERE el.essay=? ORDER BY el.id",(rs,n)->rs.getString(1),id);
        rows.get(0).put("labels",labels);
        return DigestUtil.sha256Hex(JSONUtil.toJsonStr(rows.get(0)));
    }
    public void attempt(String batch,String source,String id,String outcome,String error) {
        db.update("INSERT INTO ql_article_import_attempt(batch_id,source_system,source_id,outcome,error_code) VALUES(?,?,?,?,?)",
                batch,source,id!=null && id.matches("[A-Za-z0-9_.:-]{1,191}")?id:null,outcome,error);
    }
    private long generated(String sql,Object... args) {
        KeyHolder key=new GeneratedKeyHolder();
        db.update(connection->{PreparedStatement p=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int n=0;n<args.length;n++)p.setObject(n+1,args[n]);return p;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
