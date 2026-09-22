package com.yicai.common.utils.file;

import com.yicai.common.config.RuoYiConfig;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared storage contract for authenticated public-media upload endpoints. */
public final class PublicMediaUpload {
    private PublicMediaUpload() { }
    public static Map<String,String> upload(MultipartFile file) throws IOException {
        UploadContentValidator.validateCommonMedia(file);
        String name = "public/" + FileUploadUtils.upload(RuoYiConfig.getProfile() + "/public", file);
        Map<String,String> result = new LinkedHashMap<>();
        result.put("fileName", name);
        result.put("url", RuoYiConfig.getImagePath().replaceAll("/+$", "") + "/" + name);
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        result.put("fileType", lower.endsWith(".mp4") || lower.endsWith(".rmvb") || lower.endsWith(".avi") ? "video" : "image");
        return result;
    }
}