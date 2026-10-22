package com.yicai.web.controller.life;
import com.yicai.common.core.domain.entity.SysUser;
import com.yicai.common.core.domain.model.LoginUser;
import com.yicai.framework.web.service.PermissionService;
import com.yicai.life.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EssayDraftPermissionTest {
 AnnotationConfigApplicationContext context;EssayController controller;EssayDraftMediaService media;LoginUser login;
 @BeforeEach void setup(){context=new AnnotationConfigApplicationContext();context.getBeanFactory().registerSingleton("tokenService",mock(com.yicai.framework.web.service.TokenService.class));com.yicai.system.service.AccountPagePermissionService pages=mock(com.yicai.system.service.AccountPagePermissionService.class);when(pages.allowsPermission(anyLong(),anyString())).thenReturn(true);context.getBeanFactory().registerSingleton("accountPages",pages);context.register(Config.class);context.refresh();controller=context.getBean(EssayController.class);media=context.getBean(EssayDraftMediaService.class);SysUser u=new SysUser();u.setUserId(42L);login=new LoginUser(u,new HashSet<>());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,Collections.emptyList()));org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));when(context.getBean(com.yicai.framework.web.service.TokenService.class).getLoginUser(any(javax.servlet.http.HttpServletRequest.class))).thenReturn(login);}
 @AfterEach void close(){SecurityContextHolder.clearContext();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();context.close();}
 @Test void listPermissionCannotReadPrivateArticleImagesOrUpload(){login.getPermissions().add("life:essay:list");assertThrows(AccessDeniedException.class,()->controller.draftMedia(7L,"x.png"));assertThrows(AccessDeniedException.class,()->controller.uploadDraftMedia(7L,new MockMultipartFile("file",new byte[0])));verifyNoInteractions(media);}
 @Test void readPermissionIsNotUploadPermission()throws Exception{login.getPermissions().add("life:essay:query");Map<String,Object> image=new HashMap<>();image.put("mime","image/png");image.put("data",new byte[]{1});when(media.read(7L,"x.png")).thenReturn(image);assertEquals("no-store",controller.draftMedia(7L,"x.png").getHeaders().getFirst("Cache-Control"));assertThrows(AccessDeniedException.class,()->controller.uploadDraftMedia(7L,new MockMultipartFile("file",new byte[0])));}
 @Test void editPermissionDelegatesOnlyToExistingDraftService()throws Exception{login.getPermissions().add("life:essay:edit");MockMultipartFile file=new MockMultipartFile("file",new byte[0]);controller.uploadDraftMedia(7L,file);verify(media).upload(7L,file);}
 @Configuration @EnableGlobalMethodSecurity(prePostEnabled=true) static class Config{
  @Bean(name="ss") PermissionService permissions(){return new PermissionService();}
  @Bean EssayDraftMediaService media(){return mock(EssayDraftMediaService.class);}
  @Bean IEssayService articles(){return mock(IEssayService.class);}
  @Bean EssayController controller(IEssayService articles,EssayDraftMediaService media){return new EssayController(articles,media);}
 }
}
