package com.yicai.web.controller.common;

import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.UploadContentValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class UploadContentValidatorTest {
    private byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test void acceptedPhotoMustMatchDeclaredFormat() throws Exception {
        byte[] image = png();
        UploadContentValidator.validateCommonMedia(
                new MockMultipartFile("file", "photo.png", "image/png", image));
        assertArrayEquals(image, UploadContentValidator.decodeInterviewImage(
                "image/png", Base64.getEncoder().encodeToString(image)));
        assertThrows(CustomException.class, () -> UploadContentValidator.decodeInterviewImage(
                "image/jpeg", Base64.getEncoder().encodeToString(image)));
        assertThrows(CustomException.class, () -> UploadContentValidator.validateCommonMedia(
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", image)));
    }

    @Test void rejectsOversizedAndDisguisedMedia() throws Exception {
        assertThrows(CustomException.class, () -> UploadContentValidator.validateCommonMedia(
                new MockMultipartFile("file", "sheet.xlsx", "application/vnd.ms-excel", new byte[]{1})));
        assertThrows(CustomException.class, () -> UploadContentValidator.validateCommonMedia(
                new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[12])));
        assertThrows(CustomException.class, () -> UploadContentValidator.validateCommonMedia(
                new MockMultipartFile("file", "photo.png", "image/png", new byte[10 * 1024 * 1024 + 1])));
        assertThrows(CustomException.class, () -> UploadContentValidator.decodeInterviewImage(
                "image/png", new String(new char[2_800_001]).replace('\0', 'A')));
    }
}