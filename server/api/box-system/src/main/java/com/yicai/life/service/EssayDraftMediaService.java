package com.yicai.life.service;

import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.UploadContentValidator;
import com.yicai.life.domain.Essay;
import com.yicai.life.mapper.EssayMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Images for existing article drafts. This directory is never a public static resource. */
@Service
public class EssayDraftMediaService {
    private final EssayMapper essays;
    public EssayDraftMediaService(EssayMapper essays) { this.essays = essays; }
    private void requireDraft(Long id) {
        Essay essay = id == null ? null : essays.selectById(id);
        if (essay == null || !Integer.valueOf(0).equals(essay.getStatus()))
            throw new CustomException("仅未公开文章可使用草稿图片", 404);
    }
    private Path directory(Long id, boolean create) throws IOException {
        Path profile = Paths.get(RuoYiConfig.getProfile()).toRealPath();
        Path root = profile.resolve("article-drafts"), folder = root.resolve(id.toString());
        if (create) Files.createDirectories(folder);
        if (!Files.isDirectory(folder) || !root.toRealPath().startsWith(profile) ||
                !folder.toRealPath().startsWith(root.toRealPath()))
            throw new CustomException("草稿图片不存在", 404);
        return folder.toRealPath();
    }
    public Map<String,String> upload(Long id, MultipartFile file) throws IOException {
        requireDraft(id);
        if (file == null || !("image/png".equals(file.getContentType()) || "image/jpeg".equals(file.getContentType())))
            throw new CustomException("草稿图片仅支持 PNG/JPEG", 400);
        UploadContentValidator.validateCommonMedia(file);
        String name = UUID.randomUUID().toString() + ("image/png".equals(file.getContentType()) ? ".png" : ".jpg");
        Files.write(directory(id, true).resolve(name), file.getBytes(), StandardOpenOption.CREATE_NEW);
        Map<String,String> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("path", "/life/essay/" + id + "/draft-media/" + name);
        return result;
    }
    public Map<String,Object> read(Long id, String name) throws IOException {
        requireDraft(id);
        if (name == null || !name.matches("[a-f0-9-]{36}\\.(png|jpg)"))
            throw new CustomException("草稿图片不存在", 404);
        Path folder = directory(id, false), image = folder.resolve(name);
        if (!Files.isRegularFile(image) || !image.toRealPath().startsWith(folder))
            throw new CustomException("草稿图片不存在", 404);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("mime", name.endsWith(".png") ? "image/png" : "image/jpeg");
        result.put("data", Files.readAllBytes(image));
        return result;
    }
}
