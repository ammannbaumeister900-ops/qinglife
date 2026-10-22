package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.QlRegistration;
import com.yicai.life.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static com.yicai.life.service.QlFinanceOperationService.uuid;

/** One transaction/retry boundary and session -> order -> sorted people lock order for participant writes. */
@Service @RequiredArgsConstructor
public class QlParticipantFinanceGuard {
    private final PlatformTransactionManager tx;
    private final QlParticipantModePolicy modes;
    private final QlRegistrationPolicy seats;
    private final QlParticipantFinanceMapper mapper;
    private final QlRegistrationMapper registrations;
    public <T> T write(Supplier<T> work) {
        for(int attempt=0;;attempt++) {
            TransactionTemplate t=new TransactionTemplate(tx);t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            try{return t.execute(s->{modes.requireEnabled();return work.get();});}
            catch(ConcurrencyFailureException e){if(attempt==2)throw new CustomException("并发操作未完成，请使用原操作标识重试",409);}
            catch(org.springframework.dao.DataAccessException e){throw new CustomException("资金操作未完成，请核对资金记录后使用原标识重试",409);}
        }
    }
    public Target lock(String batch) {
        uuid(batch);Map<String,Object> snapshot=mapper.batch(batch,false);
        if(snapshot==null)throw new CustomException("订单不存在",404);
        Map<String,Object> session=seats.lockSession(snapshot.get("sessionId").toString());
        Map<String,Object> order=mapper.batch(batch,true);modes.requireParticipant(String.valueOf(order.get("mode")));
        List<QlRegistration> people=registrations.selectBatchRegistrationsForUpdate(batch);
        if(people.isEmpty())throw new CustomException("订单没有报名人",409);
        for(QlRegistration person:people)if(!Objects.equals(order.get("sessionId"),person.getSessionId()))
            throw new CustomException("订单与人员营期不一致，请先核对",409);
        return new Target(order,session,people);
    }
    public String batchFor(String id) {
        uuid(id);QlRegistration row=registrations.selectById(id);
        if(row==null)throw new CustomException("报名不存在",404);
        if(row.getBatchId()==null)throw new CustomException("历史单人报名请使用原流程",409);
        return row.getBatchId();
    }
    public QlRegistration person(Target t,String id) {
        return t.people.stream().filter(r->id.equals(r.getId())).findFirst().orElseThrow(()->new CustomException("报名人不属于本订单",409));
    }
    public void revision(Integer expected,Object actual) {
        if(expected==null||expected<0||expected.intValue()!=((Number)actual).intValue())throw new CustomException("资料已变化，请刷新后使用新操作标识",409);
    }
    public static class Target {
        final Map<String,Object> order,session;final List<QlRegistration> people;
        Target(Map<String,Object> order,Map<String,Object> session,List<QlRegistration> people){this.order=order;this.session=session;this.people=people;}
        String id(){return order.get("id").toString();}
    }
}
