package com.yicai.life.service;

import com.yicai.common.core.mybatisplus.core.IServicePlus;
import com.yicai.common.core.page.TableDataInfo;
import com.yicai.life.domain.QlRegistration;
import com.yicai.life.domain.bo.QlPaymentBo;
import com.yicai.life.domain.bo.QlRegistrationBo;
import com.yicai.life.domain.vo.QlRegistrationVo;

public interface IQlRegistrationService extends IServicePlus<QlRegistration, QlRegistrationVo> {
    TableDataInfo<QlRegistrationVo> queryPageList(QlRegistrationBo bo);
    QlRegistrationVo queryById(String id);
    boolean insertByBo(QlRegistrationBo bo, Long operatorId);
    boolean updateByBo(QlRegistrationBo bo, Long operatorId);
    boolean changePayment(String id, QlPaymentBo bo, Long operatorId);
    boolean cancelBatch(String batchId, String reason, Long operatorId);
    boolean changeBatchPayment(String batchId, QlPaymentBo bo, Long operatorId);
}
