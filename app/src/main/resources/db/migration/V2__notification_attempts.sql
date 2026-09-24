CREATE TABLE notification_attempt (
 id VARCHAR(36) PRIMARY KEY,
 notification_id VARCHAR(36) NOT NULL,
 attempted_at TIMESTAMP(6) NOT NULL,
 outcome VARCHAR(20) NOT NULL,
 reason VARCHAR(100),
 CONSTRAINT fk_attempt_notification FOREIGN KEY (notification_id) REFERENCES notification_request(id)
);
CREATE INDEX ix_attempt_notification ON notification_attempt(notification_id,attempted_at);
