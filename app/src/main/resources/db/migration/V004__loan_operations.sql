-- Additive UC03 indexes. V003 is owned by the notification integration and optional
-- for the standalone data module. No existing migration or business history is rewritten.
BEGIN;
-- Keep V002 checksums/history stable and migrate the agreed UC02 codes without deleting decisions.
ALTER TABLE serms.approval_decision DROP CONSTRAINT approval_decision_decision_check;
ALTER TABLE serms.approval_decision DROP CONSTRAINT approval_decision_check;
ALTER TABLE serms.approval_decision DISABLE TRIGGER approval_immutable;
UPDATE serms.approval_decision SET decision=CASE decision WHEN 'APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END;
ALTER TABLE serms.approval_decision ENABLE TRIGGER approval_immutable;
ALTER TABLE serms.approval_decision ADD CONSTRAINT approval_decision_decision_check CHECK(decision IN ('APPROVED','REJECTED'));
ALTER TABLE serms.approval_decision ADD CONSTRAINT approval_decision_reason_check CHECK(decision<>'REJECTED' OR (comment IS NOT NULL AND length(btrim(comment))>0));
ALTER TABLE serms.equipment ALTER COLUMN version TYPE bigint;
ALTER TABLE serms.reservation ALTER COLUMN version TYPE bigint;
ALTER TABLE serms.loan ALTER COLUMN version TYPE bigint;
ALTER TABLE serms.maintenance_case ALTER COLUMN version TYPE bigint;
CREATE INDEX loan_checkout_history ON serms.loan(checked_out_at DESC, loan_id);
CREATE INDEX reservation_equipment_lookup ON serms.reservation(equipment_id, reservation_id);
INSERT INTO serms.schema_version(version,description) VALUES (4,'UC03 operations and shared approval/version alignment');
COMMIT;
