package com.yicai.life.service;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class QlSessionCoverTest {
    @TempDir Path temp;
    @Test void rendersDistinctReadablePeriodImagesWithoutChangingTheTemplate() throws Exception {
        BufferedImage a=ImageIO.read(new ByteArrayInputStream(QlSessionCover.render(501)));
        BufferedImage b=ImageIO.read(new ByteArrayInputStream(QlSessionCover.render(Integer.MAX_VALUE)));
        assertEquals(1200,a.getWidth());assertEquals(600,a.getHeight());
        assertEquals(a.getRGB(100,100),b.getRGB(100,100));
        assertFalse(java.util.Arrays.equals(QlSessionCover.render(501),QlSessionCover.render(502)));
        assertThrows(CustomException.class,()->QlSessionCover.render(0));
        assertThrows(CustomException.class,()->QlSessionCover.render(null));
    }
    @Test void threeDigitAndLongPeriodsStayInsideTheCompactNumberSlot() throws Exception {
        BufferedImage source=ImageIO.read(QlSessionCover.class.getResourceAsStream("/images/session-cover-template-v3.png"));
        BufferedImage blank=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=blank.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source,0,0,1200,600,null);
        } finally {g.dispose();}
        for(int number:new int[]{1,501,999,Integer.MAX_VALUE}) {
            BufferedImage rendered=ImageIO.read(new ByteArrayInputStream(QlSessionCover.render(number)));
            int changed=0;
            for(int y=180;y<400;y++) for(int x=0;x<1200;x++) {
                if(blank.getRGB(x,y)==rendered.getRGB(x,y)) continue;
                changed++;
                assertTrue(x>=500 && x<700,"Period "+number+" overlaps the surrounding template text at "+x);
            }
            assertTrue(changed>0,"Period number must remain visible");
        }
    }
    @Test void storesReusablePublicFilesAndRecognizesOnlyTheReservedAutomaticUrls() throws Exception {
        RuoYiConfig config=new RuoYiConfig();String profile=RuoYiConfig.getProfile(),images=RuoYiConfig.getImagePath();
        config.setProfile(temp.toString());config.setImagePath("https://example.invalid/api/profile/public/");
        try {
            String file=QlSessionCover.ensure(501);Path path=temp.resolve(file);
            assertTrue(Files.isRegularFile(path));
            assertTrue(file.startsWith("public/session-covers/v3-"));
            Files.setLastModifiedTime(path,FileTime.fromMillis(12345000));
            assertEquals(file,QlSessionCover.ensure(501));assertEquals(12345000,Files.getLastModifiedTime(path).toMillis());
            assertNotEquals(file,QlSessionCover.ensure(502));
            assertTrue(QlSessionCover.isAutomatic(""));assertTrue(QlSessionCover.isAutomatic(file));
            assertTrue(QlSessionCover.isAutomatic("https://example.invalid/api/profile/public/session-covers/v1-501.png"));
            assertTrue(QlSessionCover.isAutomatic("https://example.invalid/api/profile/public/session-covers/v2-501.png"));
            assertTrue(QlSessionCover.isAutomatic("https://example.invalid/api/profile/public/session-covers/v3-501.png"));
            assertFalse(QlSessionCover.isAutomatic("https://other.invalid/session-covers/v1-501.png"));
            assertFalse(QlSessionCover.isAutomatic("public/photo.png"));
            assertFalse(QlSessionCover.isAutomatic("public/session-covers/../../private.png"));
        } finally {config.setProfile(profile);config.setImagePath(images);}
    }
}
