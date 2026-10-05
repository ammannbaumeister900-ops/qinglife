package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
import com.yicai.common.utils.file.UploadContentValidator;
import com.yicai.life.domain.bo.QlPassAccountBo;
import com.yicai.life.domain.bo.QlPassAdjustmentBo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QlPassService {
    private final JdbcTemplate db;

    public List<Map<String,Object>> list(String customerId) {
        String where = customerId == null || customerId.trim().isEmpty() ? "" : " WHERE a.customer_id=?";
        String sql = "SELECT a.id,a.customer_id AS customerId,c.customer_no AS customerNo,c.nickname,c.real_name AS realName," +
                "a.pass_type AS passType,a.status,a.created_at AS createdAt,(a.image_data IS NOT NULL) AS hasImage,DATE_FORMAT(a.valid_from,'%Y-%m-%d') AS validFrom," +
                "DATE_FORMAT(a.valid_until,'%Y-%m-%d') AS validUntil,COALESCE(SUM(l.quantity_delta),0) AS balance," +
                "CASE WHEN a.status='active' AND (a.valid_from IS NULL OR a.valid_from<=CURRENT_DATE()) " +
                "AND (a.valid_until IS NULL OR a.valid_until>=CURRENT_DATE()) THEN 1 ELSE 0 END AS usable " +
                "FROM ql_pass_account a JOIN ql_customer c ON c.id=a.customer_id " +
                "LEFT JOIN ql_pass_ledger l ON l.pass_account_id=a.id" + where +
                " GROUP BY a.id,a.customer_id,c.customer_no,c.nickname,c.real_name,a.pass_type,a.status,a.valid_from,a.valid_until,a.created_at " +
                "ORDER BY a.created_at DESC,a.id DESC";
        return where.isEmpty() ? db.queryForList(sql) : db.queryForList(sql, customerId);
    }

    public List<Map<String,Object>> ledger(String accountId) {
        return db.queryForList("SELECT h.id,h.entryType,h.quantityDelta,h.balanceAfter,h.reason,h.registrationId,h.registrationBatchId,h.sessionNumber," +
                "h.previousValidFrom,h.previousValidUntil,h.validFrom,h.validUntil,DATE_FORMAT(h.occurredAt,'%Y-%m-%d %H:%i:%s') AS occurredAt,u.nick_name AS operatorName FROM (" +
                "SELECT l.id,l.entry_type AS entryType,l.quantity_delta AS quantityDelta,l.balance_after AS balanceAfter," +
                "l.reason,l.occurred_at AS occurredAt,l.registration_id AS registrationId," +
                "l.registration_batch_id AS registrationBatchId,s.session_number AS sessionNumber,l.operator_id," +
                "NULL AS previousValidFrom,NULL AS previousValidUntil,NULL AS validFrom,NULL AS validUntil " +
                "FROM ql_pass_ledger l LEFT JOIN ql_session s ON s.id=l.session_id WHERE l.pass_account_id=? " +
                "UNION ALL SELECT v.id,'validity',NULL,NULL,v.reason,v.created_at,NULL,NULL,NULL,v.operator_id," +
                "DATE_FORMAT(v.previous_valid_from,'%Y-%m-%d'),DATE_FORMAT(v.previous_valid_until,'%Y-%m-%d')," +
                "DATE_FORMAT(v.valid_from,'%Y-%m-%d'),DATE_FORMAT(v.valid_until,'%Y-%m-%d') " +
                "FROM ql_pass_validity_log v WHERE v.pass_account_id=?" +
                ") h LEFT JOIN sys_user u ON u.user_id=h.operator_id ORDER BY h.occurredAt DESC,h.id DESC",accountId,accountId);
    }

    public Map<String,Object> image(String accountId) {
        List<Map<String,Object>> rows=db.queryForList("SELECT image_mime AS mime,image_data AS data FROM ql_pass_account WHERE id=? AND image_data IS NOT NULL",accountId);
        if(rows.isEmpty()) throw new CustomException("图片不存在",404);
        return rows.get(0);
    }

    @Transactional
    public String open(QlPassAccountBo bo, Long operatorId) {
        if (bo.getValidFrom() != null && bo.getValidUntil() != null && bo.getValidUntil().before(bo.getValidFrom())) {
            throw new CustomException("结束日期不能早于开始日期", 400);
        }
        Integer customers = db.queryForObject("SELECT COUNT(*) FROM ql_customer WHERE id=? AND deleted_at IS NULL", Integer.class, bo.getCustomerId());
        if (customers == null || customers == 0) throw new CustomException("轻友档案不存在", 404);
        byte[] image = bo.getImage() == null ? null : UploadContentValidator.decodeInterviewImage(bo.getImage().getMime(), bo.getImage().getData());
        String id = UUID.randomUUID().toString();
        java.util.Date now = new java.util.Date();
        db.update("INSERT INTO ql_pass_account(id,customer_id,pass_type,valid_from,valid_until,status,created_by,created_at,updated_at,image_mime,image_data) VALUES(?,?,?,?,?,'active',?,?,?,?,?)",
                id, bo.getCustomerId(), bo.getPassType().trim(), sqlDate(bo.getValidFrom()), sqlDate(bo.getValidUntil()), operatorId, now, now, image == null ? null : bo.getImage().getMime(), image);
        insertLedger(id, null, null, null, null, "grant", bo.getInitialUnits(), bo.getInitialUnits(), bo.getReason() == null ? null : bo.getReason().trim(), operatorId, now);
        return id;
    }

    @Transactional
    public int adjust(String accountId, QlPassAdjustmentBo bo, Long operatorId) {
        if (bo.getQuantityDelta() == null) throw new CustomException("请填写调整次数",400);
        if (bo.getReason() == null || bo.getReason().trim().isEmpty() || bo.getReason().trim().length()>500) throw new CustomException("请填写500字以内的调整原因",400);
        Map<String,Object> account = lock(accountId);
        Date from=sqlDate(bo.getValidFrom()),until=sqlDate(bo.getValidUntil());
        if(bo.isChangeValidity() && from!=null && until!=null && until.before(from)) throw new CustomException("结束日期不能早于开始日期",400);
        boolean datesChanged=bo.isChangeValidity() && (!Objects.equals(from,account.get("validFrom")) || !Objects.equals(until,account.get("validUntil")));
        if(bo.getQuantityDelta()==0 && !datesChanged) throw new CustomException("请调整次数或有效期",400);
        int current = number(account.get("balance"));
        long calculated = (long) current + bo.getQuantityDelta();
        if (calculated < 0 || calculated > Integer.MAX_VALUE) throw new CustomException("调整后的卡次余额无效", 400);
        int after = (int) calculated;
        java.util.Date now=new java.util.Date();
        if(datesChanged) {
            db.update("INSERT INTO ql_pass_validity_log(id,pass_account_id,previous_valid_from,previous_valid_until,valid_from,valid_until,reason,operator_id,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(),accountId,account.get("validFrom"),account.get("validUntil"),from,until,bo.getReason().trim(),operatorId,now);
            db.update("UPDATE ql_pass_account SET valid_from=?,valid_until=?,updated_at=? WHERE id=?",from,until,now,accountId);
        }
        if(bo.getQuantityDelta()!=0) insertLedger(accountId, null, null, null, null, "adjust", bo.getQuantityDelta(), after, bo.getReason().trim(), operatorId, now);
        return after;
    }

    /** Consumes one account for an exact settlement round. Caller owns the transaction. */
    public int consume(String accountId, String expectedCustomerId, String sessionId, String registrationId,
                       String batchId, String settlementId, int units, String reason, Long operatorId,
                       java.util.Date now) {
        if (accountId == null || accountId.trim().isEmpty()) throw new CustomException("请选择要使用的卡次账户", 400);
        if (settlementId == null || settlementId.trim().isEmpty()) throw new CustomException("结算轮次缺失，无法关联扣卡流水", 400);
        if (units < 1) throw new CustomException("使用卡次至少为1次", 400);
        Map<String,Object> account = lock(accountId);
        if (!expectedCustomerId.equals(String.valueOf(account.get("customerId")))) throw new CustomException("卡次账户不属于本次报名发起人或单人报名参与者", 400);
        if (!"active".equals(String.valueOf(account.get("status"))) || number(account.get("usable")) != 1) throw new CustomException("卡次账户当前不可用", 400);
        int current = number(account.get("balance"));
        if (current < units) throw new CustomException("卡次余额不足，当前剩余" + current + "次", 400);
        int after = current - units;
        insertLedger(accountId, sessionId, registrationId, batchId, settlementId, "consume", -units, after,
                reason == null || reason.trim().isEmpty() ? "报名结算使用" : reason.trim(), operatorId, now);
        return after;
    }

    /**
     * Appends a reversal debit only from the immutable consume row for this settlement round.
     * Inactive and expired accounts are intentionally accepted: the original account is restored
     * without changing its status or validity dates.
     */
    public PassReversal reverseConsumption(String settlementId, String expectedCustomerId, String sessionId,
                                           String registrationId, String batchId, String accountId, Integer units,
                                           String reason, Long operatorId, java.util.Date now) {
        if (settlementId == null || settlementId.trim().isEmpty()
                || accountId == null || accountId.trim().isEmpty() || units == null || units < 1) {
            throw new CustomException("原结算卡次信息不完整，无法安全撤销", 409);
        }
        Map<String,Object> account = lock(accountId);
        if (!expectedCustomerId.equals(String.valueOf(account.get("customerId")))) {
            throw new CustomException("原结算卡次账户归属与报名人不一致，无法安全退回", 409);
        }
        List<Map<String,Object>> rows = db.queryForList(
                "SELECT id,pass_account_id AS passAccountId,session_id AS sessionId," +
                        "registration_id AS registrationId,registration_batch_id AS registrationBatchId," +
                        "quantity_delta AS quantityDelta FROM ql_pass_ledger " +
                        "WHERE settlement_id=? AND entry_type='consume' FOR UPDATE", settlementId);
        if (rows.size() != 1) {
            throw new CustomException("原结算扣卡流水缺失或不唯一，无法确认已扣次数，已阻止撤销", 409);
        }
        Map<String,Object> debit = rows.get(0);
        int delta = number(debit.get("quantityDelta"));
        if (!accountId.equals(String.valueOf(debit.get("passAccountId")))
                || !Objects.equals(sessionId, debit.get("sessionId"))
                || !Objects.equals(registrationId, debit.get("registrationId"))
                || !Objects.equals(batchId, debit.get("registrationBatchId"))
                || delta >= 0 || delta == Integer.MIN_VALUE || -delta != units) {
            throw new CustomException("原结算与扣卡流水的账户、归属或次数不一致，已阻止撤销", 409);
        }
        Integer previousReversal = db.queryForObject(
                "SELECT COUNT(*) FROM ql_pass_ledger WHERE settlement_id=? AND entry_type='void'",
                Integer.class, settlementId);
        if (previousReversal != null && previousReversal != 0) {
            throw new CustomException("原结算已有退卡流水但撤销记录缺失，已阻止重复处理", 409);
        }
        int current = number(account.get("balance"));
        long calculated = (long) current + units;
        if (calculated > Integer.MAX_VALUE) throw new CustomException("原账户退回后余额超出可处理范围", 409);
        int after = (int) calculated;
        String ledgerReason = reason.trim();
        insertLedger(accountId, sessionId, registrationId, batchId, settlementId, "void", units, after,
                ledgerReason, operatorId, now);
        return new PassReversal(String.valueOf(debit.get("id")), after);
    }

    public static final class PassReversal {
        private final String originalLedgerId;
        private final int balanceAfter;

        public PassReversal(String originalLedgerId, int balanceAfter) {
            this.originalLedgerId = originalLedgerId;
            this.balanceAfter = balanceAfter;
        }

        public String getOriginalLedgerId() { return originalLedgerId; }
        public int getBalanceAfter() { return balanceAfter; }
    }

    private Map<String,Object> lock(String accountId) {
        List<Map<String,Object>> rows = db.queryForList("SELECT a.id,a.customer_id AS customerId,a.status,a.valid_from AS validFrom,a.valid_until AS validUntil," +
                "CASE WHEN a.status='active' AND (a.valid_from IS NULL OR a.valid_from<=CURRENT_DATE()) " +
                "AND (a.valid_until IS NULL OR a.valid_until>=CURRENT_DATE()) THEN 1 ELSE 0 END AS usable " +
                "FROM ql_pass_account a WHERE a.id=? FOR UPDATE", accountId);
        if (rows.isEmpty()) throw new CustomException("卡次账户不存在", 404);
        // A locking read sees committed ledger entries after waiting for the account lock,
        // even when this transaction already has an older repeatable-read snapshot.
        long balance = 0;
        for (Map<String,Object> entry : db.queryForList(
                "SELECT quantity_delta FROM ql_pass_ledger WHERE pass_account_id=? FOR UPDATE", accountId)) {
            balance += ((Number) entry.get("quantity_delta")).longValue();
        }
        if (balance < Integer.MIN_VALUE || balance > Integer.MAX_VALUE) throw new CustomException("卡次余额超出可处理范围", 400);
        rows.get(0).put("balance", (int) balance);
        return rows.get(0);
    }

    private void insertLedger(String accountId, String sessionId, String registrationId, String batchId,
                              String settlementId, String type, int delta, int after, String reason,
                              Long operatorId, java.util.Date now) {
        db.update("INSERT INTO ql_pass_ledger(id,pass_account_id,session_id,registration_id,registration_batch_id,settlement_id,entry_type,quantity_delta,balance_after,reason,occurred_at,operator_id,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, sessionId, registrationId, batchId, settlementId,
                type, delta, after, reason, now, operatorId, now);
    }

    private int number(Object value) { return value == null ? 0 : ((Number)value).intValue(); }
    private Date sqlDate(java.util.Date value) { return value == null ? null : new Date(value.getTime()); }
}
