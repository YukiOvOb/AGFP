-- Shared fail-closed guard for explicit local browser fixtures and read-only assertions.
DO $$
BEGIN
  IF current_database() <> 'serms_local_demo'
      OR current_setting('server_version_num')::int / 10000 <> 18 THEN
    RAISE EXCEPTION 'UC02 browser fixtures require the isolated PostgreSQL 18 demo database';
  END IF;
  IF (SELECT count(*) FROM serms.app_user) <> 6
      OR EXISTS (SELECT 1 FROM serms.app_user WHERE user_id NOT IN (
        '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000006'))
      OR NOT EXISTS (SELECT 1 FROM serms.app_user
        WHERE user_id='10000000-0000-0000-0000-000000000002'
          AND email='borrower2@example.test' AND account_status='ACTIVE') THEN
    RAISE EXCEPTION 'Refusing fixtures outside the explicitly provisioned local demo accounts';
  END IF;
END $$;
