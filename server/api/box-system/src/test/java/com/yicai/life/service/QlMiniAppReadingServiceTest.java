package com.yicai.life.service;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.impl.QlMiniAppServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QlMiniAppReadingServiceTest {
    private QlMiniAppServiceImpl service(QlMiniAppMapper mapper) {
        return new QlMiniAppServiceImpl(mock(RedisCache.class),mock(QlCustomerIdentityService.class),mapper,
                mock(QlSessionPricing.class),mock(QlHabitPlanService.class),new QlSessionAdmissionPolicy(), new QlCommunityAccessPolicy());
    }
    @Test void detailReusesListStoryNormalizationAndLeavesTheHtmlUnchanged() {
        QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);
        Map<String,Object> row=new HashMap<>();
        row.put("id","1");row.put("isStory",1);row.put("cover","https://example.invalid/portrait.jpg");
        row.put("content","<h2>结构</h2><p><strong>原文</strong></p>");
        when(mapper.selectPublicReading(1L)).thenReturn(row);
        Map<String,Object> detail=service(mapper).readingDetail(1L);
        assertEquals(Boolean.TRUE,detail.get("isStory"));
        assertEquals("https://example.invalid/portrait.jpg",detail.get("cover"));
        assertEquals("<h2>结构</h2><p><strong>原文</strong></p>",detail.get("content"));
        assertEquals("1",detail.get("id"));
    }
    @Test void storedLegacyHtmlIsCleanedOnReadWithoutUpdatingTheDatabase() {
        QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);
        Map<String,Object> row=new HashMap<>();row.put("id","7");
        row.put("content","<h2>真实标题</h2><script>bad()</script><img src='/media/old.jpg' onerror='bad()'>");
        when(mapper.selectPublicReading(7L)).thenReturn(row);
        String html=(String)service(mapper).readingDetail(7L).get("content");
        assertTrue(html.contains("真实标题"));assertTrue(html.contains("/media/old.jpg"));
        assertFalse(html.contains("script"));assertFalse(html.contains("onerror"));
        verify(mapper).selectPublicReading(7L);verifyNoMoreInteractions(mapper);
    }
    @Test void falseAndMissingFlagsDoNotInventStoryMembership() {
        for(Object flag:Arrays.asList(0,Boolean.FALSE,null)) {
            QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);
            Map<String,Object> row=new HashMap<>();row.put("id","2");row.put("isStory",flag);
            when(mapper.selectPublicReading(2L)).thenReturn(row);
            assertEquals(Boolean.FALSE,service(mapper).readingDetail(2L).get("isStory"));
        }
    }
    @Test void unpublishedOrMissingArticleKeepsTheExistingFailureBoundary() {
        QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);
        when(mapper.selectPublicReading(99L)).thenReturn(null);
        assertThrows(CustomException.class,()->service(mapper).readingDetail(99L));
    }
}


