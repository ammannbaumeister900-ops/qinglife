-- Keep cancellation history; at most one active registration per participant/session.
-- No order, payment, cancellation or reflection rows are rewritten.
ALTER TABLE ql_registration
  ADD COLUMN active_registration_slot TINYINT GENERATED ALWAYS AS
    (CASE WHEN registration_status='cancelled' THEN NULL ELSE 1 END) STORED,
  DROP INDEX uq_ql_registration_customer_session,
  ADD UNIQUE KEY uq_ql_registration_active (customer_id,session_id,active_registration_slot);
