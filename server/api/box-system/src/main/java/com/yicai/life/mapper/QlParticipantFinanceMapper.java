package com.yicai.life.mapper;

import com.yicai.life.domain.QlRegistration;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

/** Participant finance data only. Session/order/registration lock order is orchestrated by the service. */
@Repository @RequiredArgsConstructor
public class QlParticipantFinanceMapper {
    private final JdbcTemplate db;
    public List<String> flags() {
        return db.queryForList("SELECT config_value FROM sys_config WHERE config_key='qinglife.finance.participant_write_enabled'",String.class);
    }
    public String registrationMode(String id) {
        List<String> rows=db.queryForList("SELECT COALESCE(b.settlement_mode,'legacy_batch') FROM ql_registration r LEFT JOIN ql_registration_batch b ON b.id=r.batch_id WHERE r.id=?",String.class,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public String batchMode(String id) {
        List<String> rows=db.queryForList("SELECT settlement_mode FROM ql_registration_batch WHERE id=?",String.class,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void initializeBatch(String id) {
        db.update("UPDATE ql_registration_batch SET settlement_mode='participant' WHERE id=? AND finance_revision=0 AND settlement_mode='legacy_batch'",id);
    }
    public void singleBatch(String id,String number,QlRegistration r) {
        db.update("INSERT INTO ql_registration_batch(id,order_no,session_id,submitted_by_customer_id,source,participant_count,quoted_amount,payable_amount,submitted_at,settlement_mode) VALUES(?,?,?,?,'web_admin',1,?,?,NOW(3),'participant')",
            id,number,r.getSessionId(),r.getCustomerId(),r.getUnitPrice(),r.getUnitPrice());
    }
    public Map<String,Object> batch(String id,boolean lock) {
        List<Map<String,Object>> rows=db.queryForList("SELECT id,session_id AS sessionId,settlement_mode AS mode,finance_revision AS revision,quoted_amount AS quotedAmount,final_amount AS finalAmount,payment_status AS paymentStatus,settlement_status AS settlementStatus,submitted_by_customer_id AS buyerCustomerId FROM ql_registration_batch WHERE id=?"+(lock?" FOR UPDATE":""),id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void advanceBatch(String id) { db.update("UPDATE ql_registration_batch SET finance_revision=finance_revision+1 WHERE id=?",id); }
    public Map<String,Object> operation(String id,String action,String hash,String target,Long operator) {
        db.update("INSERT INTO ql_finance_operation(id,action,request_hash,target_id,operator_id) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",id,action,hash,target,operator);
        return db.queryForMap("SELECT action,request_hash AS requestHash,target_id AS targetId,operator_id AS operatorId,result_json AS resultJson FROM ql_finance_operation WHERE id=? FOR UPDATE",id);
    }
    public void result(String id,String json) { db.update("UPDATE ql_finance_operation SET result_json=? WHERE id=? AND result_json IS NULL",json,id); }
    public List<Map<String,Object>> participants(String batch) {
        return db.queryForList("SELECT r.id,r.customer_id AS customerId,r.session_id AS sessionId,COALESCE(NULLIF(r.participant_name_snapshot,''),c.nickname) AS name,r.current_settlement_id AS currentSettlementId,r.pass_account_id AS passAccountId,r.pass_units AS passUnits,r.unit_price AS quotedAmount,r.final_amount AS finalAmount,r.registration_status AS registrationStatus,r.payment_status AS paymentStatus,r.settlement_status AS settlementStatus,r.settlement_type AS settlementType,r.revision,COALESCE((SELECT SUM(CASE a.entry_type WHEN 'allocate' THEN a.amount ELSE -a.amount END) FROM ql_receipt_allocation a WHERE a.registration_id=r.id),0) AS allocatedAmount FROM ql_registration r LEFT JOIN ql_customer c ON c.id=r.customer_id WHERE r.batch_id=? ORDER BY r.created_at,r.id",batch);
    }
    public List<Map<String,Object>> receipts(String batch) {
        return db.queryForList("SELECT t.id,t.amount,t.status,t.revision,t.payment_method AS paymentMethod,t.transaction_at AS receivedAt,t.external_transaction_no AS reference,t.remark AS evidence,t.operator_id AS operatorId,COALESCE((SELECT SUM(CASE a.entry_type WHEN 'allocate' THEN a.amount ELSE -a.amount END) FROM ql_receipt_allocation a WHERE a.receipt_id=t.id),0) AS allocatedAmount FROM ql_transaction t WHERE t.registration_batch_id=? AND t.transaction_type='session' ORDER BY t.created_at,t.id",batch);
    }
    public List<Map<String,Object>> allocationHistory(String batch) {
        return db.queryForList("SELECT a.id AS allocationId,a.receipt_id AS receiptId,a.registration_id AS registrationId,a.amount,a.entry_type AS entryType,a.original_allocation_id AS originalAllocationId,a.operation_id AS operationId,a.created_at AS createdAt,f.settlement_id AS settlementId FROM ql_receipt_allocation a JOIN ql_registration r ON r.id=a.registration_id LEFT JOIN ql_participant_settlement_funding f ON f.allocation_id=a.id WHERE r.batch_id=? ORDER BY a.created_at,a.id",batch);
    }
    public BigDecimal allocated(String receipt) {
        return db.queryForObject("SELECT COALESCE(SUM(CASE entry_type WHEN 'allocate' THEN amount ELSE -amount END),0) FROM ql_receipt_allocation WHERE receipt_id=?",BigDecimal.class,receipt);
    }
    public void allocation(String id,String operation,int line,String receipt,String registration,BigDecimal amount,String reason) {
        db.update("INSERT INTO ql_receipt_allocation(id,operation_id,line_no,receipt_id,registration_id,amount,entry_type,reason) VALUES(?,?,?,?,?,?,'allocate',?)",id,operation,line,receipt,registration,amount,reason);
    }
    public List<Map<String,Object>> allocations(String registration) {
        return db.queryForList("SELECT a.id,a.receipt_id AS receiptId,a.amount,a.entry_type AS entryType,t.registration_batch_id AS batchId,t.status AS receiptStatus,t.transaction_type AS transactionType,"+
            "(SELECT COUNT(*) FROM ql_participant_settlement_funding f WHERE f.allocation_id=a.id) AS bound,"+
            "(SELECT COUNT(*) FROM ql_receipt_allocation rev WHERE rev.original_allocation_id=a.id) AS reversed "+
            "FROM ql_receipt_allocation a JOIN ql_transaction t ON t.id=a.receipt_id WHERE a.registration_id=? ORDER BY a.receipt_id,a.id FOR UPDATE",registration);
    }
    public boolean hasPass(String id) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_pass_ledger l JOIN ql_registration r ON r.id=? WHERE l.entry_type='consume' AND (l.registration_id=r.id OR l.registration_batch_id=r.batch_id) AND NOT EXISTS(SELECT 1 FROM ql_settlement_reversal v WHERE v.original_pass_ledger_id=l.id)",Integer.class,id)>0;
    }
    public boolean hasBatchSettlement(String batch) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_settlement_log WHERE registration_batch_id=?",Integer.class,batch)>0;
    }
    public boolean hasUnlinkedReceipt(String id) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_transaction t JOIN ql_registration r ON r.id=? WHERE t.transaction_type='session' AND t.status IN ('paid','pending_confirmation','refund_pending') AND (t.registration_id=r.id OR (t.registration_id IS NULL AND t.registration_batch_id IS NULL AND t.customer_id=r.customer_id AND t.session_id=r.session_id))",Integer.class,id)>0;
    }
    public void advanceReceipt(String id) { db.update("UPDATE ql_transaction SET revision=revision+1,updated_at=NOW(3) WHERE id=?",id); }
    public void advanceRegistration(String id,Long operator) {
        db.update("UPDATE ql_registration SET revision=revision+1,updated_by=?,updated_at=NOW(3) WHERE id=?",operator,id);
    }
    public int confirmPerson(String id,String round,Long operator,Integer revision) {
        // The reused settlement mapper already advanced this operation's revision under the same row lock/transaction.
        return db.update("UPDATE ql_registration SET payment_status='paid',registration_status='confirmed',confirmed_by=?,confirmed_at=COALESCE(confirmed_at,NOW(3)) WHERE id=? AND revision=? AND current_settlement_id=? AND settlement_status='confirmed' AND payment_status='unpaid' AND registration_status IN ('pending','waitlisted')",
            operator,id,revision+1,round);
    }
    public void fund(String round,String allocation) { db.update("INSERT INTO ql_participant_settlement_funding(settlement_id,allocation_id) VALUES(?,?)",round,allocation); }
}
