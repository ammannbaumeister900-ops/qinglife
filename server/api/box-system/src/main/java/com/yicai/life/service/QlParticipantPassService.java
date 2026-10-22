package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.*;
import com.yicai.life.mapper.*;
import com.yicai.life.service.QlParticipantFinanceGuard.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import static com.yicai.life.service.QlFinanceOperationService.*;

/** Operator verification and person settlement only; existing QlPassService owns all balance/consume/void rules. */
@Service @RequiredArgsConstructor
public class QlParticipantPassService {
    private final QlParticipantFinanceGuard guard;
    private final QlParticipantFinanceMapper finance;
    private final QlParticipantRefundMapper refunds;
    private final QlParticipantPassMapper mapper;
    private final QlRegistrationMapper registrations;
    private final QlRegistrationStatusLogMapper logs;
    private final QlRegistrationPolicy seats;
    private final QlPassService passes;
    private final QlFinanceOperationService operations;

    private Target lock(String id){return guard.lock(guard.batchFor(id));}
    private void noCash(Target t,String id) {
        if(!finance.allocations(id).isEmpty()||finance.hasUnlinkedReceipt(id)||finance.hasBatchSettlement(t.id())||refunds.hasRefundExposure(id)||
            !"unpaid".equals(t.order.get("paymentStatus"))||!"pending".equals(t.order.get("settlementStatus")))
            throw new CustomException("此人存在现金、退款或整单结算事实，不能混用卡次",409);
    }
    private void ready(QlRegistration r) {
        if(r.getUnitPrice()==null||r.getUnitPrice().signum()<0)throw new CustomException("此人缺少可靠应收快照，请核对",409);
        if(!Arrays.asList("pending","waitlisted","confirmed").contains(r.getRegistrationStatus())||!"unpaid".equals(r.getPaymentStatus())||
            !"pending".equals(r.getSettlementStatus())||r.getCurrentSettlementId()!=null||r.getFinalAmount()!=null||
            r.getPassAccountId()!=null||r.getPassUnits()!=null&&r.getPassUnits()!=0||finance.hasPass(r.getId()))
            throw new CustomException("此人已处理、已取消或有未核对卡次，请刷新",409);
        if("confirmed".equals(r.getRegistrationStatus())&&!mapper.previouslyReturned(r.getId()))
            throw new CustomException("确认人员缺少可核对的原返卡轮次",409);
    }
    private void units(Integer value){if(value==null||value<1||value>65535)throw new CustomException("每人使用1至65535次且仅一个账户",400);}
    private String holder(Target t,QlRegistration r,String scope) {
        if(!Arrays.asList("self","same_order").contains(scope))throw new CustomException("核实范围无效",400);
        String owner="self".equals(scope)?r.getCustomerId():t.order.get("buyerCustomerId").toString();
        if("same_order".equals(scope)&&owner.equals(r.getCustomerId()))throw new CustomException("本人账户请使用本人核实范围",409);
        if(!mapper.activeCustomer(owner)||!mapper.activeCustomer(r.getCustomerId()))throw new CustomException("持卡人或使用人档案不可用",409);
        return owner;
    }
    private Map<String,Object> verification(String id,String registration,Target t) {
        uuid(id);Map<String,Object> v=mapper.verification(id);
        if(v==null||!registration.equals(v.get("registrationId"))||!t.id().equals(v.get("batchId")))throw new CustomException("核实记录不属于此人或此订单",409);
        return v;
    }
    private void advance(Target t,String registration,Long operator){finance.advanceRegistration(registration,operator);finance.advanceBatch(t.id());}
    public Map<String,Object> verify(String id,QlPassVerificationBo bo,Long operator) {
        uuid(bo.getOperationId());uuid(bo.getPassAccountId());units(bo.getPassUnits());
        if(bo.getRegistrationId()!=null&&!id.equals(bo.getRegistrationId()))throw new CustomException("核实对象与路径不符",400);
        if(bo.getBasis()==null||bo.getBasis().trim().isEmpty()||bo.getBasis().length()>600)throw new CustomException("请提供600字以内的运营核实依据",400);
        String basis=bo.getBasis().trim();
        return guard.write(()->{
            Target t=lock(id);QlRegistration r=guard.person(t,id);finance.allocations(id);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"pass_verify",id,operator,Arrays.asList(bo.getExpectedRevision(),bo.getPassAccountId(),bo.getPassUnits(),bo.getScope(),basis));
            if(replay!=null)return replay;
            noCash(t,id);
            guard.revision(bo.getExpectedRevision(),r.getRevision());ready(r);
            String owner=holder(t,r,bo.getScope());passes.eligible(bo.getPassAccountId(),owner);
            String verification=UUID.randomUUID().toString();mapper.verify(verification,bo.getOperationId(),id,r.getRevision()+1,t.id(),bo.getPassAccountId(),owner,r.getCustomerId(),bo.getPassUnits(),bo.getScope(),basis,operator);
            advance(t,id,operator);
            return operations.finish(bo.getOperationId(),map("verificationId",verification,"registrationId",id,"passAccountId",bo.getPassAccountId(),"holderCustomerId",owner,
                "beneficiaryCustomerId",r.getCustomerId(),"passUnits",bo.getPassUnits(),"scope",bo.getScope(),"revision",r.getRevision()+1,"state","verified","reservedUnits",0));
        });
    }
    public Map<String,Object> revokeVerification(String id,String verificationId,QlFinanceActionBo bo,Long operator) {
        uuid(bo.getOperationId());uuid(verificationId);String why=reason(bo.getReason());
        if(bo.getNoActualPaymentConfirmed()!=null)throw new CustomException("核实撤销不是退款动作",400);
        return guard.write(()->{
            Target t=lock(id);QlRegistration r=guard.person(t,id);finance.allocations(id);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"pass_verify_revoke",verificationId,operator,Arrays.asList(id,bo.getExpectedRevision(),why));
            if(replay!=null)return replay;
            guard.revision(bo.getExpectedRevision(),r.getRevision());Map<String,Object> v=verification(verificationId,id,t);
            if(((Number)v.get("used")).intValue()!=0)throw new CustomException("核实已用于扣卡，请通过原结算轮次独立返卡",409);
            if(((Number)v.get("revoked")).intValue()!=0)throw new CustomException("核实已撤销，请刷新",409);
            mapper.revokeVerification(verificationId,bo.getOperationId(),why,operator);advance(t,id,operator);
            return operations.finish(bo.getOperationId(),map("verificationId",verificationId,"registrationId",id,"state","revoked","revision",r.getRevision()+1,"reason",why,"returnedUnits",0));
        });
    }
    public Map<String,Object> settle(String id,QlSettlementBo bo,Long operator) {
        if(!"participant".equals(bo.getMode())||!"pass".equals(bo.getSettlementType())||!bo.getExtraFields().isEmpty()||
            bo.getAllocationIds()!=null||bo.getFinalAmount()!=null&&bo.getFinalAmount().signum()!=0||bo.getDifferenceConfirmed()!=null||bo.getDifferenceReason()!=null)
            throw new CustomException("卡次不能混合现金、多卡或差额字段",400);
        uuid(bo.getOperationId());uuid(bo.getVerificationId());uuid(bo.getPassAccountId());units(bo.getPassUnits());String why=reason(bo.getReason());
        return guard.write(()->{
            Target t=lock(id);QlRegistration r=guard.person(t,id);finance.allocations(id);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"pass_settle",id,operator,Arrays.asList(bo.getExpectedRevision(),bo.getVerificationId(),bo.getPassAccountId(),bo.getPassUnits(),why,bo.getNote()));
            if(replay!=null)return replay;
            noCash(t,id);guard.revision(bo.getExpectedRevision(),r.getRevision());ready(r);
            if(!Arrays.asList("open","in_progress").contains(t.session.get("configuredStatus"))||"completed".equals(t.session.get("effectiveStatus")))
                throw new CustomException("当前营期不接受卡次确认",409);
            Map<String,Object> v=verification(bo.getVerificationId(),id,t);guard.revision(r.getRevision(),v.get("revision"));
            if(((Number)v.get("revoked")).intValue()!=0||((Number)v.get("used")).intValue()!=0||!bo.getPassAccountId().equals(v.get("accountId"))||
                bo.getPassUnits().intValue()!=((Number)v.get("units")).intValue()||!r.getCustomerId().equals(v.get("beneficiaryId"))||
                !holder(t,r,v.get("scope").toString()).equals(v.get("holderId")))
                throw new CustomException("核实已失效、已使用或与账户/持卡人/使用人/次数不符",409);
            seats.requirePaidCapacity(r.getSessionId(),Collections.singletonList(id));
            // Lock the account before settlement inserts acquire FK locks, then reuse the exact consume implementation.
            passes.eligible(bo.getPassAccountId(),v.get("holderId").toString());
            String round=UUID.randomUUID().toString();Date now=new Date();
            if(registrations.insertSettlementLog(round,id,null,r.getUnitPrice(),BigDecimal.ZERO,"pass",bo.getPassUnits(),bo.getPassAccountId(),null,why,operator,now)!=1)throw new CustomException("结算轮次写入失败",500);
            mapper.bind(round,bo.getVerificationId());
            int after=passes.consume(bo.getPassAccountId(),v.get("holderId").toString(),r.getSessionId(),id,null,round,bo.getPassUnits(),why,operator,now);
            if(registrations.updateSettlementLogBalance(round,after)!=1||registrations.confirmRegistrationSettlement(id,round,BigDecimal.ZERO,"pass",bo.getPassUnits(),bo.getPassAccountId(),why,operator,now)!=1||
                mapper.confirm(id,round,r.getRevision(),operator)!=1)throw new CustomException("人员卡次确认状态已变化",409);
            status(r,why,operator,now);finance.advanceBatch(t.id());
            return operations.finish(bo.getOperationId(),map("registrationId",id,"settlementId",round,"verificationId",bo.getVerificationId(),"passAccountId",bo.getPassAccountId(),
                "holderCustomerId",v.get("holderId"),"beneficiaryCustomerId",r.getCustomerId(),"sessionId",r.getSessionId(),"passUnits",bo.getPassUnits(),"balanceAfter",after,
                "registrationStatus","confirmed","settlementStatus","confirmed","settlementType","pass","paymentStatus","unpaid","seatOccupied",true,"revision",r.getRevision()+1,"reason",why,"note",bo.getNote()));
        });
    }
    public Map<String,Object> reverse(String id,String round,QlSettlementReversalBo bo,Long operator) {
        uuid(round);uuid(bo.getOperationId());String why=reason(bo.getReason());
        if(!"participant".equals(bo.getMode())||!bo.getExtraFields().isEmpty())throw new CustomException("请明确逐人返卡模式和允许字段",400);
        return guard.write(()->{
            Target t=lock(id);QlRegistration r=guard.person(t,id);finance.allocations(id);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"pass_return",round,operator,Arrays.asList(id,bo.getExpectedRevision(),why));
            if(replay!=null)return replay;
            noCash(t,id);guard.revision(bo.getExpectedRevision(),r.getRevision());
            if(registrations.selectSettlementReversalForUpdate(round)!=null)throw new CustomException("原轮次已返还，不能重复返卡",409);
            Map<String,Object> h=registrations.selectSettlementLogForUpdate(round);
            if(h==null||!id.equals(h.get("registrationId"))||h.get("batchId")!=null||!"pass".equals(h.get("settlementType"))||
                !"confirmed".equals(r.getSettlementStatus())||!round.equals(r.getCurrentSettlementId())||!"pass".equals(r.getSettlementType())||
                !Objects.equals(r.getPassAccountId(),h.get("passAccountId"))||r.getPassUnits()==null||r.getPassUnits().intValue()!=((Number)h.get("passUnits")).intValue()||
                r.getFinalAmount()==null||r.getFinalAmount().signum()!=0||((BigDecimal)h.get("finalAmount")).signum()!=0||!"unpaid".equals(r.getPaymentStatus()))
                throw new CustomException("原结算与当前人员卡次信息不一致，禁止返还",409);
            if(h.get("passVerificationId")==null)throw new CustomException("原轮次没有可靠运营核实关联",409);
            Map<String,Object> v=verification(h.get("passVerificationId").toString(),id,t);
            if(!r.getCustomerId().equals(v.get("beneficiaryId"))||!r.getPassAccountId().equals(v.get("accountId"))||r.getPassUnits().intValue()!=((Number)v.get("units")).intValue())
                throw new CustomException("原核实与原扣次归属不符",409);
            Date now=new Date();
            QlPassService.PassReversal returned=passes.reverseConsumption(round,v.get("holderId").toString(),r.getSessionId(),id,null,r.getPassAccountId(),r.getPassUnits(),why,operator,now);
            if(registrations.insertSettlementReversal(round,id,null,(BigDecimal)h.get("finalAmount"),"pass",r.getPassAccountId(),r.getPassUnits(),returned.getOriginalLedgerId(),returned.getBalanceAfter(),why,operator,now)!=1||
                registrations.updateRegistrationSettlementToPending(id,round,operator,now)!=1)throw new CustomException("返卡审计或结算状态更新失败",500);
            finance.advanceBatch(t.id());
            return operations.finish(bo.getOperationId(),map("registrationId",id,"settlementId",round,"passAccountId",r.getPassAccountId(),"holderCustomerId",v.get("holderId"),
                "returnedUnits",r.getPassUnits(),"balanceAfter",returned.getBalanceAfter(),"registrationStatus",r.getRegistrationStatus(),"seatOccupied","confirmed".equals(r.getRegistrationStatus()),
                "settlementStatus","pending","revision",r.getRevision()+1,"reason",why));
        });
    }
    private void status(QlRegistration r,String why,Long operator,Date now) {
        QlRegistrationStatusLog log=new QlRegistrationStatusLog();log.setId(UUID.randomUUID().toString());log.setRegistrationId(r.getId());log.setStatusType("registration");
        log.setFromStatus(r.getRegistrationStatus());log.setToStatus("confirmed");log.setChangeReason(why);log.setOperatorId(operator);log.setChangedAt(now);
        if(logs.insert(log)!=1)throw new CustomException("卡次确认审计失败",500);
    }
}
