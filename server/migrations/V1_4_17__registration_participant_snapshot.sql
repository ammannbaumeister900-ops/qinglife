-- Store each submission's declared participant details without mutating identities or old history.
-- NULL snapshots on historical/admin registrations keep the existing customer-profile fallback.
ALTER TABLE ql_registration
  ADD COLUMN participant_name_snapshot varchar(100) DEFAULT NULL,
  ADD COLUMN participant_phone_snapshot varchar(32) DEFAULT NULL;
