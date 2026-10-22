package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.*;
import com.yicai.life.mapper.*;
import com.yicai.life.service.QlParticipantFinanceGuard.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import static com.yicai.life.service.QlFinanceOperationService.*;

/** Person-only cash refunds and cancellation. Actual outflow commits before attribution, never releases a seat. */
@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class QlParticipantRefundService {
    private final QlParticipantFinanceGuard guard;
    private final QlParticipantFinanceMapper finance;
    private final QlParticipantRefundMapper refunds;
    private final QlFinanceOperationService operations;
    private final QlRegistrationPolicy seats;
    private final QlRegistrationStatusLogMapper logs;

    private Target lock(String registration) {return guard.lock(guard.batchFor(registration));}
    private List<Map<String,Object>> allocations(Target t,String registration) {
        List<Map<String,Object>> rows=finance.allocations(registration);
        for(Map<String,Object> row:rows)if(!t.id().equals(row.get("batchId"))||!"allocate".equals(row.get("entryType"))||
            ((Number)row.get("reversed")).intValue()!=0||!"paid".equals(row.get("receiptStatus"))||!"session".equals(row.get("transactionType")))
            throw new CustomException("原到账分配存在异常，请先核对",409);
        return rows;
    }
    private Map<String,Object> request(String id,String registration) {
        uuid(id);Map<String,Object> r=refunds.request(id);
        if(r==null||!registration.equals(r.get("registrationId")))throw new CustomException("退款申请不存在或不属于此人",409);
        return r;
    }
    private void advance(Target t,String registration,Long operator) {
        finance.advanceRegistration(registration,operator);finance.advanceBatch(t.id());
    }
    public Map<String,Object> request(String registration,QlRefundRequestBo bo,Long operator) {
        uuid(bo.getOperationId());String amount=money(bo.getRequestedAmount(),10),why=reason(bo.getReason());
        if(bo.getAllocations()==null||bo.getAllocations().isEmpty()||bo.getAllocations().size()>100)throw new CustomException("请指定1至100条原分配",400);
        List<QlRefundRequestBo.Line> lines=new ArrayList<>(bo.getAllocations());Set<String> ids=new HashSet<>();BigDecimal sum=BigDecimal.ZERO;
        for(QlRefundRequestBo.Line line:lines) {
            if(line==null)throw new CustomException("退款分配不能为空",400);
            uuid(line.getAllocationId());money(line.getAmount(),10);
            if(!ids.add(line.getAllocationId()))throw new CustomException("退款分配不可重复",400);
            sum=sum.add(line.getAmount());
        }
        if(sum.compareTo(bo.getRequestedAmount())!=0)throw new CustomException("预留合计须等于申请金额",400);
        lines.sort(Comparator.comparing(QlRefundRequestBo.Line::getAllocationId));List<Object> payload=new ArrayList<>();
        for(QlRefundRequestBo.Line line:lines)payload.add(map("allocationId",line.getAllocationId(),"amount",money(line.getAmount(),10)));
        return guard.write(()->{
            Target t=lock(registration);QlRegistration person=guard.person(t,registration);List<Map<String,Object>> original=allocations(t,registration);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"refund_request",registration,operator,Arrays.asList(bo.getExpectedRevision(),amount,payload,why));
            if(replay!=null)return replay;
            guard.revision(bo.getExpectedRevision(),person.getRevision());
            if(finance.hasPass(registration)||"pass".equals(person.getSettlementType())||person.getPassAccountId()!=null||person.getPassUnits()!=null&&person.getPassUnits()>0)throw new CustomException("卡次不能折现退款",409);
            if(finance.hasBatchSettlement(t.id())||finance.hasUnlinkedReceipt(registration)||refunds.needsReconciliation(registration))
                throw new CustomException("存在未核对资金，请先完成核对",409);
            for(QlRefundRequestBo.Line line:lines) {
                if(original.stream().noneMatch(a->line.getAllocationId().equals(a.get("id"))))throw new CustomException("分配不属于此人",409);
                if(line.getAmount().compareTo(refunds.remaining(line.getAllocationId(),null))>0)throw new CustomException("申请超过原分配的可退余额（含已退及有效预留）",409);
            }
            String id=UUID.randomUUID().toString();refunds.request(id,registration,bo.getOperationId(),bo.getRequestedAmount(),why,operator);
            for(QlRefundRequestBo.Line line:lines)refunds.reserve(id,line.getAllocationId(),line.getAmount());
            advance(t,registration,operator);
            return operations.finish(bo.getOperationId(),map("requestId",id,"registrationId",registration,"requestedAmount",amount,"status","requested","revision",0,"reason",why));
        });
    }
    public Map<String,Object> transition(String registration,String requestId,String action,QlFinanceActionBo bo,Long operator) {
        uuid(bo.getOperationId());uuid(requestId);String why=reason(bo.getReason());
        if(!Arrays.asList("processing","cancel","fail").contains(action))throw new CustomException("退款动作无效",400);
        if("processing".equals(action)&&bo.getNoActualPaymentConfirmed()!=null)throw new CustomException("处理中不能声明未退款",400);
        return guard.write(()->{
            Target t=lock(registration);allocations(t,registration);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"refund_"+action,requestId,operator,Arrays.asList(registration,bo.getExpectedRevision(),why,bo.getNoActualPaymentConfirmed()));
            if(replay!=null)return replay;
            Map<String,Object> r=request(requestId,registration);guard.revision(bo.getExpectedRevision(),r.get("revision"));
            if(refunds.hasPayment(requestId))throw new CustomException("已有实际退款事实，不可取消或失败释放额度",409);
            String before=r.get("status").toString(),after;
            if("processing".equals(action)) {
                if(!"requested".equals(before))throw new CustomException("仅待处理申请可进入处理中",409);
                after="processing";
            } else {
                if(!Arrays.asList("requested","processing","reconciliation_required").contains(before))throw new CustomException("申请已关闭",409);
                // Ambiguous/in-flight money stays reserved until an explicit operator assertion of no actual outflow.
                after=Boolean.TRUE.equals(bo.getNoActualPaymentConfirmed())?("cancel".equals(action)?"cancelled":"failed"):"reconciliation_required";
            }
            refunds.state(requestId,after);advance(t,registration,operator);
            return operations.finish(bo.getOperationId(),map("requestId",requestId,"status",after,"fromStatus",before,"revision",((Number)r.get("revision")).intValue()+1,"reason",why,
                "noActualPaymentConfirmed",bo.getNoActualPaymentConfirmed(),"reservationReleased",Arrays.asList("cancelled","failed").contains(after)));
        });
    }
    public Map<String,Object> payment(String registration,String requestId,QlRefundPaymentBo bo,Long operator) {
        uuid(bo.getOperationId());uuid(requestId);String amount=money(bo.getActualAmount(),10),evidence=reason(bo.getEvidence()),why=reason(bo.getReason());
        if(!Arrays.asList("cash","wechat_scan","alipay_scan","transfer","other").contains(bo.getPaymentMethod()))throw new CustomException("退款方式无效",400);
        String reference=bo.getExternalReference()==null?null:bo.getExternalReference().trim();
        if(reference!=null&&(reference.isEmpty()||reference.length()>100))throw new CustomException("回单号无效",400);
        final Date occurred;
        try {OffsetDateTime time=OffsetDateTime.parse(bo.getOccurredAt());if(!ZoneOffset.ofHours(8).equals(time.getOffset()))throw new DateTimeParseException("offset",bo.getOccurredAt(),0);occurred=Date.from(time.toInstant());}
        catch(DateTimeParseException|NullPointerException e){throw new CustomException("实际退款时间须为有效的+08:00日期时间",400);}
        Map<String,Object> captured=guard.write(()->{
            Target t=lock(registration);allocations(t,registration);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"refund_payment",requestId,operator,Arrays.asList(registration,bo.getExpectedRevision(),amount,bo.getPaymentMethod(),reference,evidence,bo.getOccurredAt(),why));
            if(replay!=null)return replay;
            Map<String,Object> r=request(requestId,registration);guard.revision(bo.getExpectedRevision(),r.get("revision"));
            if(!Arrays.asList("requested","processing").contains(r.get("status"))||refunds.hasPayment(requestId))throw new CustomException("申请不能再次登记实际退款，请核对原事实",409);
            String id=UUID.randomUUID().toString();refunds.payment(id,requestId,bo.getOperationId(),bo.getActualAmount(),bo.getPaymentMethod(),reference,evidence,occurred);
            refunds.state(requestId,"reconciliation_required");advance(t,registration,operator);
            return operations.finish(bo.getOperationId(),map("paymentId",id,"requestId",requestId,"actualAmount",amount,"status","recorded_unreconciled","reason",why));
        });
        String payment=captured.get("paymentId").toString();
        try {return guard.write(()->apply(registration,requestId,payment,operator));}
        catch(RuntimeException error) {
            // Only attribution rolled back. Exact original request can retry attribution without another real payment.
            log.warn("Participant refund attribution pending for payment {}",payment);
            return map("paymentId",payment,"requestId",requestId,"actualAmount",captured.get("actualAmount"),"status","recorded_unreconciled","reconciliationRequired",true);
        }
    }
    private Map<String,Object> apply(String registration,String requestId,String paymentId,Long operator) {
        Target t=lock(registration);List<Map<String,Object>> original=allocations(t,registration);
        Map<String,Object> r=request(requestId,registration),payment=refunds.payment(paymentId);
        if(!requestId.equals(payment.get("requestId")))throw new CustomException("退款事实归属不符",409);
        if("applied".equals(payment.get("status")))return applied(paymentId,requestId,payment.get("amount"));
        if(!"reconciliation_required".equals(r.get("status"))||((BigDecimal)payment.get("amount")).compareTo((BigDecimal)r.get("amount"))!=0)
            throw new CustomException("实际退款与申请不同，保留原事实待核对",409);
        List<Map<String,Object>> reserved=refunds.reservations(requestId);BigDecimal sum=BigDecimal.ZERO;
        for(Map<String,Object> line:reserved) {
            String id=line.get("allocationId").toString();BigDecimal amount=(BigDecimal)line.get("amount");
            if(original.stream().noneMatch(a->id.equals(a.get("id")))||amount.compareTo(refunds.remaining(id,requestId))>0)throw new CustomException("退款归因超过原分配余额",409);
            sum=sum.add(amount);
        }
        if(sum.compareTo((BigDecimal)payment.get("amount"))!=0)throw new CustomException("退款归因金额不一致",409);
        for(Map<String,Object> line:reserved)refunds.attribute(paymentId,line.get("allocationId").toString(),(BigDecimal)line.get("amount"));
        refunds.applied(paymentId);refunds.state(requestId,"completed");advance(t,registration,operator);
        return applied(paymentId,requestId,payment.get("amount"));
    }
    private Map<String,Object> applied(String payment,String request,Object amount) {
        return map("paymentId",payment,"requestId",request,"actualAmount",amount.toString(),"status","applied","requestStatus","completed","reconciliationRequired",false);
    }
    public Map<String,Object> cancel(String registration,QlFinanceActionBo bo,Long operator) {
        uuid(bo.getOperationId());String why=reason(bo.getReason());
        if(bo.getNoActualPaymentConfirmed()!=null)throw new CustomException("报名取消与退款申请取消是独立动作",400);
        return guard.write(()->{
            Target t=lock(registration);QlRegistration r=guard.person(t,registration);finance.allocations(registration);
            Map<String,Object> replay=operations.begin(bo.getOperationId(),"participant_cancel",registration,operator,Arrays.asList(bo.getExpectedRevision(),why));
            if(replay!=null)return replay;
            guard.revision(bo.getExpectedRevision(),r.getRevision());
            if(!Arrays.asList("pending","waitlisted","confirmed").contains(r.getRegistrationStatus()))throw new CustomException("已取消报名不能再次取消或恢复",409);
            seats.requireNoAttendance(registration);
            if(refunds.cancel(registration,r.getRevision(),operator)!=1)throw new CustomException("报名状态已变化",409);
            QlRegistrationStatusLog audit=new QlRegistrationStatusLog();audit.setId(UUID.randomUUID().toString());audit.setRegistrationId(registration);audit.setStatusType("registration");
            audit.setFromStatus(r.getRegistrationStatus());audit.setToStatus("cancelled");audit.setChangeReason(why);audit.setOperatorId(operator);audit.setChangedAt(new Date());
            if(logs.insert(audit)!=1)throw new CustomException("取消审计失败",500);
            finance.advanceBatch(t.id());
            return operations.finish(bo.getOperationId(),map("registrationId",registration,"registrationStatus","cancelled","seatOccupied",false,"revision",r.getRevision()+1,"reason",why,"refundCreated",false));
        });
    }
    /** Safe backend summary only: no private evidence, reference numbers or operator credentials. */
    public Map<String,Object> decorate(List<Map<String,Object>> people) {
        BigDecimal actual=BigDecimal.ZERO,reserved=BigDecimal.ZERO;
        for(Map<String,Object> person:people) {
            String id=person.get("id").toString();Map<String,Object> summary=refunds.summary(id);
            BigDecimal paid=(BigDecimal)summary.get("refundedAmount"),held=(BigDecimal)summary.get("refundReserved"),gross=(BigDecimal)person.get("allocatedAmount");
            boolean blocked=refunds.needsReconciliation(id);
            String state=blocked?"reconciliation_required":((Number)summary.get("processingCount")).intValue()>0?"processing":((Number)summary.get("requestedCount")).intValue()>0?"requested":
                paid.signum()==0?"none":paid.compareTo(gross)>=0?"full":"partial";
            person.put("refundState",state);person.put("refundedAmount",paid);person.put("refundReserved",held);person.put("netReceived",gross.subtract(paid));
            person.put("refundableAmount",blocked?BigDecimal.ZERO:gross.subtract(paid).subtract(held).max(BigDecimal.ZERO));person.put("reconciliationRequired",blocked);
            actual=actual.add(paid);reserved=reserved.add(held);
        }
        return map("refundedAmount",actual,"refundReserved",reserved);
    }
}
