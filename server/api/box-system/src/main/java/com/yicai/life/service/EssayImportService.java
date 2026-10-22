package com.yicai.life.service;

import com.yicai.life.domain.bo.EssayImportBo;
import com.yicai.life.mapper.EssayImportMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Offline batch orchestration: one article transaction, independent failure journal, no ordinary create API. */
public class EssayImportService {
    private final EssayImportMapper mapper;
    private final TransactionTemplate transaction;
    public EssayImportService(EssayImportMapper mapper,PlatformTransactionManager manager) {
        this.mapper=mapper;transaction=new TransactionTemplate(manager);
    }
    public static final class Result {
        public final String sourceId,outcome,errorCode;
        public final Long targetId;
        public Result(String id,String state,Long target,String error){sourceId=id;outcome=state;targetId=target;errorCode=error;}
    }
    public List<Result> run(String source,String batch,List<EssayImportBo> inputs,boolean dryRun,boolean readable) {
        EssayImportPolicy.require(source!=null && source.matches("[a-z0-9][a-z0-9_.-]{0,63}"),"INVALID_SOURCE_SYSTEM");
        EssayImportPolicy.require(batch!=null && batch.matches("[a-z0-9][a-z0-9_-]{0,63}"),"INVALID_BATCH");
        EssayImportPolicy.require(inputs!=null && !inputs.isEmpty() && inputs.size()<=5,"BATCH_LIMIT");
        List<Result> results=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(EssayImportBo input:inputs) {
            String id=input==null?null:input.getSourceId();Result result;
            try {
                EssayImportPolicy.Prepared prepared=EssayImportPolicy.prepare(source,input);
                EssayImportPolicy.require(seen.add(id),"DUPLICATE_BATCH_SOURCE_ID");
                if(dryRun) {
                    Map<String,Object> old=mapper.source(source,id,false);
                    if(old==null)mapper.validateLabels(source,input.getLabels());
                    result=old==null?new Result(id,"WOULD_IMPORT",null,null):existing(id,old,prepared);
                } else result=transaction.execute(status->{
                    mapper.reserve(source,batch,input);
                    Map<String,Object> old=mapper.source(source,id,true);
                    if(old.get("essay_id")!=null)return existing(id,old,prepared);
                    // Sort lock acquisition, preserve the input association order separately.
                    Map<String,Long> labelIds=new HashMap<>();
                    List<EssayImportBo.LabelInput> ordered=new ArrayList<>(input.getLabels());
                    ordered.sort(Comparator.comparing(EssayImportBo.LabelInput::getSourceId));
                    for(EssayImportBo.LabelInput label:ordered)labelIds.put(label.getSourceId(),mapper.label(source,batch,label));
                    long target=mapper.insert(prepared,readable);
                    for(EssayImportBo.LabelInput label:input.getLabels())mapper.link(target,labelIds.get(label.getSourceId()));
                    mapper.complete(source,id,target,prepared);
                    return new Result(id,"IMPORTED",target,null);
                });
            } catch(EssayImportPolicy.Invalid invalid) {result=new Result(id,"REJECTED",null,invalid.code);}
            catch(RuntimeException failure) {result=new Result(id,"FAILED",null,"PERSISTENCE_FAILURE");}
            // A failure journal is not rolled back with the failed article. Never log SQL/credentials/content.
            if(!dryRun)mapper.attempt(batch,source,id,result.outcome,result.errorCode);
            results.add(result);
        }
        return results;
    }
    private Result existing(String id,Map<String,Object> old,EssayImportPolicy.Prepared p) {
        Long target=old.get("essay_id")==null?null:((Number)old.get("essay_id")).longValue();
        if(target==null)return new Result(id,"CONFLICT",null,"INCOMPLETE_MAPPING");
        if(!p.sourceHash.equals(old.get("source_hash")))return new Result(id,"CONFLICT",target,"SOURCE_CHANGED");
        if(!Objects.equals(old.get("target_hash"),mapper.targetHash(target)))return new Result(id,"CONFLICT",target,"TARGET_CHANGED");
        return new Result(id,"UNCHANGED",target,null);
    }
}
