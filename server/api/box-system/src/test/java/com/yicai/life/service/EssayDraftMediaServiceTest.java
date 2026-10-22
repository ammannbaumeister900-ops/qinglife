package com.yicai.life.service;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.Essay;
import com.yicai.life.mapper.EssayMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EssayDraftMediaServiceTest {
    @TempDir Path folder;
    String previous;EssayMapper mapper;EssayDraftMediaService service;
    byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    @BeforeEach void setup(){previous=RuoYiConfig.getProfile();new RuoYiConfig().setProfile(folder.toString());mapper=mock(EssayMapper.class);service=new EssayDraftMediaService(mapper);when(mapper.selectById(7L)).thenReturn(new Essay().setId(7L).setStatus(0));}
    @AfterEach void restore(){new RuoYiConfig().setProfile(previous);}
    MockMultipartFile image(){return new MockMultipartFile("file","original.png","image/png",png);}
    @Test void validatedOriginalBytesStayOutsidePublicAndReadBackExactly()throws Exception{
        Map<String,String> result=service.upload(7L,image());assertTrue(result.get("path").startsWith("/life/essay/7/draft-media/"));
        assertArrayEquals(png,(byte[])service.read(7L,result.get("name")).get("data"));assertFalse(Files.exists(folder.resolve("public")));
    }
    @Test void missingPublishedAndNullStateArticlesCannotUploadOrRead()throws Exception{
        for(Integer state:Arrays.asList(1,null)){when(mapper.selectById(7L)).thenReturn(new Essay().setId(7L).setStatus(state));assertThrows(CustomException.class,()->service.upload(7L,image()));assertThrows(CustomException.class,()->service.read(7L,"00000000-0000-0000-0000-000000000000.png"));}
        when(mapper.selectById(7L)).thenReturn(null);assertThrows(CustomException.class,()->service.upload(7L,image()));
    }
    @Test void forgedImageTraversalAndForeignArticlePathAreRejected()throws Exception{
        assertThrows(CustomException.class,()->service.upload(7L,new MockMultipartFile("file","x.png","image/png","not-image".getBytes())));
        assertThrows(CustomException.class,()->service.read(7L,"../secret.png"));
        Map<String,String> own=service.upload(7L,image());when(mapper.selectById(8L)).thenReturn(new Essay().setId(8L).setStatus(0));assertThrows(CustomException.class,()->service.read(8L,own.get("name")));
    }
}
