-- Explicit, isolated local demo data. Never run on a shared or production database.
\set ON_ERROR_STOP on
\getenv demo_password SERMS_LOCAL_DEMO_PASSWORD
BEGIN;
DO $$
BEGIN
  IF current_database() <> 'serms_local_demo' THEN
    RAISE EXCEPTION 'Demo data requires the isolated serms_local_demo database';
  END IF;
  IF EXISTS (SELECT 1 FROM serms.app_user WHERE user_id NOT IN (
      '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
      '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004',
      '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000006'))
    OR EXISTS (SELECT 1 FROM serms.equipment WHERE asset_tag NOT LIKE 'DEMO-%') THEN
    RAISE EXCEPTION 'Refusing to seed a database containing unrelated users or equipment';
  END IF;
END $$;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
WITH accounts(user_id, email, display_name) AS (VALUES
  ('10000000-0000-0000-0000-000000000001'::uuid, 'borrower@example.test', 'Alex Borrower'),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'borrower2@example.test', 'Taylor Borrower'),
  ('10000000-0000-0000-0000-000000000003'::uuid, 'approver@example.test', 'Jamie Approver'),
  ('10000000-0000-0000-0000-000000000004'::uuid, 'custodian@example.test', 'Morgan Custodian'),
  ('10000000-0000-0000-0000-000000000005'::uuid, 'maintainer@example.test', 'Sam Maintainer'),
  ('10000000-0000-0000-0000-000000000006'::uuid, 'admin@example.test', 'Robin Administrator'))
INSERT INTO serms.app_user(user_id, email, display_name, password_hash, account_status, notification_principal)
SELECT user_id, email, display_name, crypt(:'demo_password', gen_salt('bf', 12)), 'ACTIVE', user_id::text
FROM accounts ON CONFLICT (user_id) DO NOTHING;
WITH assignments(user_id, code) AS (VALUES
  ('10000000-0000-0000-0000-000000000001'::uuid, 'BORROWER'),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'BORROWER'),
  ('10000000-0000-0000-0000-000000000003'::uuid, 'APPROVER'),
  ('10000000-0000-0000-0000-000000000004'::uuid, 'CUSTODIAN'),
  ('10000000-0000-0000-0000-000000000005'::uuid, 'MAINTAINER'),
  ('10000000-0000-0000-0000-000000000006'::uuid, 'ADMIN'))
INSERT INTO serms.user_role(user_id, role_id)
SELECT assignments.user_id, role.role_id FROM assignments JOIN serms.role role USING (code)
ON CONFLICT DO NOTHING;
INSERT INTO serms.equipment(equipment_id, asset_tag, name, category, location, status, requires_approval) VALUES
  ('20000000-0000-0000-0000-000000000001', 'DEMO-CAM-01', 'Sony mirrorless camera', 'Photography', 'Media Lab', 'AVAILABLE', true),
  ('20000000-0000-0000-0000-000000000002', 'DEMO-TRI-01', 'Professional tripod', 'Photography', 'Media Lab', 'AVAILABLE', false),
  ('20000000-0000-0000-0000-000000000003', 'DEMO-MIC-01', 'Wireless microphone kit', 'Audio', 'Media Lab', 'AVAILABLE', false),
  ('20000000-0000-0000-0000-000000000004', 'DEMO-PRO-01', 'Portable projector', 'Presentation', 'Equipment Store', 'AVAILABLE', true),
  ('20000000-0000-0000-0000-000000000005', 'DEMO-LAP-01', 'Engineering laptop', 'Computing', 'Engineering Lab', 'AVAILABLE', true),
  ('20000000-0000-0000-0000-000000000006', 'DEMO-MEA-01', 'Digital multimeter', 'Measurement', 'Engineering Lab', 'AVAILABLE', false),
  ('20000000-0000-0000-0000-000000000007', 'DEMO-CAM-02', 'Retired camera', 'Photography', 'Equipment Store', 'RETIRED', true),
  ('20000000-0000-0000-0000-000000000008', 'DEMO-LIG-01', 'Studio lighting kit', 'Photography', 'Media Lab', 'AVAILABLE', false)
ON CONFLICT (equipment_id) DO NOTHING;
COMMIT;
