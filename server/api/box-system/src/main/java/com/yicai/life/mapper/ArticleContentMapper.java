package com.yicai.life.mapper;
import com.yicai.life.domain.bo.ArticlePlacementBo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository @RequiredArgsConstructor
public class ArticleContentMapper {
    private final JdbcTemplate db;
    private static final String COLUMNS="p.placement_key AS placementKey,p.person_key AS personKey,CAST(p.essay_id AS CHAR) AS essayId,p.enabled,p.authorization_confirmed AS authorizationConfirmed,p.editorial_approved AS editorialApproved,p.identity_confirmed AS identityConfirmed,p.evidence_ref AS evidenceRef,p.article_hash AS approvedArticleHash,p.revision,p.updated_by AS updatedBy,p.updated_at AS updatedAt,e.title,e.status+0 AS articleStatus";
    public List<Map<String,Object>> placements() {return db.queryForList("SELECT "+COLUMNS+" FROM ql_article_placement p LEFT JOIN essay e ON e.id=p.essay_id ORDER BY p.placement_key");}
    public Map<String,Object> placement(String key) {List<Map<String,Object>> rows=db.queryForList("SELECT "+COLUMNS+" FROM ql_article_placement p LEFT JOIN essay e ON e.id=p.essay_id WHERE p.placement_key=?",key);return rows.isEmpty()?null:rows.get(0);}
    public Map<String,Object> article(Long id,boolean lock) {List<Map<String,Object>> rows=db.queryForList("SELECT id,title,status+0 AS status,content,Introduction,author_name,title_url,insert_time,content_origin,source_url FROM essay WHERE id=?"+(lock?" FOR UPDATE":""),id);return rows.isEmpty()?null:rows.get(0);}
    public String approvedHash(String key){return db.queryForObject("SELECT article_hash FROM ql_article_placement WHERE placement_key=?",String.class,key);}
    public int save(String key,ArticlePlacementBo bo,Long actor,String hash) {
        return db.update("UPDATE ql_article_placement SET essay_id=?,enabled=?,authorization_confirmed=?,editorial_approved=?,identity_confirmed=?,evidence_ref=?,article_hash=?,revision=revision+1,updated_by=?,updated_at=NOW(3) WHERE placement_key=? AND revision=?",bo.getEssayId(),bo.getEnabled(),bo.getAuthorizationConfirmed(),bo.getEditorialApproved(),bo.getIdentityConfirmed(),bo.getEvidenceRef(),hash,actor,key,bo.getExpectedRevision());
    }
    public void audit(String key,int revision,String snapshot,Long actor) {db.update("INSERT INTO ql_article_placement_audit(placement_key,revision,snapshot_json,operator_id) VALUES(?,?,?,?)",key,revision,snapshot,actor);}
    public List<Map<String,Object>> audit(String key) {return db.queryForList("SELECT revision,snapshot_json AS snapshot,operator_id AS operatorId,created_at AS createdAt FROM ql_article_placement_audit WHERE placement_key=? ORDER BY revision DESC LIMIT 50",key);}
    public List<Map<String,Object>> provenance(Long id) {return db.queryForList("SELECT source_system AS sourceSystem,source_id AS sourceId,source_url AS sourceUrl,batch_id AS batchId,approval_ref AS approvalRef FROM ql_article_source WHERE essay_id=?",id);}
}
