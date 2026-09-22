package com.yicai.web.controller.common;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.exception.CustomException;
import com.yicai.web.controller.system.SysOssController;
import com.yicai.system.service.ISysOssService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PublicMediaUploadTest {
    @TempDir Path temp;
    @Test void bothEditorEndpointsWriteValidatedMediaIntoPublicDirectory() throws Exception {
        RuoYiConfig config=new RuoYiConfig();
        String oldProfile=RuoYiConfig.getProfile(),oldImages=RuoYiConfig.getImagePath();
        config.setProfile(temp.toString());config.setImagePath("https://example.invalid/test-api/profile/");
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
            MockMultipartFile file=new MockMultipartFile("file","photo.png","image/png",bytes.toByteArray());
            SysOssController local=new SysOssController(mock(ISysOssService.class));
            Map<?,?> a=(Map<?,?>)local.localUpload(file).getData();
            Map<?,?> b=(Map<?,?>)new CommonController().uploadFile(file).getData();
            for(Map<?,?> result:new Map[]{a,b}) {
                String name=String.valueOf(result.get("fileName"));
                assertTrue(name.startsWith("public/"));
                assertEquals("https://example.invalid/test-api/profile/"+name,result.get("url"));
                assertArrayEquals(bytes.toByteArray(),Files.readAllBytes(temp.resolve(name)));
            }
            MockMultipartFile disguised=new MockMultipartFile("file","photo.png","image/png","<script>bad</script>".getBytes("UTF-8"));
            assertThrows(CustomException.class,()->local.localUpload(disguised));
            assertThrows(CustomException.class,()->new CommonController().uploadFile(disguised));
            try(java.util.stream.Stream<Path> files=Files.list(temp.resolve("public"))){assertEquals(2,files.count());}
        } finally {config.setProfile(oldProfile);config.setImagePath(oldImages);}
    }
}