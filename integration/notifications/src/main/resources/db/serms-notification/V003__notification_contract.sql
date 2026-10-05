-- Optional notification integration after shared V001/V002. Never run standalone upstream V1/V2/V3 here.
BEGIN;
ALTER TABLE serms.app_user ADD COLUMN notification_principal varchar(100) COLLATE "C";
UPDATE serms.app_user SET notification_principal=user_id::text;
ALTER TABLE serms.app_user ALTER COLUMN notification_principal SET NOT NULL;
ALTER TABLE serms.app_user ADD CONSTRAINT user_notification_principal_unique UNIQUE(notification_principal),
    ADD CONSTRAINT user_notification_principal_valid CHECK(length(btrim(notification_principal))>0);
CREATE FUNCTION serms.default_notification_principal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.notification_principal IS NULL THEN NEW.notification_principal:=NEW.user_id::text; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER user_notification_principal BEFORE INSERT ON serms.app_user
    FOR EACH ROW EXECUTE FUNCTION serms.default_notification_principal();
ALTER TABLE serms.loan ADD COLUMN reminder_id bigint GENERATED ALWAYS AS IDENTITY;
ALTER TABLE serms.loan ADD CONSTRAINT loan_reminder_id_unique UNIQUE(reminder_id);
-- This numeric ID is an interface bridge, not a replacement for the UUID domain identity.
CREATE FUNCTION serms.keep_reminder_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.reminder_id<>OLD.reminder_id THEN
        RAISE EXCEPTION 'Reminder identity is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER loan_reminder_identity BEFORE UPDATE ON serms.loan
    FOR EACH ROW EXECUTE FUNCTION serms.keep_reminder_identity();

-- Preserve the original ERD history; no dual-write model is introduced.
ALTER TABLE serms.notification RENAME TO notification_legacy_v2;
CREATE TABLE serms.notification_request (
    id varchar(36) PRIMARY KEY,
    dedup_key varchar(64) COLLATE "C" NOT NULL UNIQUE,
    recipient varchar(100) COLLATE "C" NOT NULL REFERENCES serms.app_user(notification_principal) ON DELETE RESTRICT,
    type varchar(30) NOT NULL CHECK(type IN ('BUSINESS_EVENT','DUE_SOON','OVERDUE')),
    loan_id bigint REFERENCES serms.loan(reminder_id) ON DELETE RESTRICT,
    expected_due_at timestamptz,
    content varchar(2000) NOT NULL CHECK(length(btrim(content))>0),
    status varchar(20) NOT NULL CHECK(status IN ('PENDING','RETRY','DELIVERED','CANCELLED','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
    created_at timestamptz NOT NULL,
    next_attempt_at timestamptz,
    delivered_at timestamptz,
    read_at timestamptz,
    reason varchar(100),
    legacy_notification_id uuid UNIQUE REFERENCES serms.notification_legacy_v2(notification_id) ON DELETE RESTRICT,
    CHECK(type='BUSINESS_EVENT' OR (loan_id IS NOT NULL AND expected_due_at IS NOT NULL)),
    CHECK((status IN ('PENDING','RETRY') AND next_attempt_at IS NOT NULL)
        OR (status NOT IN ('PENDING','RETRY') AND next_attempt_at IS NULL)),
    CHECK((status='DELIVERED' AND delivered_at IS NOT NULL)
        OR (status<>'DELIVERED' AND delivered_at IS NULL AND read_at IS NULL)),
    CHECK(delivered_at IS NULL OR delivered_at>=created_at),
    CHECK(read_at IS NULL OR read_at>=delivered_at)
);
CREATE INDEX ix_notification_pending ON serms.notification_request(status,next_attempt_at);
CREATE INDEX ix_notification_inbox ON serms.notification_request(recipient,status,delivered_at);
CREATE INDEX ix_notification_loan ON serms.notification_request(loan_id) WHERE loan_id IS NOT NULL;
CREATE TABLE serms.notification_attempt (
    id varchar(36) PRIMARY KEY,
    notification_id varchar(36) NOT NULL REFERENCES serms.notification_request(id) ON DELETE RESTRICT,
    attempted_at timestamptz NOT NULL,
    outcome varchar(20) NOT NULL CHECK(outcome IN ('RETRY','FAILED','CANCELLED','DELIVERED')),
    reason varchar(100)
);
CREATE INDEX ix_attempt_notification ON serms.notification_attempt(notification_id,attempted_at);

-- Reconstruct the exact upstream length-prefixed SHA-256 key, with period "once".
-- Newly mapped principals and source identifiers are ASCII UUIDs. Instant.toString()
-- renders fractional seconds in groups of three (0, 3 or 6 digits for PostgreSQL precision).
WITH mapped AS (
    SELECT n.*,u.notification_principal,l.reminder_id,
        CASE WHEN n.type IN ('DUE_SOON','OVERDUE') AND n.loan_id IS NOT NULL THEN n.type ELSE 'BUSINESS_EVENT' END AS mapped_type,
        CASE WHEN n.type IN ('DUE_SOON','OVERDUE') AND n.loan_id IS NOT NULL THEN 'loan:'||n.loan_id::text
            ELSE 'legacy-notification:'||n.notification_id::text END AS source_id,
        CASE WHEN n.type IN ('DUE_SOON','OVERDUE') AND n.loan_id IS NOT NULL THEN l.due_at ELSE NULL END AS expected_due
    FROM serms.notification_legacy_v2 n JOIN serms.app_user u ON u.user_id=n.recipient_id
    LEFT JOIN serms.loan l ON l.loan_id=n.loan_id
), canonical AS (
    SELECT m.*, CASE WHEN expected_due IS NULL THEN 'null' ELSE
        to_char(expected_due AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS') ||
        CASE WHEN to_char(expected_due AT TIME ZONE 'UTC','US')='000000' THEN ''
             WHEN right(to_char(expected_due AT TIME ZONE 'UTC','US'),3)='000'
                THEN '.'||left(to_char(expected_due AT TIME ZONE 'UTC','US'),3)
             ELSE '.'||to_char(expected_due AT TIME ZONE 'UTC','US') END || 'Z' END AS due_text
    FROM mapped m
)
INSERT INTO serms.notification_request
    (id,dedup_key,recipient,type,loan_id,expected_due_at,content,status,attempts,created_at,
     next_attempt_at,delivered_at,read_at,legacy_notification_id)
SELECT n.notification_id::text,
    encode(sha256(convert_to((
        SELECT string_agg(length(part)::text||':'||part,'' ORDER BY position)
        FROM unnest(ARRAY[n.notification_principal,n.mapped_type,n.source_id,'once',
            coalesce(n.reminder_id::text,'null'),n.due_text]) WITH ORDINALITY AS identity_part(part,position)
    ),'UTF8')),'hex'),
    n.notification_principal,n.mapped_type,n.reminder_id,n.expected_due,n.content,
    CASE n.delivery_status WHEN 'SENT' THEN 'DELIVERED' ELSE n.delivery_status END,
    n.attempt_count,n.created_at,CASE WHEN n.delivery_status='PENDING' THEN n.created_at ELSE NULL END,
    n.delivered_at,n.read_at,n.notification_id
FROM canonical n;

CREATE TRIGGER legacy_notification_readonly BEFORE INSERT OR UPDATE OR DELETE ON serms.notification_legacy_v2
    FOR EACH ROW EXECUTE FUNCTION serms.reject_history_mutation();
CREATE TRIGGER legacy_notification_no_truncate BEFORE TRUNCATE ON serms.notification_legacy_v2
    FOR EACH STATEMENT EXECUTE FUNCTION serms.reject_history_mutation();
INSERT INTO serms.schema_version(version,description) VALUES (3,'Wang Yuanmeng notification contract adapter');
COMMIT;