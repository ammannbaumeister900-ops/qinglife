package com.yicai.life.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.QlRegistration;
import com.yicai.life.domain.bo.QlRegistrationBo;
import com.yicai.life.domain.vo.QlRegistrationVo;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface QlRegistrationMapper extends BaseMapperPlus<QlRegistration> {
    Page<QlRegistrationVo> selectPageList(@Param("page") Page<QlRegistrationVo> page,
                                          @Param("bo") QlRegistrationBo bo);
    @org.apache.ibatis.annotations.Select("SELECT * FROM ql_registration WHERE id=#{id} FOR UPDATE")
    QlRegistration selectForUpdate(@Param("id") String id);
    @org.apache.ibatis.annotations.Select("SELECT * FROM ql_registration WHERE batch_id=#{batchId} ORDER BY id FOR UPDATE")
    List<QlRegistration> selectBatchRegistrationsForUpdate(@Param("batchId") String batchId);
    QlRegistrationVo selectVoById(@Param("id") String id);
    int updatePaymentStatus(@Param("id") String id, @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus, @Param("operatorId") Long operatorId,
                            @Param("updatedAt") Date updatedAt);
    Map<String, Object> selectBatchForPayment(@Param("batchId") String batchId);
    List<String> selectRegistrationIdsByBatch(@Param("batchId") String batchId);
    int updateBatchPaymentStatus(@Param("batchId") String batchId, @Param("fromStatus") String fromStatus,
                                 @Param("toStatus") String toStatus, @Param("operatorId") Long operatorId,
                                 @Param("updatedAt") Date updatedAt);
    int updateRegistrationPaymentByBatch(@Param("batchId") String batchId, @Param("toStatus") String toStatus,
                                         @Param("operatorId") Long operatorId, @Param("updatedAt") Date updatedAt);
}
