package com.yicai.life.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yicai.common.exception.CustomException;
import com.yicai.life.domain.*;
import com.yicai.life.domain.bo.*;
import com.yicai.life.domain.vo.QlRegistrationVo;
import com.yicai.life.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** A verified receipt survives allocation failure. Registration, settlement and paid-seat writes remain atomic. */
@Service @RequiredArgsConstructor
public class QlOfflineReceiptService {
    private final PlatformTransactionManager transactionManager;
    private final QlRegistrationPolicy policy;
    private final QlRegistrationMapper registrations;
    private final QlTransactionMapper transactions;
    private final QlOfflineReceiptMapper receipts;
    private final QlRegistrationStatusLogMapper statusLogs;
    private final IQlRegistrationService registrationService;
    private final QlOfflineReceiptImageService images;
    private final ObjectMapper json;
    private final QlParticipantModePolicy modes;

    private TransactionTemplate separateTransaction() {
        TransactionTemplate t=new TransactionTemplate(transactionManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Re-read the locked order and capacity after concurrent commits.
        t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return t;
    }
    public List<Map<String,Object>> list(String id) {
        QlRegistrationVo row=registrations.selectVoById(id);
        if(row==null)throw new CustomException("报名不存在",404);
        List<Map<String,Object>> result=receipts.list(id,StrUtil.emptyToNull(row.getBatchId()));
        for(Map<String,Object> r:result) {
            r.put("attachments",images.list(String.valueOf(r.get("id"))));
            r.put("revisions",receipts.revisions(String.valueOf(r.get("id"))));
        }
        return result;
    }
    public Map<String,Object> image(String registration,String image) {return images.image(registration,image);}
    public boolean confirm(String id,QlOfflineReceiptBo bo,Long operator) {
        if(bo==null || !Boolean.TRUE.equals(bo.getReceivedConfirmed()) || operator==null)
            throw new CustomException("请先核实实际到账",400);
        List<byte[]> imageBytes=images.validate(bo.getAttachments());
        QlTransaction captured;
        try {captured=separateTransaction().execute(s->capture(id,bo,operator,imageBytes));}
        catch(DuplicateKeyException e){throw new CustomException("收款凭据已登记，请刷新收款记录核对，勿重复记账",409);}
        if(Boolean.TRUE.equals(bo.getDefer()))return true;
        String receiptId=captured.getId();
        try {return Boolean.TRUE.equals(separateTransaction().execute(s->allocate(id,receiptId,captured.getRevision(),operator)));}
        catch(RuntimeException e) {
            String reason=e instanceof CustomException?e.getMessage():"处理失败，请联系管理员核查";
            Boolean alreadyPaid=separateTransaction().execute(s->{
                QlTransaction r=receipts.lockReceipt(receiptId);
                if("paid".equals(r.getStatus()))return true;
                if("pending_confirmation".equals(r.getStatus()) && Objects.equals(r.getRevision(),captured.getRevision()))audit(receiptId,"pending_confirmation","pending_confirmation","报名未确认："+reason,operator,new Date());
                return false;
            });
            if(Boolean.TRUE.equals(alreadyPaid))throw new CustomException("该收款已完成报名确认，请刷新查看",409);
            throw new CustomException("到账记录已保留，报名未确认："+reason,409);
        }
    }
    private QlTransaction capture(String id,QlOfflineReceiptBo bo,Long operator,List<byte[]> imageBytes) {
        Target t=lockTarget(id);
        modes.requireLegacyRegistration(id);
        if(Boolean.TRUE.equals(bo.getCaptureOnly()))throw new CustomException("历史订单不接受逐人到账报文",409);
        String ref=StrUtil.trimToNull(bo.getReceiptReference()),evidence=StrUtil.trimToNull(bo.getEvidence());
        String key=StrUtil.trimToNull(bo.getRequestId()),existing=StrUtil.trimToNull(bo.getReceiptId());
        if(ref==null && key==null && existing==null)throw new CustomException("操作标识缺失，请刷新后重新操作",400);
        QlTransaction old=existing!=null?receipts.lockReceipt(existing):key==null?null:receipts.byRequest(key);
        if(existing!=null && old==null)throw new CustomException("原收款记录不存在",404);
        if(old==null && ref!=null)old=receipts.byReference(ref);
        if(old!=null) {
            if(!Objects.equals(old.getRegistrationBatchId(),t.row.getBatchId())
                || (StrUtil.isBlank(t.row.getBatchId()) && !id.equals(old.getRegistrationId()))
                || !"operator_entry".equals(old.getRecordSource()) || !Objects.equals(old.getSessionId(),t.row.getSessionId()))
                throw new CustomException("此收款凭据属于其他订单，不能处理",409);
            images.checkRetry(old.getId(),bo.getAttachments(),imageBytes);
            if(!"pending_confirmation".equals(old.getStatus()))throw new CustomException("收款记录已处理，不能重复确认或修改",409);
            return reconcile(old,bo,t,operator);
        }
        if("paid".equals(StrUtil.blankToDefault(t.row.getBatchPaymentStatus(),t.row.getPaymentStatus()))
            || transactions.countUnallocatedReceipts(id,StrUtil.emptyToNull(t.row.getBatchId()))>0)
            throw new CustomException("已有收款记录，请刷新收款记录后处理，勿重复登记",409);
        Date now=new Date();QlTransaction r=new QlTransaction();r.setId(UUID.randomUUID().toString());
        r.setCustomerId(StrUtil.blankToDefault(t.row.getBuyerCustomerId(),t.row.getCustomerId()));
        r.setSessionId(t.row.getSessionId());r.setRegistrationBatchId(StrUtil.emptyToNull(t.row.getBatchId()));
        r.setRegistrationId(StrUtil.isBlank(t.row.getBatchId())?id:null);
        r.setTransactionType("session");r.setAmount(bo.getAmount());r.setPaymentMethod(bo.getPaymentMethod());
        r.setStatus("pending_confirmation");r.setTransactionAt(now);r.setRecordSource("operator_entry");
        r.setExpectedAmount(referenceAmount(t));r.setFinalSettlementConfirmed(Boolean.TRUE.equals(bo.getFinalSettlementConfirmed()));
        r.setDifferenceReason(StrUtil.trimToNull(bo.getDifferenceReason()));r.setPartialPayment(Boolean.TRUE.equals(bo.getPartialPayment()));
        r.setReceiptRequestId(key);r.setExternalTransactionNo(ref);r.setRemark(evidence);r.setOperatorId(operator);r.setRevision(0);r.setCreatedAt(now);r.setUpdatedAt(now);
        if(transactions.insert(r)!=1)throw new CustomException("到账登记失败",500);
        images.save(r.getId(),bo.getAttachments(),imageBytes);
        audit(r.getId(),null,"pending_confirmation","工作人员核实线下到账，待分配并确认报名",operator,now);
        return r;
    }
    private QlTransaction reconcile(QlTransaction old,QlOfflineReceiptBo bo,Target t,Long operator) {
        QlTransaction next;
        try {next=json.readValue(json.writeValueAsString(old),QlTransaction.class);}catch(Exception e){throw new IllegalStateException(e);}
        next.setAmount(bo.getAmount());next.setPaymentMethod(bo.getPaymentMethod());
        next.setExternalTransactionNo(StrUtil.trimToNull(bo.getReceiptReference()));next.setRemark(StrUtil.trimToNull(bo.getEvidence()));
        next.setFinalSettlementConfirmed(Boolean.TRUE.equals(bo.getFinalSettlementConfirmed()));
        next.setDifferenceReason(StrUtil.trimToNull(bo.getDifferenceReason()));next.setPartialPayment(Boolean.TRUE.equals(bo.getPartialPayment()));
        if(next.getExpectedAmount()==null)next.setExpectedAmount(referenceAmount(t));
        boolean financialChange=old.getAmount().compareTo(next.getAmount())!=0 || !Objects.equals(old.getPaymentMethod(),next.getPaymentMethod())
            || !Objects.equals(old.getExternalTransactionNo(),next.getExternalTransactionNo()) || !Objects.equals(old.getRemark(),next.getRemark())
            || !Objects.equals(Boolean.TRUE.equals(old.getPartialPayment()),next.getPartialPayment());
        boolean changed=financialChange || !Objects.equals(old.getExpectedAmount(),next.getExpectedAmount())
            || !Objects.equals(Boolean.TRUE.equals(old.getFinalSettlementConfirmed()),next.getFinalSettlementConfirmed())
            || !Objects.equals(old.getDifferenceReason(),next.getDifferenceReason());
        if(!changed)return old;
        if(!Objects.equals(bo.getReceiptId(),old.getId()) || !Objects.equals(bo.getExpectedRevision(),old.getRevision()) && (financialChange || !Objects.equals(Boolean.TRUE.equals(old.getFinalSettlementConfirmed()),next.getFinalSettlementConfirmed()) || !Objects.equals(old.getDifferenceReason(),next.getDifferenceReason())))
            throw new CustomException("收款记录已变化，请重新打开后继续核对",409);
        String reason=StrUtil.trimToNull(bo.getCorrectionReason());
        if(financialChange && reason==null)throw new CustomException("修正到账信息请填写简要修正原因",400);
        if(reason==null)reason=StrUtil.blankToDefault(next.getDifferenceReason(),"补充原应收快照及结清核对依据");
        Date now=new Date();
        if(receipts.correct(next,now)!=1)throw new CustomException("收款记录已变化，请重新打开",409);
        next.setRevision(old.getRevision()+1);
        try {
            if(receipts.revision(UUID.randomUUID().toString(),old.getId(),next.getRevision(),json.writeValueAsString(old),json.writeValueAsString(next),reason,operator,now)!=1)
                throw new CustomException("收款修正历史写入失败",500);
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        audit(old.getId(),"pending_confirmation","pending_confirmation","继续核对 / 修正："+reason,operator,now);
        return next;
    }
    private BigDecimal referenceAmount(Target t) {
        if("confirmed".equals(t.row.getSettlementStatus()))return t.row.getFinalAmount();
        if(StrUtil.isNotBlank(t.row.getBatchId()))return t.row.getQuotedAmount();
        return t.rows.isEmpty()?null:t.rows.get(0).getUnitPrice();
    }
    private boolean allocate(String id,String receiptId,Integer revision,Long operator) {
        Target t=lockTarget(id);QlTransaction receipt=receipts.lockReceipt(receiptId);
        if(!Objects.equals(revision,receipt.getRevision()))throw new CustomException("到账信息已被修正，请重新核对",409);
        if(!"pending_confirmation".equals(receipt.getStatus()))throw new CustomException("收款记录已处理，不能重复确认",409);
        if(!Arrays.asList("open","in_progress").contains(String.valueOf(t.session.get("configuredStatus")))
            || "completed".equals(String.valueOf(t.session.get("effectiveStatus"))))
            throw new CustomException("当前期次不接受报名确认，请人工核对收款",409);
        if(t.rows.isEmpty() || t.row.getParticipantCount()!=null && t.row.getParticipantCount()!=t.rows.size())
            throw new CustomException("订单参与人数不一致，请人工核查",409);
        for(QlRegistration r:t.rows) {
            if(StrUtil.isNotBlank(t.row.getBatchId()) && ("confirmed".equals(r.getSettlementStatus()) || r.getCurrentSettlementId()!=null))
                throw new CustomException("整单内存在独立结算记录，请人工核查",409);
            if(!Arrays.asList("pending","waitlisted","confirmed").contains(r.getRegistrationStatus())
                || !"unpaid".equals(r.getPaymentStatus()))throw new CustomException("整单存在已取消或付款状态冲突的参与人",409);
            if(receipts.conflictingReceipts(r.getId(),receiptId)>0 || receipts.unreversedPass(r.getId())>0
                || receipts.unreversedSettlements(r.getId(),t.row.getCurrentSettlementId())>0)
                throw new CustomException("已有其他收退款或卡次记录，请人工核查",409);
        }
        if(!"unpaid".equals(StrUtil.blankToDefault(t.row.getBatchPaymentStatus(),t.row.getPaymentStatus())))
            throw new CustomException("订单付款状态异常，请人工核查",409);
        BigDecimal due;
        boolean settled="confirmed".equals(t.row.getSettlementStatus());
        if(!settled && (t.row.getCurrentSettlementId()!=null || t.row.getFinalAmount()!=null))
            throw new CustomException("订单结算状态与金额依据冲突，请人工核查",409);
        if(settled) {
            Map<String,Object> log=t.row.getCurrentSettlementId()==null?null:registrations.selectSettlementLogForUpdate(t.row.getCurrentSettlementId());
            if(!"money".equals(t.row.getSettlementType()) || log==null || t.row.getFinalAmount()==null
                || !"money".equals(log.get("settlementType"))
                || registrations.selectSettlementReversalForUpdate(t.row.getCurrentSettlementId())!=null
                || !Objects.equals(StrUtil.emptyToNull(t.row.getBatchId()),log.get("batchId"))
                || StrUtil.isBlank(t.row.getBatchId()) && !id.equals(log.get("registrationId"))
                || t.row.getFinalAmount().compareTo(new BigDecimal(String.valueOf(log.get("finalAmount"))))!=0)
                throw new CustomException("原结算依据不一致，请人工核查",409);
            due=t.row.getFinalAmount();
        } else {
            due=BigDecimal.ZERO;
            for(QlRegistration r:t.rows) {
                if(r.getUnitPrice()==null || r.getUnitPrice().signum()<0)throw new CustomException("缺少可靠应收金额，请人工核查",409);
                due=due.add(r.getUnitPrice());
            }
            if(StrUtil.isNotBlank(t.row.getBatchId()) && (t.row.getQuotedAmount()==null || due.compareTo(t.row.getQuotedAmount())!=0))
                throw new CustomException("整单报价与参与人明细不一致",409);
        }
        if(Boolean.TRUE.equals(receipt.getPartialPayment()))throw new CustomException("本笔为部分付款，尾款未结清，不能确认报名；可稍后继续核对",409);
        if(receipt.getExpectedAmount()==null || due.compareTo(receipt.getExpectedAmount())!=0)
            throw new CustomException("应收依据已变化，请人工核查，原快照已保留",409);
        if(due.compareTo(receipt.getAmount())!=0) {
            if(!Boolean.TRUE.equals(receipt.getFinalSettlementConfirmed()) || StrUtil.isBlank(receipt.getDifferenceReason()))
                throw new CustomException("实际到账与应收不一致，请确认差额为本单最终结清并填写差额原因；部分付款不可确认",409);
            if(settled)throw new CustomException("已有已确认结算，请先按现有入口撤销结算后重新核对，收款事实已保留",409);
        }
        policy.requirePaidCapacity(t.row.getSessionId(),t.rows.stream().map(QlRegistration::getId).collect(Collectors.toList()));
        Date now=new Date();
        for(QlRegistration r:t.rows)if(!"confirmed".equals(r.getRegistrationStatus())) {
            if(receipts.confirm(r.getId(),r.getRegistrationStatus(),operator,now)!=1)throw new CustomException("报名状态已变化",409);
            status(r.getId(),"registration",r.getRegistrationStatus(),"confirmed","线下到账核验后整单确认",operator,now);
        }
        if(!settled) {
            QlSettlementBo b=new QlSettlementBo();b.setSettlementType("money");b.setFinalAmount(receipt.getAmount());b.setNote("线下到账记录："+receipt.getId()+(StrUtil.isBlank(receipt.getDifferenceReason())?"":"；差额结清："+receipt.getDifferenceReason()));
            if(!registrationService.confirmSettlement(id,b,operator))throw new CustomException("结算写入失败",500);
        }
        if(StrUtil.isNotBlank(t.row.getBatchId())) {
            if(registrations.updateBatchPaymentStatus(t.row.getBatchId(),"unpaid","paid",operator,now)!=1
                || registrations.updateRegistrationPaymentByBatch(t.row.getBatchId(),"paid",operator,now)!=t.rows.size())
                throw new CustomException("整单付款状态已变化",409);
        } else if(registrations.updatePaymentStatus(id,"unpaid","paid",operator,now)!=1)throw new CustomException("付款状态已变化",409);
        for(QlRegistration r:t.rows)status(r.getId(),"payment","unpaid","paid","线下到账记录："+receipt.getId(),operator,now);
        if(receipts.allocate(receiptId,now)!=1)throw new CustomException("到账记录已变化",409);
        audit(receiptId,"pending_confirmation","paid","整单报名已确认并占用名额；应收 "+due+"，实收 "+receipt.getAmount()+"，差额 "+receipt.getAmount().subtract(due)+(StrUtil.isBlank(receipt.getDifferenceReason())?"":"；"+receipt.getDifferenceReason()),operator,now);
        return true;
    }
    private Target lockTarget(String id) {
        QlRegistrationVo snapshot=registrations.selectVoById(id);
        if(snapshot==null)throw new CustomException("报名不存在",404);
        Target t=new Target();t.session=policy.lockSession(snapshot.getSessionId());
        if(StrUtil.isNotBlank(snapshot.getBatchId())) {
            if(registrations.selectBatchForPayment(snapshot.getBatchId())==null)throw new CustomException("订单不存在",404);
            t.rows=registrations.selectBatchRegistrationsForUpdate(snapshot.getBatchId());
        } else {
            QlRegistration row=registrations.selectForUpdate(id);
            if(row==null)throw new CustomException("报名不存在",404);
            t.rows=Collections.singletonList(row);
        }
        t.row=registrations.selectVoById(id);return t;
    }
    private void audit(String id,String from,String to,String reason,Long operator,Date now) {
        if(receipts.audit(UUID.randomUUID().toString(),id,from,to,reason,operator,now)!=1)throw new CustomException("收款审计写入失败",500);
    }
    private void status(String id,String type,String from,String to,String reason,Long operator,Date now) {
        QlRegistrationStatusLog l=new QlRegistrationStatusLog();l.setId(UUID.randomUUID().toString());l.setRegistrationId(id);l.setStatusType(type);
        l.setFromStatus(from);l.setToStatus(to);l.setChangeReason(reason);l.setOperatorId(operator);l.setChangedAt(now);
        if(statusLogs.insert(l)!=1)throw new CustomException("报名审计写入失败",500);
    }
    private static class Target {QlRegistrationVo row;List<QlRegistration> rows;Map<String,Object> session;}
}
