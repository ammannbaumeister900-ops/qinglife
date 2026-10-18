package com.yicai.life.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @RequiredArgsConstructor
public class QlParticipantPassMapper {
    private final JdbcTemplate db;
    public void verify(String id,String operation,String registration,int revision,String batch,String account,String holder,String beneficiary,int units,String scope,String basis,Long operator) {
        db.update("INSERT INTO ql_pass_operator_verification(id,operation_id,registration_id,registration_revision,batch_id,pass_account_id,holder_customer_id,beneficiary_customer_id,units,scope,basis,operator_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            id,operation,registration,revision,batch,account,holder,beneficiary,units,scope,basis,operator);
    }
    public Map<String,Object> verification(String id) {
        List<Map<String,Object>> rows=db.queryForList("SELECT v.id,v.registration_id AS registrationId,v.registration_revision AS revision,v.batch_id AS batchId,v.pass_account_id AS accountId,v.holder_customer_id AS holderId,v.beneficiary_customer_id AS beneficiaryId,v.units,v.scope,(SELECT COUNT(*) FROM ql_pass_verification_revocation x WHERE x.verification_id=v.id) AS revoked,(SELECT COUNT(*) FROM ql_settlement_log l WHERE l.pass_verification_id=v.id) AS used FROM ql_pass_operator_verification v WHERE v.id=? FOR UPDATE",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void revokeVerification(String verification,String operation,String reason,Long operator) {
        db.update("INSERT INTO ql_pass_verification_revocation(verification_id,operation_id,reason,operator_id) VALUES(?,?,?,?)",verification,operation,reason,operator);
    }
    public void bind(String round,String verification) {db.update("UPDATE ql_settlement_log SET pass_verification_id=? WHERE id=? AND pass_verification_id IS NULL",verification,round);}
    public int confirm(String id,String round,int revision,Long operator) {
        return db.update("UPDATE ql_registration SET registration_status='confirmed',confirmed_by=?,confirmed_at=COALESCE(confirmed_at,NOW(3)) WHERE id=? AND revision=? AND current_settlement_id=? AND settlement_status='confirmed' AND settlement_type='pass' AND payment_status='unpaid' AND registration_status IN ('pending','waitlisted','confirmed')",operator,id,revision+1,round);
    }
    public boolean previouslyReturned(String registration) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_settlement_reversal WHERE registration_id=? AND original_settlement_type='pass'",Integer.class,registration)>0;
    }
    public boolean activeCustomer(String customer) {
        return db.queryForObject("SELECT COUNT(*) FROM ql_customer WHERE id=? AND deleted_at IS NULL AND status='active'",Integer.class,customer)>0;
    }
    public List<Map<String,Object>> history(String batch) {
        return db.queryForList("SELECT l.registration_id AS registrationId,l.id AS settlementId,l.pass_verification_id AS verificationId,v.holder_customer_id AS holderCustomerId,v.beneficiary_customer_id AS beneficiaryCustomerId,l.pass_account_id AS passAccountId,l.pass_units AS passUnits,l.pass_balance_after AS balanceAfter,l.confirmed_at AS confirmedAt,d.id AS consumeLedgerId,x.original_pass_ledger_id AS originalLedgerId,x.pass_balance_after AS returnedBalance,x.reversed_at AS returnedAt FROM ql_settlement_log l JOIN ql_registration r ON r.id=l.registration_id JOIN ql_pass_operator_verification v ON v.id=l.pass_verification_id LEFT JOIN ql_pass_ledger d ON d.settlement_id=l.id AND d.entry_type='consume' LEFT JOIN ql_settlement_reversal x ON x.settlement_id=l.id WHERE r.batch_id=? ORDER BY l.confirmed_at,l.id",batch);
    }
    public List<Map<String,Object>> verifications(String batch) {
        return db.queryForList("SELECT v.id AS verificationId,v.registration_id AS registrationId,v.pass_account_id AS passAccountId,v.holder_customer_id AS holderCustomerId,v.beneficiary_customer_id AS beneficiaryCustomerId,v.units AS passUnits,v.scope,v.registration_revision AS verifiedRevision,v.verified_at AS verifiedAt,CASE WHEN x.verification_id IS NOT NULL THEN 'revoked' WHEN l.id IS NOT NULL THEN 'used' WHEN v.registration_revision<>r.revision OR r.registration_status='cancelled' THEN 'superseded' ELSE 'verified' END AS state FROM ql_pass_operator_verification v JOIN ql_registration r ON r.id=v.registration_id LEFT JOIN ql_pass_verification_revocation x ON x.verification_id=v.id LEFT JOIN ql_settlement_log l ON l.pass_verification_id=v.id WHERE v.batch_id=? ORDER BY v.verified_at,v.id",batch);
    }
}
