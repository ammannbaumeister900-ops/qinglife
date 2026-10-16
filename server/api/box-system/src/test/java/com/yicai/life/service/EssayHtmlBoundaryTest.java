package com.yicai.life.service;

import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.life.domain.Essay;
import com.yicai.life.domain.bo.EssayBo;
import com.yicai.life.mapper.*;
import com.yicai.life.service.impl.EssayServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EssayHtmlBoundaryTest {
    @Test void insertAndUpdateCleanBeforeMapperWithoutChangingTheInputOrIdentity() {
        // Initialize MyBatis metadata only; no datasource or Spring application starts.
        org.apache.ibatis.builder.MapperBuilderAssistant builder = new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "isolated-test");
        builder.setCurrentNamespace("isolated-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(builder, com.yicai.life.domain.AppUserInfo.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(builder, com.yicai.life.domain.EssayLabel.class);
        SysUser user=new SysUser();user.setUserId(42L);user.setUserName("isolated-editor");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(user,Collections.emptySet()),null));
        try {
            for(boolean insert:new boolean[]{true,false}) {
                EssayMapper mapper=mock(EssayMapper.class);EssayLabelMapper labels=mock(EssayLabelMapper.class);AppUserInfoMapper users=mock(AppUserInfoMapper.class);
                EssayServiceImpl service=new EssayServiceImpl();ReflectionTestUtils.setField(service,"baseMapper",mapper);ReflectionTestUtils.setField(service,"essayLabelMapper",labels);ReflectionTestUtils.setField(service,"userInfoMapper",users);
                when(mapper.insert(any(Essay.class))).thenReturn(1);when(mapper.updateById(any(Essay.class))).thenReturn(1);
                when(labels.selectList(any())).thenReturn(Collections.emptyList());when(users.selectList(any())).thenReturn(Collections.emptyList());
                EssayBo bo=new EssayBo();bo.setId(7L);bo.setLabels(new Long[0]);bo.setStatus(1);bo.setTitleUrl(insert?null:"");bo.setContent("<h2>原文</h2><img src='/media/legal.jpg' onerror='bad()'><script>bad()</script>");
                assertTrue(insert?service.insertByBo(bo):service.updateByBo(bo));
                ArgumentCaptor<Essay> saved=ArgumentCaptor.forClass(Essay.class);
                if(insert)verify(mapper).insert(saved.capture());else verify(mapper).updateById(saved.capture());
                assertTrue(saved.getValue().getContent().contains("原文"));assertTrue(saved.getValue().getContent().contains("/media/legal.jpg"));assertFalse(saved.getValue().getContent().contains("onerror"));assertFalse(saved.getValue().getContent().contains("script"));
                assertEquals(7L,saved.getValue().getId());assertEquals(Integer.valueOf(1),saved.getValue().getStatus());assertEquals(insert?null:"",saved.getValue().getTitleUrl());assertTrue(bo.getContent().contains("<script>"));
            }
        } finally {SecurityContextHolder.clearContext();}
    }
}
