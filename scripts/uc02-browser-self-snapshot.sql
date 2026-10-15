-- Read-only assertions, restricted to this run's second-borrower/self-review fixture.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
\ir lib/uc02-browser-guard.sql
SELECT json_build_object(
  'status', r.status, 'version', r.version,
  'decisionCount', (SELECT count(*) FROM serms.approval_decision d WHERE d.reservation_id=r.reservation_id),
  'successAuditCount', (SELECT count(*) FROM serms.audit_log a
    WHERE a.entity_id=r.reservation_id AND a.action='RESERVATION_DECIDE' AND a.outcome='SUCCESS'),
  'notificationCount', (SELECT count(*) FROM serms.notification_request n WHERE n.recipient=r.requester_id::text)
)
FROM serms.reservation r JOIN serms.equipment e USING(equipment_id)
WHERE r.reservation_id=:'reservation_id'::uuid
  AND r.requester_id='10000000-0000-0000-0000-000000000002'
  AND e.asset_tag='DEMO-UC02-' || :'run_id' || '-self-' || :'device';
COMMIT;
