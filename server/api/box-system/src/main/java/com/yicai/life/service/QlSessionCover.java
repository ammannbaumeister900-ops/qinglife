package com.yicai.life.service;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.PublicMediaUpload;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

/** One public PNG template; only the session number changes. No external font is required. */
public final class QlSessionCover {
    private static final String PREFIX="public/session-covers/v3-";
    private QlSessionCover() { }
    public static boolean isAutomatic(String value) {
        if(value==null || value.trim().isEmpty()) return true;
        String relative=value;
        String urlPrefix=PublicMediaUpload.url("public/session-covers/");
        if(value.startsWith(urlPrefix)) relative="public/session-covers/"+value.substring(urlPrefix.length());
        return relative.matches("public/session-covers/v[123]-[0-9]+\\.png");
    }
    public static byte[] render(Integer number) {
        if(number==null || number<1) throw new CustomException("请填写有效的期次编号",400);
        try(InputStream source=QlSessionCover.class.getResourceAsStream("/images/session-cover-template-v3.png")) {
            if(source==null) throw new IOException("Session cover template is missing");
            BufferedImage template=ImageIO.read(source);
            BufferedImage image=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(template,0,0,1200,600,null);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                // Compact slot sized for three digits between the template glyphs. Longer periods shrink to fit.
                String label=number.toString();int size=100;
                Font font=new Font(Font.SANS_SERIF,Font.BOLD,size);
                while(g.getFontMetrics(font).stringWidth(label)>200) font=font.deriveFont((float)--size);
                g.setFont(font);g.setColor(new Color(62,87,73));
                FontMetrics metrics=g.getFontMetrics();
                g.drawString(label,(1200-metrics.stringWidth(label))/2,285+(metrics.getAscent()-metrics.getDescent())/2);
            } finally { g.dispose(); }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(!ImageIO.write(image,"png",bytes)) throw new IOException("PNG encoder unavailable");
            return bytes.toByteArray();
        } catch(IOException e) { throw new CustomException("封面生成失败，请重试"); }
    }
    public static String ensure(Integer number) {
        byte[] bytes=render(number);
        String relative=PREFIX+number+".png";
        Path target=Paths.get(RuoYiConfig.getProfile(),relative).toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
            if(!Files.isRegularFile(target)) {
                Path temporary=Files.createTempFile(target.getParent(),"cover-",".tmp");
                try { Files.write(temporary,bytes);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temporary); }
            }
            return relative;
        } catch(IOException e) { throw new CustomException("封面保存失败，请重试"); }
    }
}
