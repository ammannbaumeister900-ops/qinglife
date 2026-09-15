package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.AppUserInfo;
import com.yicai.life.mapper.AppUserInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QlWechatLoginServiceTest {
    @Test void resolvesPersistedUserAndUpdatesOnlyLoginTime() {
        AppUserInfoMapper mapper = mock(AppUserInfoMapper.class);
        when(mapper.selectForLogin("synthetic-open-id")).thenReturn(new AppUserInfo().setId(8L).setStatus(1));
        AppUserInfo user = new QlWechatLoginService(mapper).resolveUser("synthetic-open-id");
        assertEquals(8L, user.getId());
        org.mockito.InOrder order = inOrder(mapper);
        order.verify(mapper).ensureLoginUser(eq("synthetic-open-id"), any());
        order.verify(mapper).selectForLogin("synthetic-open-id");
        order.verify(mapper).updateLoginTime(eq(8L), any());
        verify(mapper, never()).updateById(any());
    }
    @Test void databaseFailureIsNotHidden() {
        AppUserInfoMapper mapper = mock(AppUserInfoMapper.class);
        when(mapper.ensureLoginUser(eq("new-user"), any())).thenThrow(new DataIntegrityViolationException("schema"));
        assertThrows(DataIntegrityViolationException.class, () -> new QlWechatLoginService(mapper).resolveUser("new-user"));
        verify(mapper, never()).selectForLogin(any());
    }
    @Test void disabledUserCannotLogin() {
        AppUserInfoMapper mapper = mock(AppUserInfoMapper.class);
        when(mapper.selectForLogin("disabled")).thenReturn(new AppUserInfo().setId(9L).setStatus(0));
        CustomException error = assertThrows(CustomException.class, () -> new QlWechatLoginService(mapper).resolveUser("disabled"));
        assertEquals(403, error.getCode());
        verify(mapper, never()).updateLoginTime(any(), any());
    }
    @Test void invalidIdentityDoesNotTouchDatabase() {
        AppUserInfoMapper mapper=mock(AppUserInfoMapper.class);
        assertThrows(CustomException.class, () -> new QlWechatLoginService(mapper).resolveUser(" "));
        verifyNoInteractions(mapper);
    }
}
