-- Explicit test fixtures only, never a Flyway migration or production startup hook.
\set ON_ERROR_STOP on
BEGIN;
\ir lib/uc02-browser-guard.sql
SELECT set_config('serms.browser_run_id', :'run_id', true);
DO $$
DECLARE
  run_id text := current_setting('serms.browser_run_id');
  device text;
BEGIN
  IF run_id !~ '^[a-f0-9]{10}$' THEN
    RAISE EXCEPTION 'Invalid UC02 browser run identifier';
  END IF;
  FOREACH device IN ARRAY ARRAY['desktop', 'mobile'] LOOP
    INSERT INTO serms.equipment(equipment_id, asset_tag, name, category, location, status, requires_approval)
    VALUES (gen_random_uuid(), 'DEMO-UC02-' || run_id || '-' || device,
      'UC02 browser camera ' || run_id || ' ' || device, 'Photography', 'Media Lab', 'AVAILABLE', true);
    INSERT INTO serms.equipment(equipment_id, asset_tag, name, category, location, status, requires_approval)
    VALUES (gen_random_uuid(), 'DEMO-UC02-' || run_id || '-self-' || device,
      'UC02 self-review camera ' || run_id || ' ' || device, 'Photography', 'Media Lab', 'AVAILABLE', true);
  END LOOP;
END $$;
-- Return whether this run added the role; never remove a pre-existing role during cleanup.
WITH added AS (
  INSERT INTO serms.user_role(user_id, role_id)
  SELECT '10000000-0000-0000-0000-000000000002'::uuid, role_id FROM serms.role WHERE code='APPROVER'
  ON CONFLICT DO NOTHING RETURNING 1
) SELECT count(*) FROM added;
COMMIT;
