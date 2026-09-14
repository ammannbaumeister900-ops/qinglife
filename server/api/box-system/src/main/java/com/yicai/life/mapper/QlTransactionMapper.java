package com.yicai.life.mapper;

import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.QlTransaction;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface QlTransactionMapper extends BaseMapperPlus<QlTransaction> {
    int cancelPaidByRegistration(@Param("registrationId") String registrationId,
                                 @Param("operatorId") Long operatorId,
                                 @Param("remark") String remark,
                                 @Param("updatedAt") Date updatedAt);
    int cancelPaidByBatch(@Param("batchId") String batchId,
                          @Param("operatorId") Long operatorId,
                          @Param("remark") String remark,
                          @Param("updatedAt") Date updatedAt);
}
