package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.ArticlePlacementBo;
import com.yicai.life.mapper.ArticleContentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.json.JSONUtil;
import java.net.URI;
import java.util.*;

/** Reviewed content links reuse essay as the only body/title source; never substitute a different story. */
@Service @RequiredArgsConstructor
public class ArticleContentService {
    private final ArticleContentMapper mapper;
    public static void metadata(String origin,String url) {
        if(origin!=null&&!Arrays.asList("manual","wechat").contains(origin))throw new CustomException("文章录入类型无效",400);
        if("wechat".equals(origin)) {
            try {URI uri=new URI(url);if(!"https".equals(uri.getScheme())||!"mp.weixin.qq.com".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getFragment()!=null||!("/s".equals(uri.getPath())||uri.getPath().startsWith("/s/"))||url.length()>2048)throw new IllegalArgumentException();}
            catch(Exception bad){throw new CustomException("请填写有效的HTTPS公众号文章原文链接，正文仍须录入本站",400);}
        } else if(url!=null&&!url.trim().isEmpty()) throw new CustomException("原文链接仅用于公众号录入，请核对录入类型",400);
    }
    private void key(String key) {if(!Arrays.asList("about.huahua-family","about.leilei","about.founders").contains(key))throw new CustomException("内容位置不存在",404);}
    public static String hash(Map<String,Object> article) {
        if(article==null)return null;
        Map<String,String> values=new TreeMap<>();for(Map.Entry<String,Object> entry:article.entrySet())if(!"status".equals(entry.getKey()))values.put(entry.getKey(),Objects.toString(entry.getValue(),""));
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(JSONUtil.toJsonStr(values));
    }
    public Map<String,Object> review(Long id) {Map<String,Object> article=mapper.article(id,false);if(article==null)throw new CustomException("文章不存在",404);Map<String,Object> result=new LinkedHashMap<>();result.put("id",id.toString());result.put("title",article.get("title"));result.put("status",article.get("status"));result.put("hash",hash(article));return result;}
    public List<Map<String,Object>> list(){List<Map<String,Object>> rows=mapper.placements();for(Map<String,Object> row:rows){String current=row.get("essayId")==null?null:hash(mapper.article(Long.valueOf(row.get("essayId").toString()),false));row.put("currentArticleHash",current);row.put("reviewCurrent",current!=null&&current.equals(mapper.approvedHash(row.get("placementKey").toString())));}return rows;}
    public List<Map<String,Object>> audit(String key){key(key);return mapper.audit(key);}
    public List<Map<String,Object>> provenance(Long id){return mapper.provenance(id);}
    @Transactional public Map<String,Object> save(String key,ArticlePlacementBo bo,Long actor) {
        key(key);
        if(bo==null||bo.getExpectedRevision()==null||bo.getEnabled()==null||bo.getAuthorizationConfirmed()==null||bo.getEditorialApproved()==null||bo.getIdentityConfirmed()==null||bo.getEvidenceRef()!=null&&bo.getEvidenceRef().length()>500)throw new CustomException("请填写完整配置和当前修订号",400);
        Map<String,Object> article=bo.getEssayId()==null?null:mapper.article(bo.getEssayId(),true);
        if(bo.getEssayId()!=null&&article==null)throw new CustomException("关联文章不存在",400);
        if(bo.getEnabled()&&article!=null) {
            org.jsoup.nodes.Document body=org.jsoup.Jsoup.parseBodyFragment(Objects.toString(article.get("content"),""));
            if(body.text().trim().isEmpty()&&body.select("img,video").isEmpty())throw new CustomException("全文内容为空，不能启用认知页关联",400);
        }
        if(bo.getEnabled()&&(article==null||((Number)article.get("status")).intValue()!=1||!bo.getAuthorizationConfirmed()||!bo.getEditorialApproved()||!bo.getIdentityConfirmed()||bo.getEvidenceRef()==null||bo.getEvidenceRef().trim().isEmpty()))throw new CustomException("启用前须核实文章已启用、人物归属、素材授权及全文审核，并填写依据",400);
        String fingerprint=hash(article);
        if(bo.getEnabled()&&!Objects.equals(fingerprint,bo.getExpectedArticleHash()))throw new CustomException("文章内容已变化，请刷新并重新核实全文",409);
        if(mapper.save(key,bo,actor,fingerprint)!=1)throw new CustomException("配置已被其他人修改，请刷新核对后重试",409);
        Map<String,Object> saved=mapper.placement(key);mapper.audit(key,((Number)saved.get("revision")).intValue(),JSONUtil.toJsonStr(saved),actor);return saved;
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> publicLink(String key) {
        key(key);Map<String,Object> p=mapper.placement(key),result=new LinkedHashMap<>();result.put("contractVersion",1);result.put("placementKey",key);result.put("item",null);
        if(p==null||!Boolean.TRUE.equals(p.get("enabled"))||!Boolean.TRUE.equals(p.get("authorizationConfirmed"))||!Boolean.TRUE.equals(p.get("editorialApproved"))||!Boolean.TRUE.equals(p.get("identityConfirmed"))||p.get("articleStatus")==null||((Number)p.get("articleStatus")).intValue()!=1)return result;
        if(!Objects.equals(mapper.approvedHash(key),hash(mapper.article(Long.valueOf(p.get("essayId").toString()),false))))return result;
        Map<String,Object> item=new LinkedHashMap<>();item.put("businessArticleId",p.get("essayId"));item.put("personKey",p.get("personKey"));item.put("title",p.get("title"));item.put("state","releaseReady");item.put("authorization","confirmed");item.put("editorialReview","approved");item.put("factCheck","confirmed");item.put("identityCheck","confirmed");item.put("source",Collections.singletonMap("type","reviewed-article-placement"));item.put("evidenceRef","placement:"+key+":revision:"+p.get("revision"));item.put("publicVerified",true);result.put("item",item);return result;
    }
}
