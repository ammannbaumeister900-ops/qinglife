package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import com.yicai.life.mapper.QlMiniAppMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class QlCustomerIdentityServiceTest {
    @Test void rejectsDisabledAccountEvenWhenAnIdentityAlreadyExists() {
        AppUserInfoMapper users=mock(AppUserInfoMapper.class);
        QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);
        when(users.selectForIdentity(4L)).thenReturn(new AppUserInfo().setId(4L).setStatus(0));
        assertThrows(CustomException.class, () -> new QlCustomerIdentityService(users,mapper).resolve(4L));
        verifyNoInteractions(mapper);
    }
    @Test void reusesExistingBindingAfterLockingTheAccount() {
        AppUserInfoMapper users=mock(AppUserInfoMapper.class);
        QlMiniAppMapper mapper=mock(QlMiniAppMapper.class);
        when(users.selectForIdentity(4L)).thenReturn(new AppUserInfo().setId(4L).setStatus(1));
        when(mapper.selectCustomerIdByLegacyUserId(4L)).thenReturn("existing-customer");
        assertEquals("existing-customer", new QlCustomerIdentityService(users,mapper).resolve(4L));
        verify(mapper).selectCustomerIdByLegacyUserId(4L);
        verifyNoMoreInteractions(mapper);
    }
}
