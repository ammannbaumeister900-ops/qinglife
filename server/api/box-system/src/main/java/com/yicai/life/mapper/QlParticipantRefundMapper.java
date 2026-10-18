package com.yicai.life.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

/** Append-only refund facts; mutable request state controls reservations, never original money or seats. */
@Repository @RequiredArgsConstructor
public class QlParticipantRefundMapper {
    private final JdbcTemplate db;
    private static final String ACTIVE="'requested','processing','reconciliation_required'";
    public Map<String,Object> request(String id) {
        List<Map<String,Object>> rows=db.queryForList("SELECT id,registration_id AS registrationId,requested_amount AS amount,status,revision FROM ql_refund_request WHERE id=? FOR UPDATE",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void request(String id,String registration,String operation,BigDecimal amount,String reason,Long operator) {
        db.update("INSERT INTO ql_refund_request(id,registration_id,operation_id,requested_amount,reason,requested_by) VALUES(?,?,?,?,?,?)",id,registration,operation,amount,reason,operator);
    }
    public void reserve(String request,String allocation,BigDecimal amount) {
        db.update("INSERT INTO ql_refund_reservation(request_id,allocation_id,reserved_amount) VALUES(?,?,?)",request,allocation,amount);
    }
    public List<Map<String,Object>> reservations(String request) {
        return db.queryForList("SELECT allocation_id AS allocationId,reserved_amount AS amount FROM ql_refund_reservation WHERE request_id=? ORDER BY allocation_id FOR UPDATE",request);
    }
    public void state(String id,String state) {
        db.update("UPDATE ql_refund_request SET status=?,revision=revision+1,closed_at=CASE WHEN ? IN ('completed','cancelled','failed') THEN NOW(3) ELSE NULL END WHERE id=?",state,state,id);
    }
    public BigDecimal remaining(String allocation,String excludeRequest) {
        return db.queryForObject("SELECT a.amount-COALESCE((SELECT SUM(x.amount) FROM ql_refund_attribution x WHERE x.allocation_id=a.id),0)"+
            "-COALESCE((SELECT SUM(v.reserved_amount) FROM ql_refund_reservation v JOIN ql_refund_request r ON r.id=v.request_id WHERE v.allocation_id=a.id AND r.status IN ("+ACTIVE+") AND (? IS NULL OR r.id<>?)),0) FROM ql_receipt_allocation a WHERE a.id=?",BigDecimal.class,excludeRequest,excludeRequest,allocation);
    }
    public boolean needsReconciliation(String registration) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_refund_request r WHERE r.registration_id=? AND (r.status='reconciliation_required' OR EXISTS(SELECT 1 FROM ql_refund_payment p WHERE p.request_id=r.id AND p.status='recorded_unreconciled'))",Integer.class,registration)>0;
    }
    public boolean hasRefundExposure(String registration) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_refund_request r WHERE r.registration_id=? AND (r.status IN ("+ACTIVE+") OR EXISTS(SELECT 1 FROM ql_refund_payment p WHERE p.request_id=r.id))",Integer.class,registration)>0;
    }
    public boolean hasPayment(String request) {return db.queryForObject("SELECT COUNT(*) FROM ql_refund_payment WHERE request_id=?",Integer.class,request)>0;}
    public void payment(String id,String request,String operation,BigDecimal amount,String method,String reference,String evidence,Date occurred) {
        db.update("INSERT INTO ql_refund_payment(id,request_id,operation_id,actual_amount,payment_method,external_reference,evidence,occurred_at) VALUES(?,?,?,?,?,?,?,?)",id,request,operation,amount,method,reference,evidence,occurred);
    }
    public Map<String,Object> payment(String id) {
        return db.queryForMap("SELECT id,request_id AS requestId,actual_amount AS amount,status FROM ql_refund_payment WHERE id=? FOR UPDATE",id);
    }
    public void attribute(String payment,String allocation,BigDecimal amount) {
        db.update("INSERT INTO ql_refund_attribution(payment_id,allocation_id,amount) VALUES(?,?,?)",payment,allocation,amount);
    }
    public void applied(String payment) {db.update("UPDATE ql_refund_payment SET status='applied' WHERE id=? AND status='recorded_unreconciled'",payment);}
    public int cancel(String id,Integer revision,Long operator) {
        return db.update("UPDATE ql_registration SET registration_status='cancelled',revision=revision+1,updated_by=?,updated_at=NOW(3) WHERE id=? AND revision=? AND registration_status IN ('pending','waitlisted','confirmed')",operator,id,revision);
    }
    public boolean unresolvedForRebooking(String registration) {
        if(hasRefundExposure(registration)) {
            // Completed refunds are history, not a permanent ban. Active reservations and unallocated actual facts are blockers.
            if(needsReconciliation(registration)||db.queryForObject("SELECT COUNT(*) FROM ql_refund_request WHERE registration_id=? AND status IN ("+ACTIVE+")",Integer.class,registration)>0)return true;
        }
        BigDecimal outstanding=db.queryForObject("SELECT COALESCE(SUM(a.amount-COALESCE((SELECT SUM(x.amount) FROM ql_refund_attribution x WHERE x.allocation_id=a.id),0)),0) FROM ql_receipt_allocation a WHERE a.registration_id=? AND a.entry_type='allocate'",BigDecimal.class,registration);
        if(outstanding.signum()!=0)return true;
        return db.queryForObject("SELECT COUNT(*) FROM ql_registration r JOIN ql_transaction t ON t.registration_batch_id=r.batch_id WHERE r.id=? AND t.status IN ('paid','pending_confirmation','refund_pending') AND t.amount>COALESCE((SELECT SUM(CASE a.entry_type WHEN 'allocate' THEN a.amount ELSE -a.amount END) FROM ql_receipt_allocation a WHERE a.receipt_id=t.id),0)",Integer.class,registration)>0;
    }
    public Map<String,Object> summary(String registration) {
        return db.queryForMap("SELECT "+
            "COALESCE((SELECT SUM(p.actual_amount) FROM ql_refund_payment p JOIN ql_refund_request r ON r.id=p.request_id WHERE r.registration_id=?),0) AS refundedAmount,"+
            "COALESCE((SELECT SUM(GREATEST(COALESCE((SELECT SUM(v.reserved_amount) FROM ql_refund_reservation v WHERE v.request_id=r.id),0)-COALESCE((SELECT SUM(p.actual_amount) FROM ql_refund_payment p WHERE p.request_id=r.id AND p.status='recorded_unreconciled'),0),0)) FROM ql_refund_request r WHERE r.registration_id=? AND r.status IN ("+ACTIVE+")),0) AS refundReserved,"+
            "(SELECT COUNT(*) FROM ql_refund_request r WHERE r.registration_id=? AND r.status='processing') AS processingCount,"+
            "(SELECT COUNT(*) FROM ql_refund_request r WHERE r.registration_id=? AND r.status='requested') AS requestedCount",registration,registration,registration,registration);
    }
    public List<Map<String,Object>> history(String batch) {
        return db.queryForList("SELECT q.id AS requestId,q.registration_id AS registrationId,q.requested_amount AS requestedAmount,q.status,q.revision,q.reason,q.requested_by AS requestedBy,q.requested_at AS requestedAt,p.id AS paymentId,p.actual_amount AS actualAmount,p.payment_method AS paymentMethod,p.external_reference AS reference,p.evidence,p.status AS paymentStatus,p.occurred_at AS occurredAt FROM ql_refund_request q JOIN ql_registration r ON r.id=q.registration_id LEFT JOIN ql_refund_payment p ON p.request_id=q.id WHERE r.batch_id=? ORDER BY q.requested_at,q.id,p.id",batch);
    }
}
