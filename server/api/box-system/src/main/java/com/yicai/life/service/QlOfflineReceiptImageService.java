package com.yicai.life.service;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.UploadContentValidator;
import com.yicai.life.domain.bo.QlOfflineReceiptBo;
import com.yicai.life.mapper.QlOfflineReceiptImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.security.MessageDigest;

/** Private receipt images are captured atomically with the immutable receipt fact. */
@Service @RequiredArgsConstructor
public class QlOfflineReceiptImageService {
    private final QlOfflineReceiptImageMapper mapper;
    public List<byte[]> validate(List<QlOfflineReceiptBo.Image> images) {
        List<byte[]> result=new ArrayList<>();
        if(images!=null) {if(images.size()>3)throw new CustomException("最多上传3张图片",400);
            for(QlOfflineReceiptBo.Image image:images) {
                if(image==null || !Arrays.asList("image/jpeg","image/png").contains(image.getMime()))throw new CustomException("请选择 JPG / PNG 图片",400);
                result.add(UploadContentValidator.decodeInterviewImage(image.getMime(),image.getData()));
            }
        }
        return result;
    }
    public void save(String receipt,List<QlOfflineReceiptBo.Image> images,List<byte[]> bytes) {
        for(int i=0;i<bytes.size();i++)if(mapper.insert(UUID.randomUUID().toString(),receipt,i,images.get(i).getMime(),sha(bytes.get(i)),bytes.get(i))!=1)
            throw new CustomException("附件保存失败",500);
    }
    public void checkRetry(String receipt,List<QlOfflineReceiptBo.Image> images,List<byte[]> bytes) {
        // A receipt-id retry need not re-upload immutable stored images.
        if(bytes.isEmpty())return;
        List<Map<String,Object>> old=mapper.list(receipt);
        if(old.size()!=bytes.size())throw new CustomException("原收款附件不能覆盖",409);
        for(int i=0;i<bytes.size();i++)if(!sha(bytes.get(i)).equals(old.get(i).get("sha")) || !images.get(i).getMime().equals(old.get(i).get("mime")))
            throw new CustomException("原收款附件不能覆盖",409);
    }
    public List<Map<String,Object>> list(String receipt){return mapper.list(receipt);}
    public Map<String,Object> image(String registration,String image) {
        Map<String,Object> result=mapper.image(registration,image);
        if(result==null)throw new CustomException("附件不存在或不属于该报名",404);
        return result;
    }
    private String sha(byte[] bytes) {
        try {StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
