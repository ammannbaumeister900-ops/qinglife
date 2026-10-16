package com.yicai.life.service;

import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.EssayBo;
import com.yicai.life.mapper.*;
import com.yicai.life.service.impl.EssayServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EssayDraftSafetyTest {
    EssayMapper mapper; EssayLabelMapper labels; AppUserInfoMapper users; AppEssayUserMapper unread; EssayServiceImpl service;
    @BeforeEach void setup() {
        org.apache.ibatis.builder.MapperBuilderAssistant b=new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(),"draft-test");
        b.setCurrentNamespace("draft-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(b,AppUserInfo.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(b,EssayLabel.class);
        SysUser user=new SysUser();user.setUserId(42L);user.setUserName("test-editor");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(user,Collections.emptySet()),null));
        mapper=mock(EssayMapper.class);labels=mock(EssayLabelMapper.class);users=mock(AppUserInfoMapper.class);unread=mock(AppEssayUserMapper.class);service=new EssayServiceImpl();
        ReflectionTestUtils.setField(service,"baseMapper",mapper);ReflectionTestUtils.setField(service,"essayLabelMapper",labels);ReflectionTestUtils.setField(service,"userInfoMapper",users);ReflectionTestUtils.setField(service,"essayUserMapper",unread);
        when(mapper.insert(any(Essay.class))).thenReturn(1);when(mapper.updateById(any(Essay.class))).thenReturn(1);when(labels.selectList(any())).thenReturn(Collections.emptyList());
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    EssayBo input(Integer status) {EssayBo b=new EssayBo();b.setId(7L);b.setStatus(status);b.setTitle("Original title");b.setAuthorName("Original byline");b.setLabels(new Long[0]);b.setContent("<p>Original story</p>");return b;}
    @Test void explicitDraftAndMissingStatusNeverEnumerateUsersOrCreateUnread() {
        for(Integer status:Arrays.asList(0,null)) {
            assertTrue(service.insertByBo(input(status)));
        }
        ArgumentCaptor<Essay> saved=ArgumentCaptor.forClass(Essay.class);verify(mapper,times(2)).insert(saved.capture());
        for(Essay row:saved.getAllValues()){assertEquals(0,row.getStatus());assertEquals(0,row.getHomeFeatured());assertEquals("Original byline",row.getAuthorName());assertEquals(42L,row.getAuthor());}
        verifyNoInteractions(users,unread);
    }
    @Test void explicitlyEnabledArticleRetainsExistingUnreadFanout() {
        AppUserInfo a=new AppUserInfo();a.setId(1L);AppUserInfo b=new AppUserInfo();b.setId(2L);
        when(users.selectList(any())).thenReturn(Arrays.asList(a,b));
        assertTrue(service.insertByBo(input(1)));verify(users).selectList(any());
        verify(unread).insertAll(argThat(rows->rows.size()==2));
    }
    @Test void draftEditWithResetFlagStillCannotResetExistingUnread() {
        EssayBo b=input(0);b.setUpdateNotRead(true);assertTrue(service.updateByBo(b));verifyNoInteractions(users,unread);
    }
}
