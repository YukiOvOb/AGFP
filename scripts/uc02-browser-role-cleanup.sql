-- Only invoked when this run itself inserted the temporary role.
\set ON_ERROR_STOP on
BEGIN;
\ir lib/uc02-browser-guard.sql
DELETE FROM serms.user_role ur USING serms.role r
WHERE ur.role_id=r.role_id AND r.code='APPROVER'
  AND ur.user_id='10000000-0000-0000-0000-000000000002';
SELECT count(*) FROM serms.user_role ur JOIN serms.role r USING(role_id)
WHERE ur.user_id='10000000-0000-0000-0000-000000000002' AND r.code='APPROVER';
COMMIT;
