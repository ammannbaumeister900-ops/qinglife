-- Read-only preflight for a V1.3.10 schema. Run on a restored rehearsal copy first.
-- Counts only: do not print customer identifiers or upload query results containing personal data.
SELECT 'duplicate_exact_openid_groups' AS issue,COUNT(*) AS affected FROM
 (SELECT BINARY open_id FROM app_user_info WHERE open_id IS NOT NULL GROUP BY BINARY open_id HAVING COUNT(*)>1) d;
SELECT 'openid_trailing_space' AS issue,COUNT(*) AS affected FROM app_user_info WHERE OCTET_LENGTH(open_id)<>OCTET_LENGTH(RTRIM(open_id));
SELECT 'openid_over_50_characters' AS issue,COUNT(*) AS affected FROM app_user_info WHERE CHAR_LENGTH(open_id)>50;
SELECT 'duplicate_legacy_binding_groups' AS issue,COUNT(*) AS affected FROM
 (SELECT legacy_app_user_id FROM ql_customer_identifier WHERE legacy_app_user_id IS NOT NULL GROUP BY legacy_app_user_id HAVING COUNT(*)>1) d;
SELECT 'orphan_legacy_binding' AS issue,COUNT(*) AS affected FROM ql_customer_identifier i LEFT JOIN app_user_info u ON u.id=i.legacy_app_user_id WHERE i.legacy_app_user_id IS NOT NULL AND u.id IS NULL;
SELECT 'registration_batch_session_mismatch' AS issue,COUNT(*) AS affected FROM ql_registration r JOIN ql_registration_batch b ON b.id=r.batch_id WHERE r.session_id<>b.session_id;
SELECT 'batch_payment_mismatch' AS issue,COUNT(*) AS affected FROM ql_registration r JOIN ql_registration_batch b ON b.id=r.batch_id WHERE r.payment_status<>b.payment_status;
SELECT 'negative_pass_balance' AS issue,COUNT(*) AS affected FROM
 (SELECT pass_account_id FROM ql_pass_ledger GROUP BY pass_account_id HAVING SUM(quantity_delta)<0) d;
SELECT 'over_capacity_sessions' AS issue,COUNT(*) AS affected FROM
 (SELECT s.id FROM ql_session s JOIN ql_registration r ON r.session_id=s.id AND r.registration_status IN ('pending','confirmed') GROUP BY s.id,s.capacity HAVING COUNT(*)>s.capacity) d;
SELECT 'missing_price_snapshot' AS issue,COUNT(*) AS affected FROM ql_registration WHERE unit_price IS NULL;
