-- Authentication and denied probes must not change business data or notification read state.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
\ir lib/uc02-browser-guard.sql
SELECT json_build_object(
  'users', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY user_id), '')) FROM serms.app_user t),
  'equipment', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY equipment_id), '')) FROM serms.equipment t),
  'reservations', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY reservation_id), '')) FROM serms.reservation t),
  'decisions', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY approval_id), '')) FROM serms.approval_decision t),
  'loans', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY loan_id), '')) FROM serms.loan t),
  'maintenance', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY maintenance_case_id), '')) FROM serms.maintenance_case t),
  'audit', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY audit_id), '')) FROM serms.audit_log t),
  'notificationReadState', (SELECT md5(coalesce(string_agg(id || ':' || recipient || ':' || coalesce(read_at::text, ''), '' ORDER BY id), '')) FROM serms.notification_request),
  'roles', (SELECT md5(coalesce(string_agg(user_id::text || ':' || role_id::text, '' ORDER BY user_id, role_id), '')) FROM serms.user_role)
);
COMMIT;
