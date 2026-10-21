package com.yicai.life.service;

import com.yicai.common.core.redis.RedisCache;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.bo.QlFriendAssessmentBo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QlCustomerPhoneServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final QlCustomerPhoneService phones = new QlCustomerPhoneService(mock(RedisCache.class), mock(QlCustomerIdentityService.class), db);

    private void customerExists() {
        when(db.queryForList(startsWith("SELECT id FROM ql_customer WHERE"), eq("A")))
                .thenReturn(Collections.singletonList(Collections.singletonMap("id", "A")));
    }

    @Test void bindsAnUnclaimedNumberAsVerifiedPrimary() {
        customerExists();
        when(db.queryForList(startsWith("SELECT DISTINCT customer_id"), eq(String.class), anyString(), eq("A"))).thenReturn(Collections.emptyList());
        assertEquals("138****1234", phones.bindWechatPhone("A", "13800001234"));
        verify(db).update(startsWith("INSERT INTO ql_customer_identifier"), anyString(), eq("A"), eq("13800001234"), anyString(), eq("138****1234"),
                eq(QlCustomerPhoneService.WECHAT_SCOPE), eq("verified"), any(), any(), any());
    }

    @Test void numberOnAnotherDossierIsKeptAsConflictInsteadOfMerged() {
        customerExists();
        when(db.queryForList(startsWith("SELECT DISTINCT customer_id"), eq(String.class), anyString(), eq("A"))).thenReturn(Collections.singletonList("B"));
        when(db.update(startsWith("UPDATE ql_customer_identifier SET identifier_value"), any(Object[].class))).thenReturn(1);
        phones.bindWechatPhone("A", "13800001234");
        verify(db).update(startsWith("UPDATE ql_customer_identifier SET identifier_value"), eq("13800001234"), eq("138****1234"),
                eq(QlCustomerPhoneService.WECHAT_SCOPE), eq("conflict"), any(), eq("A"), anyString());
        verify(db, never()).update(startsWith("INSERT"), any(Object[].class));
        verify(db, never()).update(contains("customer_id=?"), eq("B"));
    }

    @Test void rejectsNumbersThatAreNotMainlandMobiles() {
        assertEquals(400, assertThrows(CustomException.class, () -> phones.bindWechatPhone("A", "+1 415 555 0100")).getCode());
        verifyNoInteractions(db);
    }

    @Test void loginWithoutAuthorizedPhoneIsRefused() {
        when(db.queryForList(anyString(), eq(String.class), eq("A"), eq(QlCustomerPhoneService.WECHAT_SCOPE))).thenReturn(Collections.emptyList());
        assertEquals(403, assertThrows(CustomException.class, () -> phones.requireWechatPhone("A")).getCode());
    }

    @Test void assessmentPhoneMustBeTheAuthorizedNumberBeforeAnythingIsWritten() {
        RedisCache redis = mock(RedisCache.class);
        when(redis.getCacheObject("appToken:t")).thenReturn(7L);
        QlCustomerIdentityService identities = mock(QlCustomerIdentityService.class);
        when(identities.resolve(7L)).thenReturn("A");
        QlCustomerPhoneService scoped = mock(QlCustomerPhoneService.class);
        when(scoped.requireWechatPhone("A")).thenReturn("13800001234");
        JdbcTemplate store = mock(JdbcTemplate.class);
        QlFriendAssessmentService assessments = new QlFriendAssessmentService(redis, identities, store, scoped);
        QlFriendAssessmentBo bo = QlFriendAssessmentValidatorTest.valid();
        bo.setPhone("13900009999");
        assertEquals(400, assertThrows(CustomException.class, () -> assessments.submit("t", bo)).getCode());
        verifyNoInteractions(store);
    }
}
