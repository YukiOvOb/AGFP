-- Explicit test fixtures only, never a Flyway migration or production startup hook.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('serms.browser_run_id', :'run_id', true);
DO $$
DECLARE
  run_id text := current_setting('serms.browser_run_id');
  device text;
BEGIN
  IF current_database() <> 'serms_local_demo'
      OR current_setting('server_version_num')::int / 10000 <> 18 THEN
    RAISE EXCEPTION 'UC02 browser fixtures require the isolated PostgreSQL 18 demo database';
  END IF;
  IF run_id !~ '^[a-f0-9]{10}$' THEN
    RAISE EXCEPTION 'Invalid UC02 browser run identifier';
  END IF;
  IF (SELECT count(*) FROM serms.app_user) <> 6
      OR EXISTS (SELECT 1 FROM serms.app_user WHERE user_id NOT IN (
        '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000006')) THEN
    RAISE EXCEPTION 'Refusing fixtures outside the explicitly provisioned local demo accounts';
  END IF;
  FOREACH device IN ARRAY ARRAY['desktop', 'mobile'] LOOP
    INSERT INTO serms.equipment(equipment_id, asset_tag, name, category, location, status, requires_approval)
    VALUES (gen_random_uuid(), 'DEMO-UC02-' || run_id || '-' || device,
      'UC02 browser camera ' || run_id || ' ' || device, 'Photography', 'Media Lab', 'AVAILABLE', true);
  END LOOP;
END $$;
COMMIT;
