package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.*;
import com.yicai.life.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static com.yicai.life.service.QlFinanceOperationService.*;
import com.yicai.life.service.QlParticipantFinanceGuard.Target;

/** Only participant cash capture, allocation and confirmation. No batch broadcasts, refunds or pass writes. */
@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class QlParticipantFinanceService {
    private final PlatformTransactionManager tx;
    private final QlParticipantModePolicy modes;
    private final QlRegistrationPolicy seats;
    private final QlParticipantFinanceMapper mapper;
    private final QlRegistrationMapper registrations;
    private final QlTransactionMapper transactions;
    private final QlOfflineReceiptMapper receipts;
    private final QlRegistrationStatusLogMapper logs;
    private final QlOfflineReceiptImageService images;
    private final QlFinanceOperationService operations;
    private final QlParticipantFinanceGuard guard;
    private final QlParticipantRefundMapper refunds;
    private final QlParticipantRefundService refundService;
    private final QlParticipantPassMapper passMapper;

    private <T> T write(Supplier<T> work) {
        return guard.write(work);
    }
    private Target lock(String batch) {
        return guard.lock(batch);
    }
    private String batchFor(String id) {
        return guard.batchFor(id);
    }
    private QlRegistration person(Target t,String id) {
        return guard.person(t,id);
    }
    private void pending(QlRegistration r) {
        if(!Arrays.asList("pending","waitlisted").contains(r.getRegistrationStatus())||!"unpaid".equals(r.getPaymentStatus())||
            !"pending".equals(r.getSettlementStatus())||r.getCurrentSettlementId()!=null||r.getFinalAmount()!=null)
            throw new CustomException("此人已处理或存在未核对结算，请刷新",409);
        if(r.getPassAccountId()!=null||r.getPassUnits()!=null&&r.getPassUnits()>0||"pass".equals(r.getSettlementType())||mapper.hasPass(r.getId()))
            throw new CustomException("此人存在卡次来源，不能混用现金",409);
        if(mapper.hasUnlinkedReceipt(r.getId()))throw new CustomException("存在未关联订单的历史到账，请人工核对",409);
        if(refunds.hasRefundExposure(r.getId()))throw new CustomException("此人已有退款或预留，不能重新分配或确认旧报名",409);
    }
    private void revision(Integer expected,Object actual) {
        guard.revision(expected,actual);
    }
    private void noBatchSettlement(Target t) {
        if(!"pending".equals(t.order.get("settlementStatus"))||!"unpaid".equals(t.order.get("paymentStatus"))||mapper.hasBatchSettlement(t.id()))
            throw new CustomException("逐人订单混入整单结算事实，请人工核对",409);
    }
    private QlTransaction receipt(Target t,String id) {
        uuid(id);QlTransaction r=receipts.lockReceipt(id);
        if(r==null||!t.id().equals(r.getRegistrationBatchId())||!t.order.get("sessionId").equals(r.getSessionId())||
            r.getRegistrationId()!=null||!"session".equals(r.getTransactionType())||!"operator_entry".equals(r.getRecordSource())||!"paid".equals(r.getStatus()))
            throw new CustomException("收款不存在、不属于此订单或尚未核实到账",409);
        return r;
    }
    public Map<String,Object> capture(String id,QlOfflineReceiptBo bo,Long operator) {
        if(bo==null||!Boolean.TRUE.equals(bo.getCaptureOnly())||!Boolean.TRUE.equals(bo.getReceivedConfirmed()))throw new CustomException("请明确核实实际到账并选择仅登记到账",400);
        uuid(bo.getRequestId());String amount=money(bo.getAmount(),8),evidence=reason(bo.getEvidence());
        if(!Arrays.asList("cash","wechat_scan","alipay_scan","transfer","other").contains(bo.getPaymentMethod())||
            bo.getReceiptReference()!=null&&bo.getReceiptReference().length()>100||bo.getReceiptId()!=null||bo.getCorrectionReason()!=null||
            bo.getExpectedRevision()!=null||Boolean.TRUE.equals(bo.getPartialPayment())||Boolean.TRUE.equals(bo.getFinalSettlementConfirmed())||
            bo.getDifferenceReason()!=null||Boolean.TRUE.equals(bo.getDefer())||!bo.getExtraFields().isEmpty())
            throw new CustomException("逐人到账不可使用整单结清、修正或卡次字段",400);
        List<byte[]> bytes=images.validate(bo.getAttachments());
        List<Object> imageKeys=new ArrayList<>();for(int i=0;i<bytes.size();i++)imageKeys.add(map("mime",bo.getAttachments().get(i).getMime(),"sha",operations.hash(Base64.getEncoder().encodeToString(bytes.get(i)))));
        String reference=bo.getReceiptReference()==null?null:bo.getReceiptReference().trim();
        return write(()->{
            Target t=lock(batchFor(id));
            Map<String,Object> replay=operations.begin(bo.getRequestId(),"capture",id,operator,Arrays.asList(amount,bo.getPaymentMethod(),reference,evidence,imageKeys));
            if(replay!=null)return replay;
            if(receipts.byRequest(bo.getRequestId())!=null||reference!=null&&!reference.isEmpty()&&receipts.byReference(reference)!=null)
                throw new CustomException("此收款回单或标识已登记，不能重复记账",409);
            Date now=new Date();QlTransaction r=new QlTransaction();String receiptId=UUID.randomUUID().toString();
            r.setId(receiptId);r.setRegistrationBatchId(t.id());r.setCustomerId(t.order.get("buyerCustomerId").toString());r.setSessionId(t.order.get("sessionId").toString());
            r.setTransactionType("session");r.setAmount(bo.getAmount());r.setPaymentMethod(bo.getPaymentMethod());r.setStatus("paid");r.setTransactionAt(now);
            r.setRecordSource("operator_entry");r.setReceiptRequestId(bo.getRequestId());r.setExternalTransactionNo(reference==null||reference.isEmpty()?null:reference);
            r.setRemark(evidence);r.setExpectedAmount((BigDecimal)t.order.get("quotedAmount"));r.setFinalSettlementConfirmed(false);r.setPartialPayment(false);
            r.setOperatorId(operator);r.setRevision(0);r.setCreatedAt(now);r.setUpdatedAt(now);
            if(transactions.insert(r)!=1)throw new CustomException("到账记录写入失败",500);
            images.save(receiptId,bo.getAttachments(),bytes);
            if(receipts.audit(UUID.randomUUID().toString(),receiptId,null,"paid","逐人模式：已核实实际到账，尚未分配",operator,now)!=1)throw new CustomException("到账审计失败",500);
            mapper.advanceBatch(t.id());
            return operations.finish(bo.getRequestId(),map("receiptId",receiptId,"actualReceived",amount,"receiptRevision",0,"allocationState","unallocated","unallocatedAmount",amount));
        });
    }
    public Map<String,Object> allocate(String batch,QlReceiptAllocationBo bo,Long operator) {
        if(bo==null||bo.getLines()==null||bo.getLines().isEmpty()||bo.getLines().size()>50)throw new CustomException("请提供1至50条人员分配",400);
        String why=reason(bo.getReason());uuid(bo.getOperationId());uuid(bo.getReceiptId());
        List<QlReceiptAllocationBo.Line> lines=new ArrayList<>(bo.getLines());
        Set<String> ids=new HashSet<>();List<Object> normalized=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
        for(QlReceiptAllocationBo.Line line:lines) {
            if(line==null)throw new CustomException("分配行不能为空",400);
            uuid(line.getRegistrationId());if(!ids.add(line.getRegistrationId()))throw new CustomException("同一次分配不可重复报名人",400);
            money(line.getAmount(),10);total=total.add(line.getAmount());
        }
        lines.sort(Comparator.comparing(QlReceiptAllocationBo.Line::getRegistrationId));
        for(QlReceiptAllocationBo.Line line:lines)normalized.add(map("registrationId",line.getRegistrationId(),"amount",money(line.getAmount(),10)));
        final BigDecimal requested=total;
        try {return write(()->{
            Target t=lock(batch);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"allocate",batch,operator,Arrays.asList(bo.getReceiptId(),bo.getExpectedRevision(),bo.getExpectedReceiptRevision(),normalized,why));
            if(replay!=null)return replay;
            noBatchSettlement(t);revision(bo.getExpectedRevision(),t.order.get("revision"));
            for(QlReceiptAllocationBo.Line line:lines)pending(person(t,line.getRegistrationId()));
            QlTransaction fact=receipt(t,bo.getReceiptId());revision(bo.getExpectedReceiptRevision(),fact.getRevision());
            BigDecimal available=fact.getAmount().subtract(mapper.allocated(fact.getId()));
            if(requested.compareTo(available)>0)throw new CustomException("分配金额超过该笔实际到账可用余额，到账事实已保留",409);
            List<Object> created=new ArrayList<>();int index=0;
            for(QlReceiptAllocationBo.Line line:lines) {
                String allocation=UUID.randomUUID().toString();mapper.allocation(allocation,bo.getOperationId(),++index,fact.getId(),line.getRegistrationId(),line.getAmount(),why);
                mapper.advanceRegistration(line.getRegistrationId(),operator);created.add(map("allocationId",allocation,"registrationId",line.getRegistrationId(),"amount",money(line.getAmount(),10)));
            }
            mapper.advanceReceipt(fact.getId());mapper.advanceBatch(batch);
            if(receipts.audit(UUID.randomUUID().toString(),fact.getId(),"paid","paid","逐人分配："+requested+"；"+why,operator,new Date())!=1)throw new CustomException("分配审计失败",500);
            return operations.finish(bo.getOperationId(),map("allocations",created,"receiptRevision",fact.getRevision()+1,"unallocatedAmount",available.subtract(requested).setScale(2).toPlainString(),"revision",((Number)t.order.get("revision")).intValue()+1));
        });}catch(RuntimeException error){auditAllocationFailure(batch,bo.getReceiptId(),bo.getOperationId(),operator,error);throw error;}
    }
    private void auditAllocationFailure(String batch,String receiptId,String operation,Long operator,RuntimeException error) {
        // Separate from the rolled-back allocation. Never alter amounts, revisions or another order's receipt.
        try {write(()->{
            Target t=lock(batch);QlTransaction fact=receipt(t,receiptId);
            String why=error instanceof CustomException?error.getMessage():"资金分配异常，请人工核对";
            if(why.length()>200)why=why.substring(0,200);
            receipts.audit(UUID.randomUUID().toString(),fact.getId(),"paid","paid","逐人分配未完成；操作 "+operation+"；"+why,operator,new Date());return true;
        });}catch(RuntimeException unavailable){log.warn("Participant allocation failure audit unavailable for operation {}",operation);}
    }
    public Map<String,Object> settle(String id,QlSettlementBo bo,Long operator) {
        if(bo==null||!"participant".equals(bo.getMode())||!"money".equals(bo.getSettlementType())||bo.getPassUnits()!=null||bo.getPassAccountId()!=null||bo.getVerificationId()!=null||!bo.getExtraFields().isEmpty())
            throw new CustomException("本阶段只支持单一现金来源的逐人结算",400);
        String amount=money(bo.getFinalAmount(),10),why=reason(bo.getReason());uuid(bo.getOperationId());
        if(bo.getAllocationIds()==null||bo.getAllocationIds().isEmpty()||bo.getAllocationIds().size()>100)throw new CustomException("请提供1至100条到账分配依据",400);
        List<String> selected=new ArrayList<>(bo.getAllocationIds());for(String a:selected)uuid(a);
        if(new HashSet<>(selected).size()!=selected.size())throw new CustomException("分配依据不可重复",400);Collections.sort(selected);
        return write(()->{
            Target t=lock(batchFor(id));QlRegistration r=person(t,id);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"settle",id,operator,Arrays.asList(bo.getExpectedRevision(),selected,amount,bo.getDifferenceConfirmed(),bo.getDifferenceReason(),why,bo.getNote()));
            if(replay!=null)return replay;
            noBatchSettlement(t);pending(r);revision(bo.getExpectedRevision(),r.getRevision());
            if(!Arrays.asList("open","in_progress").contains(t.session.get("configuredStatus"))||"completed".equals(t.session.get("effectiveStatus")))
                throw new CustomException("当前期次不接受报名确认，到账和分配已保留",409);
            if(r.getUnitPrice()==null||r.getUnitPrice().signum()<0)throw new CustomException("此人缺少可靠应收快照，请人工核对",409);
            if(r.getUnitPrice().compareTo(bo.getFinalAmount())!=0) {
                if(!Boolean.TRUE.equals(bo.getDifferenceConfirmed()))throw new CustomException("最终金额与此人应收不一致，须二次确认差额",409);
                reason(bo.getDifferenceReason());
            }
            List<Map<String,Object>> allocations=mapper.allocations(id);
            BigDecimal sum=BigDecimal.ZERO;List<String> actual=new ArrayList<>();
            for(Map<String,Object> a:allocations) {
                if(!"allocate".equals(a.get("entryType"))||((Number)a.get("reversed")).intValue()!=0||((Number)a.get("bound")).intValue()!=0||
                    !t.id().equals(a.get("batchId"))||!"paid".equals(a.get("receiptStatus"))||!"session".equals(a.get("transactionType")))
                    throw new CustomException("存在已处理或异常分配，请人工核对",409);
                actual.add(a.get("id").toString());sum=sum.add((BigDecimal)a.get("amount"));
            }
            Collections.sort(actual);
            if(!actual.equals(selected)||sum.compareTo(bo.getFinalAmount())!=0)throw new CustomException("须关联此人全部有效分配，且合计等于最终结清金额",409);
            seats.requirePaidCapacity(r.getSessionId(),Collections.singletonList(id));
            String round=UUID.randomUUID().toString();Date now=new Date();String note=why;
            if(registrations.insertSettlementLog(round,id,null,r.getUnitPrice(),bo.getFinalAmount(),"money",null,null,null,note,operator,now)!=1||
                registrations.confirmRegistrationSettlement(id,round,bo.getFinalAmount(),"money",null,null,note,operator,now)!=1||
                mapper.confirmPerson(id,round,operator,r.getRevision())!=1)throw new CustomException("人员结算状态已变化",409);
            for(String allocation:selected)mapper.fund(round,allocation);
            status(r,"registration",r.getRegistrationStatus(),"confirmed",why,operator,now);status(r,"payment","unpaid","paid",why,operator,now);
            mapper.advanceBatch(t.id());
            return operations.finish(bo.getOperationId(),map("registrationId",id,"settlementId",round,"finalAmount",amount,"reason",why,"differenceReason",bo.getDifferenceReason(),"registrationStatus","confirmed","paymentStatus","paid","seatOccupied",true,"revision",r.getRevision()+1));
        });
    }
    public Map<String,Object> finance(String batch) {
        TransactionTemplate read=new TransactionTemplate(tx);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(s->{
            uuid(batch);Map<String,Object> order=mapper.batch(batch,false);if(order==null)throw new CustomException("订单不存在",404);
            boolean participant="participant".equals(order.get("mode"));
            if(!participant)return map("mode","legacy_batch","legacyOrder",order,"writeEnabled",false);
            List<Map<String,Object>> people=mapper.participants(batch),facts=mapper.receipts(batch);
            BigDecimal gross=BigDecimal.ZERO,allocated=BigDecimal.ZERO;int confirmed=0,cancelled=0;
            for(Map<String,Object> r:people) {
                boolean seat="confirmed".equals(r.get("registrationStatus"));r.put("seatOccupied",seat);
                r.put("fundingSource","pass".equals(r.get("settlementType"))?"pass":((BigDecimal)r.get("allocatedAmount")).signum()>0?"money":"none");
                if(seat)confirmed++;if("cancelled".equals(r.get("registrationStatus")))cancelled++;
            }
            for(Map<String,Object> fact:facts) {
                BigDecimal amount=(BigDecimal)fact.get("amount"),used=(BigDecimal)fact.get("allocatedAmount");
                fact.put("unallocatedAmount",amount.subtract(used));fact.put("allocationState",used.signum()==0?"unallocated":used.compareTo(amount)==0?"allocated":"partial");
                if("paid".equals(fact.get("status"))) {gross=gross.add(amount);allocated=allocated.add(used);}
            }
            Map<String,Object> refundTotals=refundService.decorate(people);
            List<Map<String,Object>> usage=passMapper.history(batch);
            for(Map<String,Object> person:people) {
                List<Map<String,Object>> history=new ArrayList<>();
                for(Map<String,Object> entry:usage)if(person.get("id").equals(entry.get("registrationId")))history.add(entry);
                person.put("passUsage",history);
                if("cancelled".equals(person.get("registrationStatus"))) {
                    try {
                        seats.requireNoActiveRegistration(person.get("customerId").toString(),person.get("sessionId").toString());
                        person.put("rebookingHint","原报名已取消，资金与卡次已处理；请新建报名，仍须检查期次、价格及名额");
                    } catch(CustomException blocked) {person.put("rebookingHint",blocked.getMessage());}
                }
            }
            List<Map<String,Object>> allocations=mapper.allocationHistory(batch);
            for(Map<String,Object> allocation:allocations) {
                boolean original="allocate".equals(allocation.get("entryType"));
                allocation.put("refundableAmount",original?refunds.remaining(allocation.get("allocationId").toString(),null):BigDecimal.ZERO);
            }
            return map("mode","participant","revision",order.get("revision"),"writeEnabled",modes.enabled(),"participants",people,"receipts",facts,
                "allocations",allocations,
                "refundRequests",refunds.history(batch),
                "passVerifications",passMapper.verifications(batch),
                "orderSummary",map("total",people.size(),"confirmed",confirmed,"pending",people.size()-confirmed-cancelled,"cancelled",cancelled,"grossReceived",gross,"allocatedAmount",allocated,"unallocatedAmount",gross.subtract(allocated),
                    "refundedAmount",refundTotals.get("refundedAmount"),"refundReserved",refundTotals.get("refundReserved"),"netReceived",gross.subtract((BigDecimal)refundTotals.get("refundedAmount"))),
                "capabilities",map("cashAllocation",true,"refunds",true,"participantCancellation",true,"passSettlement",true));
        });
    }
    private void status(QlRegistration r,String type,String from,String to,String why,Long operator,Date now) {
        QlRegistrationStatusLog log=new QlRegistrationStatusLog();log.setId(UUID.randomUUID().toString());log.setRegistrationId(r.getId());log.setStatusType(type);
        log.setFromStatus(from);log.setToStatus(to);log.setChangeReason(why);log.setOperatorId(operator);log.setChangedAt(now);
        if(logs.insert(log)!=1)throw new CustomException("人员审计写入失败",500);
    }
}
