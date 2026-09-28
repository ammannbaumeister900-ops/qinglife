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
import java.math.BigDecimal;

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
    @org.apache.ibatis.annotations.Select("SELECT session_id FROM ql_registration_batch WHERE id=#{batchId}")
    String selectBatchSessionId(@Param("batchId") String batchId);
    Map<String, Object> selectBatchForPayment(@Param("batchId") String batchId);
    Map<String, Object> selectSettlementLogForUpdate(@Param("settlementId") String settlementId);
    Map<String, Object> selectSettlementReversalForUpdate(@Param("settlementId") String settlementId);
    int countPaidTransactionsByBatch(@Param("batchId") String batchId);
    int countPaidTransactionsByRegistration(@Param("registrationId") String registrationId);
    int countPassLedgerEntriesBySettlement(@Param("settlementId") String settlementId);
    int updateBatchSettlementToPending(@Param("batchId") String batchId,
                                       @Param("settlementId") String settlementId);
    int updateRegistrationSettlementToPending(@Param("registrationId") String registrationId,
                                              @Param("settlementId") String settlementId,
                                              @Param("operatorId") Long operatorId,
                                              @Param("updatedAt") Date updatedAt);
    int insertSettlementReversal(@Param("settlementId") String settlementId,
                                 @Param("registrationId") String registrationId,
                                 @Param("batchId") String batchId,
                                 @Param("finalAmount") BigDecimal finalAmount,
                                 @Param("settlementType") String settlementType,
                                 @Param("passAccountId") String passAccountId,
                                 @Param("passUnits") Integer passUnits,
                                 @Param("originalPassLedgerId") String originalPassLedgerId,
                                 @Param("passBalanceAfter") Integer passBalanceAfter,
                                 @Param("reason") String reason,
                                 @Param("operatorId") Long operatorId,
                                 @Param("reversedAt") Date reversedAt);

    int updateSettlementLogBalance(@Param("settlementId") String settlementId,
                                   @Param("passBalanceAfter") Integer passBalanceAfter);
    List<String> selectRegistrationIdsByBatch(@Param("batchId") String batchId);
    int updateBatchPaymentStatus(@Param("batchId") String batchId, @Param("fromStatus") String fromStatus,
                                 @Param("toStatus") String toStatus, @Param("operatorId") Long operatorId,
                                 @Param("updatedAt") Date updatedAt);
    int updateRegistrationPaymentByBatch(@Param("batchId") String batchId, @Param("toStatus") String toStatus,
                                         @Param("operatorId") Long operatorId, @Param("updatedAt") Date updatedAt);
    int confirmBatchSettlement(@Param("batchId") String batchId, @Param("settlementId") String settlementId,
                               @Param("finalAmount") BigDecimal finalAmount,
                               @Param("settlementType") String settlementType, @Param("passUnits") Integer passUnits,
                               @Param("passAccountId") String passAccountId,
                               @Param("note") String note, @Param("operatorId") Long operatorId,
                               @Param("confirmedAt") Date confirmedAt);
    int confirmRegistrationSettlement(@Param("id") String id, @Param("settlementId") String settlementId,
                                      @Param("finalAmount") BigDecimal finalAmount,
                                      @Param("settlementType") String settlementType, @Param("passUnits") Integer passUnits,
                                      @Param("passAccountId") String passAccountId,
                                      @Param("note") String note, @Param("operatorId") Long operatorId,
                                      @Param("confirmedAt") Date confirmedAt);
    @org.apache.ibatis.annotations.Insert("INSERT INTO ql_settlement_log(id,registration_id,registration_batch_id,quoted_amount,final_amount,settlement_type,pass_units,pass_account_id,pass_balance_after,note,operator_id,confirmed_at) VALUES(#{id},#{registrationId},#{batchId},#{quotedAmount},#{finalAmount},#{settlementType},#{passUnits},#{passAccountId},#{passBalanceAfter},#{note},#{operatorId},#{confirmedAt})")
    int insertSettlementLog(@Param("id") String id, @Param("registrationId") String registrationId,
                            @Param("batchId") String batchId, @Param("quotedAmount") BigDecimal quotedAmount,
                            @Param("finalAmount") BigDecimal finalAmount, @Param("settlementType") String settlementType,
                            @Param("passUnits") Integer passUnits, @Param("passAccountId") String passAccountId,
                            @Param("passBalanceAfter") Integer passBalanceAfter, @Param("note") String note,
                            @Param("operatorId") Long operatorId, @Param("confirmedAt") Date confirmedAt);
}
