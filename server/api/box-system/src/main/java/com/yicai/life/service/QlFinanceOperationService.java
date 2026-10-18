package com.yicai.life.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yicai.common.exception.CustomException;
import com.yicai.life.mapper.QlParticipantFinanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** Request identity survives failed work; its completed result commits atomically with resource changes. */
@Service @RequiredArgsConstructor
public class QlFinanceOperationService {
    private final QlParticipantFinanceMapper mapper;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    public Map<String,Object> begin(String id,String action,String target,Long operator,Object payload) {
        uuid(id);
        if(operator==null)throw new CustomException("操作身份无效",403);
        String fingerprint=hash(Arrays.asList(action,target,operator,payload));
        TransactionTemplate claim=new TransactionTemplate(transactions);
        claim.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        claim.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        claim.execute(s->{check(mapper.operation(id,action,fingerprint,target,operator),action,target,operator,fingerprint);return true;});
        // Reacquire under the financial transaction: one writer, exact replay, no stranded "in progress" lock.
        Map<String,Object> record=mapper.operation(id,action,fingerprint,target,operator);
        check(record,action,target,operator,fingerprint);
        if(record.get("resultJson")==null)return null;
        try {return json.readValue(record.get("resultJson").toString(),new TypeReference<Map<String,Object>>(){});}
        catch(java.io.IOException e){throw new IllegalStateException("操作结果无法读取",e);}
    }
    private void check(Map<String,Object> record,String action,String target,Long operator,String fingerprint) {
        if(!action.equals(record.get("action"))||!target.equals(record.get("targetId"))||
            !operator.equals(((Number)record.get("operatorId")).longValue())||
            !fingerprint.equals(record.get("requestHash")))
            throw new CustomException("操作标识已用于不同内容或对象",409);
    }
    public Map<String,Object> finish(String id,Map<String,Object> result) { mapper.result(id,encode(result));return result; }
    public String hash(Object value) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    public static void uuid(String id) {
        if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new CustomException("请提供有效UUID操作或资源标识",400);
    }
    public static String money(BigDecimal value,int digits) {
        if(value==null||value.signum()<=0||value.scale()>2||value.compareTo(BigDecimal.TEN.pow(digits))>=0)
            throw new CustomException("金额须为正数，最多2位小数且不超过约定上限",400);
        return value.setScale(2).toPlainString();
    }
    public static String reason(String value) {
        if(value==null||value.trim().isEmpty()||value.length()>500)throw new CustomException("请填写有效核对依据（最多500字）",400);
        return value.trim();
    }
    public static Map<String,Object> map(Object... entries) {
        Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)out.put(entries[i].toString(),entries[i+1]);return out;
    }
}
