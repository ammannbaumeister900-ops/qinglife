package com.yicai.life.mapper;

import com.yicai.common.core.mybatisplus.core.BaseMapperPlus;
import com.yicai.life.domain.QlTransaction;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface QlTransactionMapper extends BaseMapperPlus<QlTransaction> {
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM ql_transaction WHERE status='paid' AND record_source='operator_entry' AND (external_transaction_no IS NOT NULL OR receipt_request_id IS NOT NULL) AND ((#{batchId} IS NOT NULL AND registration_batch_id=#{batchId}) OR (#{batchId} IS NULL AND registration_id=#{id}))")
    int countReceiptedPayment(@Param("id") String id,@Param("batchId") String batchId);
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM ql_transaction WHERE status=\'pending_confirmation\' AND ((#{batchId} IS NOT NULL AND registration_batch_id=#{batchId}) OR (#{batchId} IS NULL AND registration_id=#{id}))")
    int countUnallocatedReceipts(@Param("id") String id,@Param("batchId") String batchId);
    int cancelPaidByRegistration(@Param("registrationId") String registrationId,
                                 @Param("operatorId") Long operatorId,
                                 @Param("remark") String remark,
                                 @Param("updatedAt") Date updatedAt);
    int cancelPaidByBatch(@Param("batchId") String batchId,
                          @Param("operatorId") Long operatorId,
                          @Param("remark") String remark,
                          @Param("updatedAt") Date updatedAt);
}
