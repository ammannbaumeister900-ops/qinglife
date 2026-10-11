package com.yicai.life.mapper;
import org.apache.ibatis.annotations.*;
import com.yicai.life.domain.QlTransaction;
import java.util.*;
public interface QlOfflineReceiptMapper {
    @Select("SELECT * FROM ql_transaction WHERE external_transaction_no=#{reference} FOR UPDATE")
    QlTransaction byReference(@Param("reference") String reference);
    @Select("SELECT * FROM ql_transaction WHERE receipt_request_id=#{key} FOR UPDATE")
    QlTransaction byRequest(@Param("key") String key);
    @Select("SELECT * FROM ql_transaction WHERE id=#{id} FOR UPDATE")
    QlTransaction lockReceipt(@Param("id") String id);
    @Select("SELECT id,receipt_request_id AS requestId,revision,expected_amount AS expectedAmount,final_settlement_confirmed AS finalSettlementConfirmed,difference_reason AS differenceReason,partial_payment AS partialPayment,amount,payment_method AS paymentMethod,external_transaction_no AS receiptReference,remark AS evidence,status,operator_id AS operatorId,transaction_at AS recordedAt," +
        "(SELECT l.change_reason FROM ql_transaction_status_log l WHERE l.transaction_id=t.id ORDER BY l.changed_at DESC,l.id DESC LIMIT 1) AS processingMessage " +
        "FROM ql_transaction t WHERE (#{batchId} IS NOT NULL AND t.registration_batch_id=#{batchId}) OR (#{batchId} IS NULL AND t.registration_id=#{id}) ORDER BY t.created_at,t.id")
    List<Map<String,Object>> list(@Param("id") String id,@Param("batchId") String batchId);
    @Update("UPDATE ql_transaction SET amount=#{r.amount},payment_method=#{r.paymentMethod},external_transaction_no=#{r.externalTransactionNo},remark=#{r.remark},expected_amount=#{r.expectedAmount},final_settlement_confirmed=#{r.finalSettlementConfirmed},difference_reason=#{r.differenceReason},partial_payment=#{r.partialPayment},revision=revision+1,updated_at=#{now} WHERE id=#{r.id} AND revision=#{r.revision} AND status='pending_confirmation'")
    int correct(@Param("r") QlTransaction receipt,@Param("now") Date now);
    @Insert("INSERT INTO ql_offline_receipt_revision(id,transaction_id,revision,before_value,after_value,reason,operator_id,changed_at) VALUES(#{id},#{receipt},#{revision},#{before},#{after},#{reason},#{operator},#{now})")
    int revision(@Param("id") String id,@Param("receipt") String receipt,@Param("revision") int revision,@Param("before") String before,@Param("after") String after,@Param("reason") String reason,@Param("operator") Long operator,@Param("now") Date now);
    @Select("SELECT revision,before_value AS beforeValue,after_value AS afterValue,reason,operator_id AS operatorId,changed_at AS changedAt FROM ql_offline_receipt_revision WHERE transaction_id=#{id} ORDER BY revision")
    List<Map<String,Object>> revisions(@Param("id") String id);
    @Update("UPDATE ql_transaction SET status='paid',receipt_request_id=COALESCE(receipt_request_id,UUID()),revision=revision+1,updated_at=#{now} WHERE id=#{id} AND status='pending_confirmation'")
    int allocate(@Param("id") String id,@Param("now") Date now);
    @Insert("INSERT INTO ql_transaction_status_log(id,transaction_id,from_status,to_status,change_reason,operator_id,changed_at) VALUES(#{id},#{receiptId},#{from},#{to},#{reason},#{operator},#{now})")
    int audit(@Param("id") String id,@Param("receiptId") String receiptId,@Param("from") String from,@Param("to") String to,@Param("reason") String reason,@Param("operator") Long operator,@Param("now") Date now);
    @Update("UPDATE ql_registration SET registration_status='confirmed',confirmed_at=COALESCE(confirmed_at,#{now}),confirmed_by=COALESCE(confirmed_by,#{operator}),updated_at=#{now},updated_by=#{operator},revision=revision+1 WHERE id=#{id} AND registration_status=#{from} AND payment_status='unpaid'")
    int confirm(@Param("id") String id,@Param("from") String from,@Param("operator") Long operator,@Param("now") Date now);
    @Select("SELECT COUNT(*) FROM ql_transaction t JOIN ql_registration r ON r.id=#{id} WHERE t.id<>#{receiptId} AND t.transaction_type='session' AND t.status IN ('pending_confirmation','paid','refund_pending') AND " +
        "(t.registration_id=r.id OR (r.batch_id IS NOT NULL AND t.registration_batch_id=r.batch_id) OR " +
        "(t.registration_id IS NULL AND t.registration_batch_id IS NULL AND t.customer_id=r.customer_id AND t.session_id=r.session_id))")
    int conflictingReceipts(@Param("id") String id,@Param("receiptId") String receiptId);
    @Select("SELECT COUNT(*) FROM ql_pass_ledger l JOIN ql_registration r ON r.id=#{id} WHERE l.entry_type='consume' AND (l.registration_id=r.id OR l.registration_batch_id=r.batch_id) AND NOT EXISTS(SELECT 1 FROM ql_settlement_reversal v WHERE v.original_pass_ledger_id=l.id)")
    int unreversedPass(@Param("id") String id);
    @Select("SELECT COUNT(*) FROM ql_settlement_log l JOIN ql_registration r ON r.id=#{id} WHERE (l.registration_id=r.id OR l.registration_batch_id=r.batch_id) AND (#{currentId} IS NULL OR l.id<>#{currentId}) AND NOT EXISTS(SELECT 1 FROM ql_settlement_reversal v WHERE v.settlement_id=l.id)")
    int unreversedSettlements(@Param("id") String id,@Param("currentId") String currentId);
}
