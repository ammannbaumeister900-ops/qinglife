package com.yicai.common.utils.file;

import com.yicai.common.exception.CustomException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;

/** Server-side limits and content checks for local media and staff interview photos. */
public final class UploadContentValidator {
    private static final long MAX_PIXELS = 16_000_000L;
    private UploadContentValidator() { }

    public static void validateCommonMedia(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null ||
                file.getOriginalFilename().length() > FileUploadUtils.DEFAULT_FILE_NAME_LENGTH)
            throw new CustomException("请选择有效文件", 400);
        String name = file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        String mime = file.getContentType();
        if (isImageExtension(extension)) {
            if (file.getSize() > 10L * 1024 * 1024) throw new CustomException("图片不能超过10MB", 400);
            String expected = "jpg".equals(extension) ? "jpeg" : extension;
            if (!("image/" + expected).equals(mime) &&
                    !("jpeg".equals(expected) && "image/jpg".equals(mime)))
                throw new CustomException("图片类型不匹配", 400);
            validateImage(file.getBytes(), expected);
        } else if ("mp4".equals(extension) || "avi".equals(extension) || "rmvb".equals(extension)) {
            if (file.getSize() > 50L * 1024 * 1024) throw new CustomException("视频不能超过50MB", 400);
            byte[] header = new byte[12];
            try (java.io.InputStream in = file.getInputStream()) {
                if (in.read(header) < ("rmvb".equals(extension) ? 4 : 12))
                    throw new CustomException("视频内容无效", 400);
            }
            boolean valid = "mp4".equals(extension) ? ascii(header, 4, "ftyp") :
                    "avi".equals(extension) ? ascii(header, 0, "RIFF") && ascii(header, 8, "AVI ") :
                    ascii(header, 0, ".RMF");
            if (!valid) throw new CustomException("视频内容与扩展名不符", 400);
        } else {
            throw new CustomException("只允许上传图片或视频", 400);
        }
    }

    public static byte[] decodeInterviewImage(String mime, String data) {
        if (!Arrays.asList("image/jpeg", "image/png", "image/webp").contains(mime))
            throw new CustomException("图片类型不支持", 400);
        if (data == null || data.length() > 2_800_000)
            throw new CustomException("图片不能超过2MB", 400);
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(data); }
        catch (IllegalArgumentException e) { throw new CustomException("图片无效", 400); }
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024)
            throw new CustomException("图片不能超过2MB", 400);
        try { validateImage(bytes, mime.substring(6)); }
        catch (IOException e) { throw new CustomException("图片无法读取", 400); }
        return bytes;
    }

    private static boolean isImageExtension(String extension) {
        return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp").contains(extension);
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++)
            if (bytes[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    private static void validateImage(byte[] bytes, String expected) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new CustomException("图片内容无效", 400);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new CustomException("图片内容无效", 400);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (!expected.equalsIgnoreCase(reader.getFormatName()) ||
                        reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0 ||
                        (long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS)
                    throw new CustomException("图片类型或尺寸无效", 400);
                reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}