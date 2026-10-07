-- Test-only immutable V002 decision, to prove V004 converts history rather than deleting it.
INSERT INTO serms.app_user(user_id,email,display_name,password_hash)
VALUES ('00000000-0000-0000-0000-000000004001','v4borrower@example.invalid','V4 borrower','TEST_ONLY'),
       ('00000000-0000-0000-0000-000000004002','v4approver@example.invalid','V4 approver','TEST_ONLY');
INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location,requires_approval)
VALUES ('00000000-0000-0000-0000-000000004003','V4-UPGRADE','Migration camera','Camera','Test',true);
INSERT INTO serms.reservation(reservation_id,requester_id,equipment_id,start_at,end_at,status)
VALUES ('00000000-0000-0000-0000-000000004004','00000000-0000-0000-0000-000000004001',
 '00000000-0000-0000-0000-000000004003',now()+interval '1 day',now()+interval '2 days','PENDING_APPROVAL');
INSERT INTO serms.approval_decision(approval_id,reservation_id,approver_id,decision,comment)
VALUES ('00000000-0000-0000-0000-000000004005','00000000-0000-0000-0000-000000004004',
 '00000000-0000-0000-0000-000000004002','REJECT','Keep this reason');
UPDATE serms.reservation SET status='REJECTED' WHERE reservation_id='00000000-0000-0000-0000-000000004004';
