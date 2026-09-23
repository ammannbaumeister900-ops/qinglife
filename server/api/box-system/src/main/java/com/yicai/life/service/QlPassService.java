package com.yicai.life.service;

import com.yicai.common.exception.CustomException;
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
                "a.pass_type AS passType,a.status,DATE_FORMAT(a.valid_from,'%Y-%m-%d') AS validFrom," +
                "DATE_FORMAT(a.valid_until,'%Y-%m-%d') AS validUntil,COALESCE(SUM(l.quantity_delta),0) AS balance," +
                "CASE WHEN a.status='active' AND (a.valid_from IS NULL OR a.valid_from<=CURRENT_DATE()) " +
                "AND (a.valid_until IS NULL OR a.valid_until>=CURRENT_DATE()) THEN 1 ELSE 0 END AS usable " +
                "FROM ql_pass_account a JOIN ql_customer c ON c.id=a.customer_id " +
                "LEFT JOIN ql_pass_ledger l ON l.pass_account_id=a.id" + where +
                " GROUP BY a.id,a.customer_id,c.customer_no,c.nickname,c.real_name,a.pass_type,a.status,a.valid_from,a.valid_until " +
                "ORDER BY a.created_at DESC";
        return where.isEmpty() ? db.queryForList(sql) : db.queryForList(sql, customerId);
    }

    public List<Map<String,Object>> ledger(String accountId) {
        return db.queryForList("SELECT l.id,l.entry_type AS entryType,l.quantity_delta AS quantityDelta," +
                "l.balance_after AS balanceAfter,l.reason,DATE_FORMAT(l.occurred_at,'%Y-%m-%d %H:%i:%s') AS occurredAt," +
                "l.registration_id AS registrationId,l.registration_batch_id AS registrationBatchId," +
                "s.session_number AS sessionNumber,u.nick_name AS operatorName " +
                "FROM ql_pass_ledger l LEFT JOIN ql_session s ON s.id=l.session_id " +
                "LEFT JOIN sys_user u ON u.user_id=l.operator_id WHERE l.pass_account_id=? " +
                "ORDER BY l.occurred_at DESC,l.created_at DESC", accountId);
    }

    @Transactional
    public String open(QlPassAccountBo bo, Long operatorId) {
        if (bo.getValidFrom() != null && bo.getValidUntil() != null && bo.getValidUntil().before(bo.getValidFrom())) {
            throw new CustomException("结束日期不能早于开始日期", 400);
        }
        Integer customers = db.queryForObject("SELECT COUNT(*) FROM ql_customer WHERE id=? AND deleted_at IS NULL", Integer.class, bo.getCustomerId());
        if (customers == null || customers == 0) throw new CustomException("轻友档案不存在", 404);
        String id = UUID.randomUUID().toString();
        java.util.Date now = new java.util.Date();
        db.update("INSERT INTO ql_pass_account(id,customer_id,pass_type,valid_from,valid_until,status,created_by,created_at,updated_at) VALUES(?,?,?,?,?,'active',?,?,?)",
                id, bo.getCustomerId(), bo.getPassType().trim(), sqlDate(bo.getValidFrom()), sqlDate(bo.getValidUntil()), operatorId, now, now);
        insertLedger(id, null, null, null, "grant", bo.getInitialUnits(), bo.getInitialUnits(), bo.getReason().trim(), operatorId, now);
        return id;
    }

    @Transactional
    public int adjust(String accountId, QlPassAdjustmentBo bo, Long operatorId) {
        if (bo.getQuantityDelta() == null || bo.getQuantityDelta() == 0) throw new CustomException("调整次数不能为0", 400);
        Map<String,Object> account = lock(accountId);
        int current = number(account.get("balance"));
        long calculated = (long) current + bo.getQuantityDelta();
        if (calculated < 0 || calculated > Integer.MAX_VALUE) throw new CustomException("调整后的卡次余额无效", 400);
        int after = (int) calculated;
        insertLedger(accountId, null, null, null, "adjust", bo.getQuantityDelta(), after, bo.getReason().trim(), operatorId, new java.util.Date());
        return after;
    }

    /** Consumes one account exactly once for a registration or registration batch. Caller owns the transaction. */
    public int consume(String accountId, String expectedCustomerId, String sessionId, String registrationId,
                       String batchId, int units, String reason, Long operatorId, java.util.Date now) {
        if (accountId == null || accountId.trim().isEmpty()) throw new CustomException("请选择要使用的卡次账户", 400);
        if (units < 1) throw new CustomException("使用卡次至少为1次", 400);
        Map<String,Object> account = lock(accountId);
        if (!expectedCustomerId.equals(String.valueOf(account.get("customerId")))) throw new CustomException("卡次账户不属于本次报名发起人或单人报名参与者", 400);
        if (!"active".equals(String.valueOf(account.get("status"))) || number(account.get("usable")) != 1) throw new CustomException("卡次账户当前不可用", 400);
        int current = number(account.get("balance"));
        if (current < units) throw new CustomException("卡次余额不足，当前剩余" + current + "次", 400);
        int after = current - units;
        insertLedger(accountId, sessionId, registrationId, batchId, "consume", -units, after,
                reason == null || reason.trim().isEmpty() ? "报名结算使用" : reason.trim(), operatorId, now);
        return after;
    }

    private Map<String,Object> lock(String accountId) {
        List<Map<String,Object>> rows = db.queryForList("SELECT a.id,a.customer_id AS customerId,a.status," +
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
                              String type, int delta, int after, String reason, Long operatorId, java.util.Date now) {
        db.update("INSERT INTO ql_pass_ledger(id,pass_account_id,session_id,registration_id,registration_batch_id,entry_type,quantity_delta,balance_after,reason,occurred_at,operator_id,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, sessionId, registrationId, batchId, type, delta, after, reason, now, operatorId, now);
    }

    private int number(Object value) { return value == null ? 0 : ((Number)value).intValue(); }
    private Date sqlDate(java.util.Date value) { return value == null ? null : new Date(value.getTime()); }
}
