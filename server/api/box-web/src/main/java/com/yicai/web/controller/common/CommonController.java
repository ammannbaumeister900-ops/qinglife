package com.yicai.web.controller.common;

import cn.hutool.core.util.StrUtil;
import com.yicai.common.config.RuoYiConfig;
import com.yicai.common.constant.Constants;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.utils.file.FileUploadUtils;
import com.yicai.common.utils.file.FileUtils;
import com.yicai.framework.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用请求处理
 *
 * @author ruoyi
 */
@RestController
public class CommonController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    @Autowired
    private ServerConfig serverConfig;

    /**
     * 通用下载请求
     *
     * @param fileName 文件名称
     */
    @GetMapping("common/download")
    public void fileDownload(String fileName, HttpServletResponse response) throws IOException
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new IllegalArgumentException(StrUtil.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            Path filePath = resolveInside(RuoYiConfig.getDownloadPath(), fileName);
            if (!Files.isRegularFile(filePath)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
			File file = filePath.toFile();
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
			FileUtils.writeToStream(file, response.getOutputStream());
        }
        catch (IllegalArgumentException e)
        {
            log.warn("拒绝非法下载路径: {}", fileName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        catch (IOException e)
        {
            log.error("下载文件失败: {}", fileName, e);
            if (!response.isCommitted()) response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/common/download/resource")
    public void resourceDownload(String resource, HttpServletResponse response) throws IOException
    {
        try
        {
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new IllegalArgumentException(StrUtil.format("资源文件({})非法，不允许下载。 ", resource));
            }
            if (resource == null || !resource.startsWith(Constants.RESOURCE_PREFIX + "/")) {
                throw new IllegalArgumentException("resource prefix");
            }
            String relativePath = resource.substring((Constants.RESOURCE_PREFIX + "/").length());
            Path downloadPath = resolveInside(RuoYiConfig.getProfile(), relativePath);
            if (!Files.isRegularFile(downloadPath)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            // 下载名称
            String downloadName = downloadPath.getFileName().toString();
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
			File file = downloadPath.toFile();
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeToStream(file, response.getOutputStream());
        }
        catch (IllegalArgumentException e)
        {
            log.warn("拒绝非法资源路径: {}", resource);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        catch (IOException e)
        {
            log.error("资源下载失败: {}", resource, e);
            if (!response.isCommitted()) response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    static Path resolveInside(String root, String requested)
    {
        if (StrUtil.isBlank(root) || StrUtil.isBlank(requested)) throw new IllegalArgumentException("blank path");
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path resolved = base.resolve(requested).normalize();
        if (!resolved.startsWith(base)) throw new IllegalArgumentException("outside allowed root");
        return resolved;
    }

    /**
     * 通用上传请求
     */
    @PostMapping("/common/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception
    {
        try
        {
            // 上传文件路径
            String filePath = RuoYiConfig.getProfile();
            // 上传并返回新文件名称
            String fileName = FileUploadUtils.upload(filePath, file);
            String url = RuoYiConfig.getImagePath() + fileName;
            Map<String,Object> ajax = new HashMap<>();
            ajax.put("fileName", fileName);
            ajax.put("url", url);
            if(fileName.contains(".mp4") || fileName.contains(".rmvb") || fileName.contains(".avi")){
                ajax.put("fileType", "video");
            }else if(fileName.contains(".jpg") || fileName.contains(".jpeg") || fileName.contains(".png") || fileName.contains(".gif")){
                ajax.put("fileType", "image");
            }
            return AjaxResult.success(ajax);
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }
}
