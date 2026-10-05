package com.yicai.life.service;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.impl.QlMiniAppServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QlMiniAppContactServiceTest {
    private Map<String,Object> contact(List<Map<String,Object>> rows) {
        QlMiniAppMapper mapper = mock(QlMiniAppMapper.class);
        when(mapper.selectContactConfig()).thenReturn(rows);
        return new QlMiniAppServiceImpl(mock(RedisCache.class), mock(QlCustomerIdentityService.class), mapper,
                mock(QlSessionPricing.class), mock(QlHabitPlanService.class), new QlSessionAdmissionPolicy()).contact();
    }
    private Map<String,Object> row(String key, String value) {
        Map<String,Object> result = new HashMap<>(); result.put("configKey", key); result.put("configValue", value); return result;
    }
    @Test void missingConfigurationPublishesTheApprovedPublicContact() {
        Map<String,Object> result = contact(Collections.emptyList());
        assertEquals("桃子", result.get("name")); assertEquals("qinglife2014", result.get("wechat")); assertEquals(true, result.get("configured"));
    }
    @Test void BlankOrNullLegacyValuesDoNotHideTheContact() {
        Map<String,Object> result = contact(Arrays.asList(row("qinglife.contact.name", "  "), row("qinglife.contact.wechat", null)));
        assertEquals("桃子", result.get("name")); assertEquals("qinglife2014", result.get("wechat"));
        assertEquals("qinglife2014", contact(Collections.singletonList(row("qinglife.contact.wechat", "  "))).get("wechat"));
    }
    @Test void NonblankOperationalConfigurationStillOverridesTheDefault() {
        Map<String,Object> result = contact(Arrays.asList(row("qinglife.contact.name", " 桃子客服 "), row("qinglife.contact.wechat", " qinglife_support ")));
        assertEquals("桃子客服", result.get("name")); assertEquals("qinglife_support", result.get("wechat")); assertEquals(true, result.get("configured"));
    }
}
