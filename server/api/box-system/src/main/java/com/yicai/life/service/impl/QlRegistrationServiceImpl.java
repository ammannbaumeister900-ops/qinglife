package com.yicai.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yicai.common.core.mybatisplus.core.ServicePlusImpl;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.PageUtils;
import com.yicai.life.domain.QlRegistration;
import com.yicai.life.domain.QlRegistrationStatusLog;
import com.yicai.life.domain.QlTransaction;
import com.yicai.life.domain.bo.QlPaymentBo;
import com.yicai.life.domain.bo.QlRegistrationBo;
import com.yicai.life.domain.vo.QlRegistrationVo;
import com.yicai.life.mapper.QlRegistrationMapper;
import com.yicai.life.mapper.QlRegistrationStatusLogMapper;
import com.yicai.life.mapper.QlTransactionMapper;
import com.yicai.life.service.IQlRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class QlRegistrationServiceImpl
        extends ServicePlusImpl<QlRegistrationMapper, QlRegistration, QlRegistrationVo>
        implements IQlRegistrationService {

    private static final java.util.List<String> PAYMENT_STATUSES = Arrays.asList("unpaid", "paid");
    private static final java.util.List<String> REGISTRATION_STATUSES = Arrays.asList("pending", "confirmed", "waitlisted", "cancelled");

    @Autowired private com.yicai.life.service.QlRegistrationPolicy policy;
    private final QlRegistrationMapper registrationMapper;
    private final com.yicai.life.mapper.QlSessionMapper sessionMapper;
    private final com.yicai.life.service.QlSessionPricing pricing;
    private final QlRegistrationStatusLogMapper statusLogMapper;
    private final QlTransactionMapper transactionMapper;

    @Override
    public TableDataInfo<QlRegistrationVo> queryPageList(QlRegistrationBo bo) {
        Page<QlRegistrationVo> page = registrationMapper.selectPageList(PageUtils.buildPage(), bo);
        return PageUtils.buildDataInfo(page);
    }

    @Override
    public QlRegistrationVo queryById(String id) {
        return registrationMapper.selectVoById(id);
    }

    @Override
    @Transactional
    public boolean insertByBo(QlRegistrationBo bo, Long operatorId) {
        QlRegistration entity = BeanUtil.toBean(bo, QlRegistration.class);
        Date now = new Date();
        entity.setId(UUID.randomUUID().toString());
        entity.setRegistrationStatus(StrUtil.blankToDefault(entity.getRegistrationStatus(), "pending"));
        validateRegistrationStatus(entity.getRegistrationStatus());
        entity.setPaymentStatus("unpaid");
        entity.setRegistrationSource(StrUtil.blankToDefault(entity.getRegistrationSource(), "web_admin"));
        if ("cancelled".equals(entity.getRegistrationStatus())) throw new CustomException("不能新增已取消报名",400);
        policy.admit(bo.getSessionId(), "waitlisted".equals(entity.getRegistrationStatus()) ? 0 : 1, true);
        com.yicai.life.domain.QlSession session = sessionMapper.selectById(bo.getSessionId());
        if (session == null) throw new CustomException("期次不存在", 400);
        entity.setUnitPrice(pricing.price(bo.getCustomerId(), session.getStandardPrice(), session.getReturningPrice()));
        entity.setRegisteredAt(now);
        if ("confirmed".equals(entity.getRegistrationStatus())) {
            entity.setConfirmedAt(now);
            entity.setConfirmedBy(operatorId);
        }
        entity.setRevision(0);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        boolean saved = save(entity);
        if (saved) {
            insertStatusLog(entity.getId(), "registration", null, entity.getRegistrationStatus(), "后台新增报名", operatorId, now);
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateByBo(QlRegistrationBo bo, Long operatorId) {
        QlRegistration snapshot = getById(bo.getId());
        if (snapshot == null) throw new CustomException("报名记录不存在",400);
        policy.lockSession(snapshot.getSessionId());
        if (StrUtil.isNotBlank(snapshot.getBatchId())) registrationMapper.selectBatchForPayment(snapshot.getBatchId());
        QlRegistration current = registrationMapper.selectForUpdate(bo.getId());
        if (current == null) {
            throw new CustomException("报名记录不存在", 400);
        }
        if (StrUtil.isNotBlank(bo.getRegistrationStatus())) validateRegistrationStatus(bo.getRegistrationStatus());
        String next = StrUtil.blankToDefault(bo.getRegistrationStatus(),current.getRegistrationStatus());
        policy.transition(current.getId(),current.getRegistrationStatus(),next,current.getPaymentStatus(),current.getBatchId());
        if (!next.equals(current.getRegistrationStatus()) && Arrays.asList("pending","confirmed").contains(next))
            policy.admit(current.getSessionId(), "waitlisted".equals(current.getRegistrationStatus()) ? 1 : 0, false);
        QlRegistration entity = BeanUtil.toBean(bo, QlRegistration.class);
        entity.setCustomerId(null);
        entity.setSessionId(null);
        entity.setPaymentStatus(null);
        entity.setRegistrationSource(null);
        entity.setRegisteredAt(null);
        entity.setCreatedBy(null);
        entity.setCreatedAt(null);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(new Date());
        entity.setRevision(current.getRevision() == null ? 1 : current.getRevision() + 1);
        if ("confirmed".equals(bo.getRegistrationStatus()) && current.getConfirmedAt() == null) {
            entity.setConfirmedAt(entity.getUpdatedAt());
            entity.setConfirmedBy(operatorId);
        }
        boolean updated = updateById(entity);
        if (updated && StrUtil.isNotBlank(bo.getRegistrationStatus())
                && !bo.getRegistrationStatus().equals(current.getRegistrationStatus())) {
            insertStatusLog(current.getId(), "registration", current.getRegistrationStatus(),
                    bo.getRegistrationStatus(), "后台更新报名状态", operatorId, new Date());
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean changePayment(String id, QlPaymentBo bo, Long operatorId) {
        if (!PAYMENT_STATUSES.contains(bo.getPaymentStatus())) {
            throw new CustomException("当前版本仅支持未付款或已付款", 400);
        }
        // Orders are always locked before their participants, on every payment entry point.
        QlRegistration snapshot = getById(id);
        if (snapshot != null) policy.lockSession(snapshot.getSessionId());
        if (snapshot != null && StrUtil.isNotBlank(snapshot.getBatchId())) {
            return changeBatchPayment(snapshot.getBatchId(), bo, operatorId);
        }
        QlRegistration current = registrationMapper.selectForUpdate(id);
        if (current == null) {
            throw new CustomException("报名记录不存在", 400);
        }
        if (bo.getPaymentStatus().equals(current.getPaymentStatus())) {
            return true;
        }
        if ("paid".equals(bo.getPaymentStatus())) {
            if (!"confirmed".equals(current.getRegistrationStatus())) {
                throw new CustomException("请先确认报名", 400);
            }
            QlRegistrationVo quote = registrationMapper.selectVoById(id);
            validateFullPayment(bo, quote == null ? null : quote.getStandardPrice());
        } else if (StrUtil.isBlank(bo.getChangeReason())) {
            throw new CustomException("撤销已付款必须填写原因", 400);
        }

        Date now = new Date();
        int rows = registrationMapper.updatePaymentStatus(id, current.getPaymentStatus(),
                bo.getPaymentStatus(), operatorId, now);
        if (rows != 1) {
            throw new CustomException("付款状态已被其他人更新，请刷新后重试", 400);
        }
        insertStatusLog(id, "payment", current.getPaymentStatus(), bo.getPaymentStatus(),
                bo.getChangeReason(), operatorId, now);

        if ("paid".equals(bo.getPaymentStatus())) {
            QlTransaction transaction = new QlTransaction();
            transaction.setId(UUID.randomUUID().toString());
            transaction.setCustomerId(current.getCustomerId());
            transaction.setSessionId(current.getSessionId());
            transaction.setRegistrationId(id);
            transaction.setTransactionType("session");
            transaction.setAmount(bo.getAmount() == null ? BigDecimal.ZERO : bo.getAmount());
            transaction.setPaymentMethod(bo.getPaymentMethod());
            transaction.setStatus("paid");
            transaction.setTransactionAt(now);
            transaction.setRecordSource("operator_entry");
            transaction.setOperatorId(operatorId);
            transaction.setRemark(bo.getChangeReason());
            transaction.setRevision(0);
            transaction.setCreatedAt(now);
            transaction.setUpdatedAt(now);
            transactionMapper.insert(transaction);
        } else {
            transactionMapper.cancelPaidByRegistration(id, operatorId, bo.getChangeReason(), now);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean changeBatchPayment(String batchId, QlPaymentBo bo, Long operatorId) {
        if (!PAYMENT_STATUSES.contains(bo.getPaymentStatus())) {
            throw new CustomException("当前版本仅支持未付款或已付款", 400);
        }
        String sessionId = registrationMapper.selectBatchSessionId(batchId);
        if (sessionId == null) throw new CustomException("报名订单不存在",400);
        policy.lockSession(sessionId);
        Map<String, Object> batch = registrationMapper.selectBatchForPayment(batchId);
        if (batch == null) {
            throw new CustomException("报名订单不存在", 400);
        }
        String currentStatus = String.valueOf(batch.get("paymentStatus"));
        if (bo.getPaymentStatus().equals(currentStatus)) {
            return true;
        }
        if ("paid".equals(bo.getPaymentStatus())) {
            List<QlRegistration> participants = registrationMapper.selectBatchRegistrationsForUpdate(batchId);
            if (participants.isEmpty()) throw new CustomException("报名订单没有参与人", 400);
            for (QlRegistration participant : participants) {
                if (!"confirmed".equals(participant.getRegistrationStatus())) {
                    throw new CustomException("请先确认订单内全部报名", 400);
                }
            }
            Object amount = batch.get("payableAmount");
            validateFullPayment(bo, amount == null ? null : new BigDecimal(amount.toString()));
        } else if (StrUtil.isBlank(bo.getChangeReason())) {
            throw new CustomException("撤销已付款必须填写原因", 400);
        }

        Date now = new Date();
        int rows = registrationMapper.updateBatchPaymentStatus(batchId, currentStatus,
                bo.getPaymentStatus(), operatorId, now);
        if (rows != 1) {
            throw new CustomException("付款状态已被其他人更新，请刷新后重试", 400);
        }
        registrationMapper.updateRegistrationPaymentByBatch(batchId, bo.getPaymentStatus(), operatorId, now);
        List<String> registrationIds = registrationMapper.selectRegistrationIdsByBatch(batchId);
        for (String registrationId : registrationIds) {
            insertStatusLog(registrationId, "payment", currentStatus, bo.getPaymentStatus(),
                    bo.getChangeReason(), operatorId, now);
        }
        if ("paid".equals(bo.getPaymentStatus())) {
            QlTransaction transaction = new QlTransaction();
            transaction.setId(UUID.randomUUID().toString());
            transaction.setCustomerId(String.valueOf(batch.get("buyerCustomerId")));
            transaction.setSessionId(String.valueOf(batch.get("sessionId")));
            transaction.setRegistrationBatchId(batchId);
            transaction.setTransactionType("session");
            transaction.setAmount(bo.getAmount());
            transaction.setPaymentMethod(bo.getPaymentMethod());
            transaction.setStatus("paid");
            transaction.setTransactionAt(now);
            transaction.setRecordSource("operator_entry");
            transaction.setOperatorId(operatorId);
            transaction.setRemark(bo.getChangeReason());
            transaction.setRevision(0);
            transaction.setCreatedAt(now);
            transaction.setUpdatedAt(now);
            transactionMapper.insert(transaction);
        } else {
            transactionMapper.cancelPaidByBatch(batchId, operatorId, bo.getChangeReason(), now);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean cancelBatch(String batchId,String reason,Long operatorId) {
        if(StrUtil.isBlank(reason)||reason.length()>500)throw new CustomException("整单取消需填写原因（最多500字）",400);
        String sessionId=registrationMapper.selectBatchSessionId(batchId);
        if(sessionId==null)throw new CustomException("报名订单不存在",404);
        policy.lockSession(sessionId);
        Map<String,Object> batch=registrationMapper.selectBatchForPayment(batchId);
        if(!"unpaid".equals(batch.get("paymentStatus")))throw new CustomException("请先撤销整单付款",400);
        List<QlRegistration> participants=registrationMapper.selectBatchRegistrationsForUpdate(batchId);
        if(participants.isEmpty())throw new CustomException("订单没有参与人",400);
        for(QlRegistration r:participants) {
            // Explicit whole-order operation; the single-participant restriction does not apply.
            policy.transition(r.getId(),r.getRegistrationStatus(),"cancelled",r.getPaymentStatus(),null);
        }
        Date now=new Date();
        for(QlRegistration r:participants) {
            if("cancelled".equals(r.getRegistrationStatus()))continue;
            String before=r.getRegistrationStatus();
            QlRegistration change=new QlRegistration();change.setId(r.getId());change.setRegistrationStatus("cancelled");
            change.setRevision(r.getRevision()+1);change.setUpdatedAt(now);change.setUpdatedBy(operatorId);
            if(!updateById(change))throw new CustomException("取消失败，请刷新后重试",409);
            insertStatusLog(r.getId(),"registration",before,"cancelled",reason,operatorId,now);
        }
        return true;
    }

    private void validateFullPayment(QlPaymentBo bo, BigDecimal due) {
        if (bo.getAmount() == null || due == null || bo.getAmount().signum() < 0
                || due.compareTo(bo.getAmount()) != 0) {
            throw new CustomException("实付金额必须与应付金额一致", 400);
        }
        if (!Arrays.asList("wechat_scan","alipay_scan","transfer","cash","other").contains(bo.getPaymentMethod())) {
            throw new CustomException("确认付款时必须填写付款方式", 400);
        }
    }

    private void validateRegistrationStatus(String status) {
        if (!REGISTRATION_STATUSES.contains(status)) {
            throw new CustomException("本期不支持改期；请先退款或撤销付款，再另外报名", 400);
        }
    }

    private void insertStatusLog(String registrationId, String type, String fromStatus,
                                 String toStatus, String reason, Long operatorId, Date changedAt) {
        QlRegistrationStatusLog log = new QlRegistrationStatusLog();
        log.setId(UUID.randomUUID().toString());
        log.setRegistrationId(registrationId);
        log.setStatusType(type);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setChangeReason(reason);
        log.setOperatorId(operatorId);
        log.setChangedAt(changedAt);
        statusLogMapper.insert(log);
    }
}
