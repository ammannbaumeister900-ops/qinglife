package com.yicai.life.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlPublicationReviewBo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Validates bounded business references and retains conclusions in the existing audit log.
 * Record existence is not identity/guardian proof: accountable operators must inspect the
 * underlying facts. Missing special-purpose evidence must remain deferred. */
@Service
@RequiredArgsConstructor
public class QlReflectionPublicationReviewService {
    private final JdbcTemplate db;

    public String prepare(Map<String,Object> reflection, QlPublicationReviewBo review, Long operator,
                          Set<String> readableTypes, String displayText) {
        if(review==null)throw new CustomException("请更新后台并补充发布核对依据",409);
        if(operator==null)throw new CustomException("缺少审核人员身份",403);
        if(!Integer.valueOf(1).equals(review.getVersion())
            ||!Arrays.asList("ready","deferred").contains(review.getDecision()))
            throw new CustomException("发布核对版本或结论无效",400);
        boolean ready="ready".equals(review.getDecision());
        String summary=text(review.getBasisSummary(),600,true);
        String safety=text(review.getContentSafetyConclusion(),300,ready);
        String identity=text(review.getIdentityConclusion(),300,ready);
        String authorization=review.getAuthorizationConclusion();
        if(authorization!=null&&!Arrays.asList("satisfied","not_applicable","insufficient").contains(authorization))
            throw new CustomException("必要授权结论无效",400);
        if(ready&&(!Arrays.asList("satisfied","not_applicable").contains(authorization)
            ||summary.codePointCount(0,summary.length())<10||identity.codePointCount(0,identity.length())<10
            ||safety.codePointCount(0,safety.length())<10))
            throw new CustomException("依据不足，请记录核对事实并暂缓发布",409);
        List<QlPublicationReviewBo.Reference> refs=review.getReferences();
        if(refs==null)refs=Collections.emptyList();
        if(refs.size()>5||ready&&refs.isEmpty())throw new CustomException("请提供一至五项可追溯业务依据",409);
        String customer=Objects.toString(reflection.get("customerId"),"");
        if(customer.isEmpty())throw new CustomException("内容归属无效",409);
        List<Map<String,Object>> references=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        boolean consent=false,objectiveFact=false;
        // Deterministic evidence lock order across requests.
        List<QlPublicationReviewBo.Reference> ordered=new ArrayList<>(refs);
        if(ordered.contains(null))throw new CustomException("业务依据无效",400);
        ordered.sort(Comparator.comparing(r->Objects.toString(r.getType(),"")+":"+Objects.toString(r.getId(),"")));
        for(QlPublicationReviewBo.Reference ref:ordered) {
            String type=ref.getType(),id=ref.getId();
            if(type==null||id==null||!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                ||!seen.add(type+":"+id))throw new CustomException("业务依据无效或重复",400);
            if(readableTypes==null||!readableTypes.contains(type))throw new CustomException("无权核对该业务依据",403);
            Map<String,Object> source=reference(type,id,customer);
            Map<String,Object> saved=new LinkedHashMap<>();
            saved.put("type",type);saved.put("id",id);
            // Hash only the bounded verification metadata, never copy health/identity originals.
            String sourceHash=DigestUtil.sha256Hex(JSONUtil.toJsonStr(new TreeMap<>(source)));
            if(ref.getSourceHash()!=null&&!sourceHash.equals(ref.getSourceHash()))throw new CustomException("已核对的业务依据发生变化，请重新审核",409);
            saved.put("sourceHash",sourceHash);
            if(source.get("revision")!=null)saved.put("sourceRevision",source.get("revision"));
            for(String time:Arrays.asList("updatedAt","submittedAt","occurredAt","grantedAt"))
                if(source.get(time)!=null){saved.put("sourceRecordedAt",source.get(time));break;}
            references.add(saved);
            consent|="consent".equals(type);
            objectiveFact|="interaction".equals(type)&&"objective_fact".equals(source.get("contentType"));
        }
        if(ready) {
            List<Map<String,Object>> current=db.queryForList(
                "SELECT r.id FROM ql_registration r JOIN ql_customer c ON c.id=r.customer_id JOIN ql_session s ON s.id=r.session_id WHERE r.id=? AND r.customer_id=? AND r.session_id=? AND r.registration_status='confirmed' AND c.deleted_at IS NULL AND s.status NOT IN ('draft','cancelled') FOR UPDATE",
                reflection.get("registrationId"),customer,reflection.get("sessionId"));
            if(current.isEmpty())throw new CustomException("参与归属或期次已变化，暂不能发布",409);
            // Generic self-consent alone cannot represent a special authorization.
            if("satisfied".equals(authorization)&&(!consent||!objectiveFact))
                throw new CustomException("特殊授权须有有效用途授权及已确认业务事实，依据不足请暂缓",409);
        }
        Map<String,Object> saved=new LinkedHashMap<>();
        saved.put("version",1);saved.put("decision",review.getDecision());
        saved.put("contentSafetyConclusion",safety);saved.put("identityConclusion",identity);
        saved.put("authorizationConclusion",authorization);saved.put("basisSummary",summary);
        saved.put("references",references);saved.put("customerId",customer);
        saved.put("registrationId",reflection.get("registrationId"));saved.put("sessionId",reflection.get("sessionId"));
        saved.put("reviewedRevision",reflection.get("revision"));
        saved.put("consentVersion",reflection.get("consentVersion"));saved.put("consentAt",reflection.get("consentAt"));
        saved.put("displayHash",DigestUtil.sha256Hex(Objects.toString(displayText,"")));
        return JSONUtil.toJsonStr(saved);
    }

    private Map<String,Object> reference(String type,String id,String customer) {
        return reference(type,id,customer,true);
    }

    private Map<String,Object> reference(String type,String id,String customer,boolean lock) {
        String sql;
        switch(type) {
            case "registration":sql="SELECT id,registration_status AS status,session_id AS sessionId,updated_at AS updatedAt FROM ql_registration WHERE id=? AND customer_id=? AND registration_status='confirmed' FOR UPDATE";break;
            case "assessment":sql="SELECT id,revision,source,submitted_at AS submittedAt,updated_at AS updatedAt FROM ql_friend_assessment WHERE id=? AND customer_id=? FOR UPDATE";break;
            case "interaction":sql="SELECT id,content_type AS contentType,summary,operator_id AS operatorId,occurred_at AS occurredAt,updated_at AS updatedAt FROM ql_interaction WHERE id=? AND customer_id=? AND confirmation_status='confirmed' AND visibility_scope='internal' AND sensitivity_level=0 FOR UPDATE";break;
            case "consent":sql="SELECT c.id,c.policy_version AS policyVersion,c.source,c.recorded_by AS recordedBy,c.evidence_attachment_id AS evidenceAttachmentId,c.granted_at AS grantedAt FROM ql_consent_record c JOIN ql_attachment a ON a.id=c.evidence_attachment_id AND a.customer_id=c.customer_id WHERE c.id=? AND c.customer_id=? AND c.consent_type='brand_reuse' AND c.status='granted' AND c.granted_at IS NOT NULL AND c.withdrawn_at IS NULL AND a.scan_status='clean' AND a.visibility_scope='internal' AND (a.retention_until IS NULL OR a.retention_until>=CURRENT_DATE()) FOR UPDATE";break;
            default:throw new CustomException("不支持的业务依据类型",400);
        }
        List<Map<String,Object>> rows=db.queryForList(lock?sql:sql.replace(" FOR UPDATE",""),id,customer);
        if(rows.size()!=1)throw new CustomException("业务依据不存在、不属于本人或已失效，请暂缓发布",409);
        Map<String,Object> source=rows.get(0);
        if("consent".equals(type)&&(source.get("recordedBy")==null||source.get("evidenceAttachmentId")==null))
            throw new CustomException("特殊授权缺少可追溯线下记录，请暂缓发布",409);
        return source;
    }

    private String text(String raw,int max,boolean required) {
        String value=raw==null?"":raw.trim();
        if(required&&value.isEmpty()||value.codePointCount(0,value.length())>max)
            throw new CustomException("请填写必要核对结论，勿附证件或完整私人资料",400);
        return value;
    }


    public List<Map<String,Object>> availableReferences(String customer,Set<String> readableTypes) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(String type:Arrays.asList("registration","assessment","interaction","consent")) {
            if(!readableTypes.contains(type))continue;
            String sql;
            switch(type) {
                case "registration":sql="SELECT id,DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS recordedAt FROM ql_registration WHERE customer_id=? AND registration_status='confirmed' ORDER BY updated_at DESC LIMIT 50";break;
                case "assessment":sql="SELECT id,source,DATE_FORMAT(submitted_at,'%Y-%m-%d %H:%i:%s') AS recordedAt FROM ql_friend_assessment WHERE customer_id=? ORDER BY submitted_at DESC LIMIT 50";break;
                case "interaction":sql="SELECT id,content_type AS contentType,summary,DATE_FORMAT(occurred_at,'%Y-%m-%d %H:%i:%s') AS recordedAt FROM ql_interaction WHERE customer_id=? AND confirmation_status='confirmed' AND visibility_scope='internal' AND sensitivity_level=0 ORDER BY occurred_at DESC LIMIT 50";break;
                default:sql="SELECT c.id,c.policy_version AS policyVersion,c.recorded_by AS recordedBy,c.evidence_attachment_id AS evidenceAttachmentId,DATE_FORMAT(c.granted_at,'%Y-%m-%d %H:%i:%s') AS recordedAt FROM ql_consent_record c JOIN ql_attachment a ON a.id=c.evidence_attachment_id AND a.customer_id=c.customer_id WHERE c.customer_id=? AND c.consent_type='brand_reuse' AND c.status='granted' AND c.withdrawn_at IS NULL AND c.granted_at IS NOT NULL AND c.recorded_by IS NOT NULL AND a.scan_status='clean' AND a.visibility_scope='internal' AND (a.retention_until IS NULL OR a.retention_until>=CURRENT_DATE()) ORDER BY c.granted_at DESC LIMIT 50";
            }
            for(Map<String,Object> row:db.queryForList(sql,customer)) {
                Map<String,Object> item=new LinkedHashMap<>(row);item.put("type",type);
                result.add(item);
            }
        }
        return result;
    }

    public Map<String,Object> history(String id,Set<String> readableTypes) {
        List<Map<String,Object>> owners=db.queryForList("SELECT customer_id AS customerId,registration_id AS registrationId,session_id AS sessionId,revision,status,consent_version AS consentVersion,consent_at AS consentAt,share_consent AS consent,COALESCE(display_note,shared_note) AS displayNote FROM ql_camp_reflection WHERE id=?",id);
        if(owners.isEmpty())throw new CustomException("心声不存在",404);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("customerId",owners.get(0).get("customerId"));
        result.put("revision",owners.get(0).get("revision"));result.put("status",owners.get(0).get("status"));
        result.put("availableReferences",availableReferences(String.valueOf(owners.get(0).get("customerId")),readableTypes));
        if(readableTypes.contains("registration"))result.put("registrationId",owners.get(0).get("registrationId"));
        List<Map<String,Object>> rows=db.queryForList("SELECT l.action,l.revision,l.operator_id AS operatorId,u.nick_name AS operatorName,DATE_FORMAT(l.created_at,'%Y-%m-%d %H:%i:%s') AS createdAt,l.publication_review AS publicationReview FROM ql_camp_reflection_log l LEFT JOIN sys_user u ON u.user_id=l.operator_id WHERE l.reflection_id=? ORDER BY l.revision DESC,l.created_at DESC,l.id DESC LIMIT 100",id);
        List<Map<String,Object>> output=new ArrayList<>();
        for(Map<String,Object> row:rows) {
            Map<String,Object> copy=new LinkedHashMap<>(row);
            Object raw=copy.get("publicationReview");
            if(raw!=null) {
                cn.hutool.json.JSONObject review=JSONUtil.parseObj(raw.toString());
                boolean allowed=true;
                cn.hutool.json.JSONArray refs=review.getJSONArray("references");
                if(refs!=null)for(Object ref:refs)
                    if(!readableTypes.contains(JSONUtil.parseObj(ref).getStr("type")))allowed=false;
                if(allowed) {
                    // Never leak a registration ID without its existing read permission.
                    if(!readableTypes.contains("registration"))review.remove("registrationId");
                    copy.put("publicationReview",review);
                } else {copy.put("publicationReview",null);copy.put("basisRestricted",true);}
            }
            output.add(copy);
        }
        for(Map<String,Object> item:output) {
            if(item.get("publicationReview")==null&&!Boolean.TRUE.equals(item.get("basisRestricted")))continue;
            if(item.get("publicationReview")!=null) {
                cn.hutool.json.JSONObject review=JSONUtil.parseObj(item.get("publicationReview"));
                if(reusable(owners.get(0),review))result.put("reusableReview",review);
            }
            break;
        }
        result.put("rows",output);return result;
    }

    private boolean reusable(Map<String,Object> owner,cn.hutool.json.JSONObject review) {
        if(!Integer.valueOf(1).equals(owner.get("consent"))||!"ready".equals(review.getStr("decision"))
            ||!Objects.equals(owner.get("customerId"),review.getStr("customerId"))
            ||!Objects.equals(owner.get("registrationId"),review.getStr("registrationId"))
            ||!Objects.equals(owner.get("sessionId"),review.getStr("sessionId"))
            ||!Objects.equals(owner.get("consentVersion"),review.getStr("consentVersion"))
            ||owner.get("consentAt")==null
            ||!DigestUtil.sha256Hex(Objects.toString(owner.get("displayNote"),"")).equals(review.getStr("displayHash")))return false;
        try {
            Object at=owner.get("consentAt");
            if(at instanceof Date ? review.getDate("consentAt")==null||((Date)at).getTime()!=review.getDate("consentAt").getTime() : !at.toString().equals(review.getStr("consentAt")))return false;
            cn.hutool.json.JSONArray refs=review.getJSONArray("references");
            if(refs==null||refs.isEmpty())return false;
            for(Object raw:refs) {
                cn.hutool.json.JSONObject ref=JSONUtil.parseObj(raw);
                Map<String,Object> source=reference(ref.getStr("type"),ref.getStr("id"),String.valueOf(owner.get("customerId")),false);
                if(!DigestUtil.sha256Hex(JSONUtil.toJsonStr(new TreeMap<>(source))).equals(ref.getStr("sourceHash")))return false;
            }
            return true;
        } catch(CustomException error) {return false;}
    }
}
