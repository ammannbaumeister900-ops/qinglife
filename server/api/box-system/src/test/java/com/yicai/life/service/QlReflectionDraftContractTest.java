package com.yicai.life.service;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.life.domain.bo.QlCampReflectionBo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;

class QlReflectionDraftContractTest {
    @Test void existingPrivateActionDoesNotTouchThePublishedSnapshotAndSubmitSavesBothInOneTransaction() {
        for(String action:Arrays.asList("private","submit")) {
            RedisCache redis=mock(RedisCache.class);QlCustomerIdentityService identity=mock(QlCustomerIdentityService.class);JdbcTemplate db=mock(JdbcTemplate.class);
            when(redis.getCacheObject("appToken:isolated")).thenReturn("42");when(identity.resolve(42L)).thenReturn("customer-A");
            Map<String,Object> session=new HashMap<>();session.put("sessionId","s");
            Map<String,Object> owner=new HashMap<>();owner.put("registrationStatus","confirmed");owner.put("sessionStatus","in_progress");owner.put("startDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());owner.put("endDate","2099-01-03");owner.put("sessionId","s");owner.put("authorName","小禾");
            Map<String,Object> old=new HashMap<>();old.put("id","f");old.put("revision",3);old.put("status","published");
            when(db.queryForList(startsWith("SELECT session_id"),eq("reg"),eq("customer-A"))).thenReturn(Collections.singletonList(session));
            when(db.queryForList(startsWith("SELECT r.id"),eq("customer-A"),eq("s"),eq("reg"))).thenReturn(Collections.singletonList(owner));
            when(db.queryForList(startsWith("SELECT id,revision,status"),eq("reg"),eq("before"))).thenReturn(Collections.singletonList(old));
            QlCampReflectionBo bo=new QlCampReflectionBo();bo.setRegistrationId("reg");bo.setPhase("before");bo.setNote("同一份原稿");bo.setRevision(3);bo.setAction(action);bo.setAnonymous(false);bo.setShareConsent("submit".equals(action));
            new QlCampReflectionService(redis,identity,db,null,mock(QlExperienceOriginalService.class), new QlCommunityAccessPolicy(), new QlReflectionPublicationReviewService(db)).save("isolated",bo);
            verify(db).update(startsWith("UPDATE ql_camp_reflection SET draft_note="),eq("同一份原稿"),eq(0),eq(4),any(Date.class),eq("f"));
            if("private".equals(action))verify(db,never()).update(startsWith("UPDATE ql_camp_reflection SET shared_note="),any(Object[].class));
            else verify(db).update(startsWith("UPDATE ql_camp_reflection SET shared_note="),eq("同一份原稿"),eq(0),eq("小禾"),eq(QlCampReflectionService.CONSENT_VERSION),any(Date.class),any(Date.class),eq("f"));
        }
    }
}
