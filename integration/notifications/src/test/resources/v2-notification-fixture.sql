BEGIN;
INSERT INTO serms.app_user(user_id,email,display_name,password_hash)
VALUES('20000000-0000-0000-0000-000000000001','upgrade@example.invalid','Upgrade','test fixture only');
INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location)
VALUES('20000000-0000-0000-0000-000000000002','NOTIFICATION-UPGRADE','Camera','Camera','Lab');
INSERT INTO serms.reservation(reservation_id,requester_id,equipment_id,start_at,end_at,status)
VALUES('20000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000001',
'20000000-0000-0000-0000-000000000002',now()-interval '1 minute',now()+interval '1 hour','CONFIRMED');
INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at)
SELECT '20000000-0000-0000-0000-000000000004',reservation_id,requester_id,now(),end_at
FROM serms.reservation WHERE reservation_id='20000000-0000-0000-0000-000000000003';
INSERT INTO serms.notification(notification_id,recipient_id,loan_id,type,content,dedup_key,
delivery_status,attempt_count,created_at,delivered_at,read_at)
VALUES('20000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000001',
'20000000-0000-0000-0000-000000000004','DUE_SOON','Original delivered content','legacy-sent','SENT',
2,now()-interval '2 minutes',now()-interval '1 minute',now());
INSERT INTO serms.notification(notification_id,recipient_id,reservation_id,type,content,dedup_key,delivery_status)
VALUES('20000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000001',
'20000000-0000-0000-0000-000000000003','RESERVATION','Original pending content','legacy-pending','PENDING'),
('20000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000001',
'20000000-0000-0000-0000-000000000003','RESERVATION','Original failed content','legacy-failed','FAILED');
COMMIT;