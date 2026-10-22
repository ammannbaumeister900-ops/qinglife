package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import com.yicai.common.core.redis.RedisCache;
import com.yicai.life.domain.bo.*;
import com.yicai.life.mapper.QlMiniAppMapper;
import com.yicai.life.service.impl.QlMiniAppServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QlMiniAppRegistrationQuoteTest {
    QlMiniAppMapper mapper = mock(QlMiniAppMapper.class);
    QlMiniAppRegistrationQuoteService service = new QlMiniAppRegistrationQuoteService(mapper,new QlSessionPricing(mapper),new QlSessionAdmissionPolicy());
    QlMiniAppRegistrationBo.Participant person(boolean self, String id, String phone) {
        QlMiniAppRegistrationBo.Participant p=new QlMiniAppRegistrationBo.Participant();
        p.setSelf(self);p.setCustomerId(id);p.setName("合成参与者");p.setPhone(phone);return p;
    }
    QlMiniAppQuoteBo request(QlMiniAppRegistrationBo.Participant... people) {
        QlMiniAppQuoteBo bo=new QlMiniAppQuoteBo();bo.setSessionId("s");bo.setParticipants(Arrays.asList(people));return bo;
    }
    void open() {
        Map<String,Object> s=new HashMap<>();s.put("status","open");s.put("standardPrice",new BigDecimal("3800"));s.put("returningPrice",new BigDecimal("2500"));s.put("endDate","2099-01-03");
        when(mapper.selectSessionDetail("s")).thenReturn(s);
    }
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> result) { return (List<Map<String,Object>>)result.get("participants"); }
    @Test void pricesActualOwnedParticipantsAndReturnsExactTotalWithoutWrites() {
        open();when(mapper.selectOwnedParticipant("family","buyer")).thenReturn("family");
        when(mapper.countCompletedSessions("buyer")).thenReturn(2);
        Map<String,Object> result=service.quote("buyer",request(person(true,null,""),person(false,"family","")));
        assertEquals("returning",rows(result).get(0).get("friendType"));assertEquals("new",rows(result).get(1).get("friendType"));
        assertEquals(new BigDecimal("6300"),result.get("quotedAmount"));
        verify(mapper).selectSessionDetail("s");verify(mapper).selectOwnedParticipant("family","buyer");
        verify(mapper).countCompletedSessions("buyer");verify(mapper).countCompletedSessions("family");verifyNoMoreInteractions(mapper);
    }
    @Test void newPersonDoesNotInheritBuyerHistoryOrCreateAnIdentity() {
        open();Map<String,Object> result=service.quote("buyer",request(person(false,null,"")));
        assertEquals("new",rows(result).get(0).get("friendType"));assertEquals(new BigDecimal("3800"),result.get("quotedAmount"));
        verify(mapper).selectSessionDetail("s");verifyNoMoreInteractions(mapper);
    }
    @Test void resolvesAnOwnedPhoneUsingTheExistingRegistrationBoundary() {
        open();when(mapper.selectPhoneCandidates(anyString())).thenReturn(Collections.singletonList("family"));
        when(mapper.selectOwnedParticipant("family","buyer")).thenReturn("family");when(mapper.countCompletedSessions("family")).thenReturn(1);
        assertEquals(new BigDecimal("2500"),service.quote("buyer",request(person(false,null,"19900000002"))).get("quotedAmount"));
    }
    @Test void rejectsUnownedAndAmbiguousIdentitiesInsteadOfDisclosingHistory() {
        open();assertThrows(CustomException.class,()->service.quote("buyer",request(person(false,"stranger",""))));
        when(mapper.selectPhoneCandidates(anyString())).thenReturn(Arrays.asList("a","b"));
        assertThrows(CustomException.class,()->service.quote("buyer",request(person(false,null,"19900000002"))));
        verify(mapper,never()).countCompletedSessions(anyString());
    }
    @Test void rejectsDuplicateSelfPhonesAndResolvedPeople() {
        open();assertThrows(CustomException.class,()->service.quote("buyer",request(person(true,null,""),person(true,null,""))));
        assertThrows(CustomException.class,()->service.quote("buyer",request(person(false,null,"19900000002"),person(false,null,"19900000002"))));
        when(mapper.selectOwnedParticipant("family","buyer")).thenReturn("family");
        assertThrows(CustomException.class,()->service.quote("buyer",request(person(false,"family",""),person(false,"family",""))));
    }
    @Test void rejectsClosedMissingAndOversizedRequests() {
        assertThrows(CustomException.class,()->service.quote("buyer",request(person(true,null,""))));
        open();when(mapper.selectSessionDetail("s")).thenReturn(Collections.singletonMap("status","cancelled"));
        assertThrows(CustomException.class,()->service.quote("buyer",request(person(true,null,""))));
        QlMiniAppQuoteBo tooMany=request();tooMany.setParticipants(Collections.nCopies(11,person(true,null,"")));
        assertThrows(CustomException.class,()->service.quote("buyer",tooMany));
    }
    @Test void zeroAndMissingConfiguredPricesMatchThePersistedPricingPolicy() {
        open();Map<String,Object> s=new HashMap<>();s.put("status","open");s.put("standardPrice",BigDecimal.ZERO);when(mapper.selectSessionDetail("s")).thenReturn(s);
        assertEquals(BigDecimal.ZERO,service.quote("buyer",request(person(true,null,""))).get("quotedAmount"));
        s.remove("standardPrice");assertEquals(new BigDecimal("3800"),service.quote("buyer",request(person(true,null,""))).get("quotedAmount"));
    }
    @Test void unauthenticatedFacadeNeverReachesQuoteData() {
        QlMiniAppServiceImpl facade=new QlMiniAppServiceImpl(mock(RedisCache.class),mock(QlCustomerIdentityService.class),mapper,new QlSessionPricing(mapper),mock(QlHabitPlanService.class),new QlSessionAdmissionPolicy(),new QlCommunityAccessPolicy());
        assertThrows(CustomException.class,()->facade.registrationQuote(null,request(person(true,null,""))));
        verifyNoInteractions(mapper);
    }
}
