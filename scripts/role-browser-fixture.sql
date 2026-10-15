-- Read existing completed UC02 browser history; never create or reset transactions.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
\ir lib/uc02-browser-guard.sql
SELECT json_build_object(
  'reservationId', r.reservation_id, 'reservationVersion', r.version,
  'equipmentId', e.equipment_id, 'equipmentName', e.name, 'borrowerId', r.requester_id,
  'loanId', l.loan_id, 'loanVersion', l.version, 'caseId', m.maintenance_case_id,
  'notificationId', (SELECT n.id FROM serms.notification_request n
    WHERE n.recipient=r.requester_id::text AND n.status='DELIVERED' ORDER BY n.created_at DESC LIMIT 1)
)
FROM serms.reservation r JOIN serms.equipment e USING(equipment_id)
JOIN serms.loan l USING(reservation_id)
JOIN serms.maintenance_case m ON m.loan_id=l.loan_id
WHERE r.requester_id='10000000-0000-0000-0000-000000000001'
  AND e.asset_tag LIKE 'DEMO-UC02-%' AND e.status='AVAILABLE'
  AND l.status='RETURNED' AND m.status='RESOLVED'
  AND m.assigned_to='10000000-0000-0000-0000-000000000005'
ORDER BY l.returned_at DESC LIMIT 1;
COMMIT;
