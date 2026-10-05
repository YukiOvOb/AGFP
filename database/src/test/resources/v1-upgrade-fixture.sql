-- Upgrade test data only. Never included in Docker initialization or production migrations.
BEGIN;
INSERT INTO serms.app_user(id,email,display_name,password_hash,role)
VALUES ('10000000-0000-0000-0000-000000000001','v1-upgrade@example.invalid','Legacy maintainer','legacy hash retained','TECHNICIAN');
INSERT INTO serms.equipment(id,asset_tag,name,category,location,requires_approval)
VALUES ('10000000-0000-0000-0000-000000000002','V1-UPGRADE','Legacy camera','Camera','Lab',true);
INSERT INTO serms.reservation(id,user_id,equipment_id,starts_at,ends_at,status)
VALUES ('10000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000002',now()+interval '1 day',now()+interval '2 days','PENDING');
UPDATE serms.equipment SET status='MAINTENANCE' WHERE asset_tag='V1-UPGRADE';
UPDATE serms.app_user SET active=false WHERE id='10000000-0000-0000-0000-000000000001';
COMMIT;
