CREATE TABLE notification_request (
 id VARCHAR(36) PRIMARY KEY,
 dedup_key VARCHAR(64) NOT NULL,
 recipient VARCHAR(100) NOT NULL,
 type VARCHAR(30) NOT NULL,
 loan_id BIGINT,
 expected_due_at TIMESTAMP(6) NULL,
 content VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL,
 next_attempt_at TIMESTAMP(6) NULL,
 delivered_at TIMESTAMP(6) NULL,
 read_at TIMESTAMP(6) NULL,
 reason VARCHAR(100),
 CONSTRAINT uk_notification_dedup UNIQUE (dedup_key)
);
CREATE INDEX ix_notification_pending ON notification_request(status,next_attempt_at);
CREATE INDEX ix_notification_inbox ON notification_request(recipient,status,delivered_at);
