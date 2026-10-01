-- Principal names must remain case-sensitive, matching Spring Security ownership checks.
ALTER TABLE notification_attempt DROP FOREIGN KEY fk_attempt_notification;
ALTER TABLE notification_request CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE notification_attempt CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE notification_attempt ADD CONSTRAINT fk_attempt_notification
 FOREIGN KEY (notification_id) REFERENCES notification_request(id);
