-- Align V001 with SERMS ER Diagram and integration/state specifications.
-- Run once after V001, with psql -v ON_ERROR_STOP=1. Existing rows are preserved.
BEGIN;
DROP TRIGGER reservation_rules ON serms.reservation;
ALTER TABLE serms.reservation DROP CONSTRAINT reservation_no_overlap;
ALTER TABLE serms.reservation DROP CONSTRAINT reservation_status_check;
ALTER TABLE serms.equipment DROP CONSTRAINT equipment_status_check;

ALTER TABLE serms.app_user RENAME COLUMN id TO user_id;
ALTER TABLE serms.equipment RENAME COLUMN id TO equipment_id;
ALTER TABLE serms.reservation RENAME COLUMN id TO reservation_id;
ALTER TABLE serms.reservation RENAME COLUMN user_id TO requester_id;
ALTER TABLE serms.reservation RENAME COLUMN starts_at TO start_at;
ALTER TABLE serms.reservation RENAME COLUMN ends_at TO end_at;
ALTER TABLE serms.app_user ADD COLUMN account_status text NOT NULL DEFAULT 'ACTIVE'
    CHECK (account_status IN ('ACTIVE', 'DISABLED'));
UPDATE serms.app_user SET account_status = CASE WHEN active THEN 'ACTIVE' ELSE 'DISABLED' END;

CREATE TABLE serms.role (
    role_id uuid PRIMARY KEY,
    code text NOT NULL UNIQUE CHECK (code IN ('BORROWER','APPROVER','CUSTODIAN','MAINTAINER','ADMIN')),
    name text NOT NULL CHECK (length(btrim(name)) > 0)
);
INSERT INTO serms.role VALUES
    ('00000000-0000-0000-0000-000000000001','BORROWER','Borrower'),
    ('00000000-0000-0000-0000-000000000002','APPROVER','Approver'),
    ('00000000-0000-0000-0000-000000000003','CUSTODIAN','Custodian'),
    ('00000000-0000-0000-0000-000000000004','MAINTAINER','Maintainer'),
    ('00000000-0000-0000-0000-000000000005','ADMIN','Administrator');
CREATE TABLE serms.user_role (
    user_id uuid NOT NULL REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    role_id uuid NOT NULL REFERENCES serms.role(role_id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);
INSERT INTO serms.user_role
SELECT u.user_id, r.role_id FROM serms.app_user u JOIN serms.role r
    ON r.code = CASE WHEN u.role = 'TECHNICIAN' THEN 'MAINTAINER' ELSE u.role END;
ALTER TABLE serms.app_user DROP COLUMN role, DROP COLUMN active;

UPDATE serms.equipment SET status='UNDER_MAINTENANCE' WHERE status='MAINTENANCE';
UPDATE serms.reservation SET status='PENDING_APPROVAL' WHERE status='PENDING';
ALTER TABLE serms.equipment ADD CONSTRAINT equipment_status_check
    CHECK (status IN ('AVAILABLE','ON_LOAN','UNDER_MAINTENANCE','RETIRED'));
ALTER TABLE serms.reservation ADD CONSTRAINT reservation_status_check
    CHECK (status IN ('PENDING_APPROVAL','CONFIRMED','REJECTED','CANCELLED','FULFILLED'));
ALTER TABLE serms.equipment ADD COLUMN version integer NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE serms.reservation ADD COLUMN purpose text,
    ADD COLUMN version integer NOT NULL DEFAULT 0 CHECK (version >= 0);
-- If legacy FULFILLED rows overlap, abort the whole migration for manual reconciliation.
ALTER TABLE serms.reservation ADD CONSTRAINT reservation_no_overlap EXCLUDE USING gist (
    equipment_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&
) WHERE (status IN ('PENDING_APPROVAL','CONFIRMED','FULFILLED'));

CREATE TABLE serms.approval_decision (
    approval_id uuid PRIMARY KEY,
    reservation_id uuid NOT NULL UNIQUE REFERENCES serms.reservation(reservation_id) ON DELETE RESTRICT,
    approver_id uuid NOT NULL REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    decision text NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
    comment text,
    decided_at timestamptz NOT NULL DEFAULT now() CHECK (isfinite(decided_at)),
    CHECK (decision <> 'REJECT' OR (comment IS NOT NULL AND length(btrim(comment)) > 0))
);
CREATE TABLE serms.loan (
    loan_id uuid PRIMARY KEY,
    reservation_id uuid NOT NULL UNIQUE REFERENCES serms.reservation(reservation_id) ON DELETE RESTRICT,
    checkout_by uuid NOT NULL REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    return_by uuid REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    checked_out_at timestamptz NOT NULL CHECK (isfinite(checked_out_at)),
    due_at timestamptz NOT NULL CHECK (isfinite(due_at)),
    returned_at timestamptz CHECK (isfinite(returned_at)),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RETURNED')),
    return_condition text CHECK (return_condition IN ('GOOD','DAMAGED')),
    return_note text,
    version integer NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (checked_out_at < due_at),
    CHECK (returned_at IS NULL OR returned_at >= checked_out_at),
    CHECK ((status='ACTIVE' AND return_by IS NULL AND returned_at IS NULL
            AND return_condition IS NULL AND return_note IS NULL)
        OR (status='RETURNED' AND return_by IS NOT NULL AND returned_at IS NOT NULL
            AND return_condition IS NOT NULL)),
    CHECK (return_condition IS DISTINCT FROM 'DAMAGED'
        OR (return_note IS NOT NULL AND length(btrim(return_note)) > 0))
);
CREATE TABLE serms.maintenance_case (
    maintenance_case_id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL REFERENCES serms.equipment(equipment_id) ON DELETE RESTRICT,
    reported_by uuid NOT NULL REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    assigned_to uuid REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    loan_id uuid REFERENCES serms.loan(loan_id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ASSIGNED','IN_PROGRESS','RESOLVED','UNREPAIRABLE')),
    fault_description text NOT NULL CHECK (length(btrim(fault_description)) > 0),
    resolution_note text,
    reported_at timestamptz NOT NULL DEFAULT now() CHECK (isfinite(reported_at)),
    resolved_at timestamptz CHECK (isfinite(resolved_at)),
    version integer NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (status NOT IN ('ASSIGNED','IN_PROGRESS') OR assigned_to IS NOT NULL),
    CHECK ((status IN ('OPEN','ASSIGNED','IN_PROGRESS') AND resolved_at IS NULL)
        OR (status IN ('RESOLVED','UNREPAIRABLE') AND resolved_at IS NOT NULL
            AND resolution_note IS NOT NULL AND length(btrim(resolution_note)) > 0)),
    CHECK (resolved_at IS NULL OR resolved_at >= reported_at)
);
CREATE TABLE serms.notification (
    notification_id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    reservation_id uuid REFERENCES serms.reservation(reservation_id) ON DELETE RESTRICT,
    loan_id uuid REFERENCES serms.loan(loan_id) ON DELETE RESTRICT,
    maintenance_case_id uuid REFERENCES serms.maintenance_case(maintenance_case_id) ON DELETE RESTRICT,
    type text NOT NULL CHECK (length(btrim(type)) > 0),
    content text NOT NULL CHECK (length(btrim(content)) > 0),
    delivery_status text NOT NULL DEFAULT 'PENDING' CHECK (delivery_status IN ('PENDING','SENT','FAILED')),
    dedup_key text NOT NULL UNIQUE CHECK (length(btrim(dedup_key)) > 0),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now() CHECK (isfinite(created_at)),
    delivered_at timestamptz CHECK (isfinite(delivered_at)),
    read_at timestamptz CHECK (isfinite(read_at)),
    CHECK (num_nonnulls(reservation_id, loan_id, maintenance_case_id) = 1),
    CHECK ((delivery_status='SENT' AND delivered_at IS NOT NULL)
        OR (delivery_status<>'SENT' AND delivered_at IS NULL AND read_at IS NULL)),
    CHECK (delivered_at IS NULL OR delivered_at >= created_at),
    CHECK (read_at IS NULL OR read_at >= delivered_at)
);
CREATE TABLE serms.audit_log (
    audit_id uuid PRIMARY KEY,
    actor_id uuid REFERENCES serms.app_user(user_id) ON DELETE RESTRICT,
    action text NOT NULL CHECK (length(btrim(action)) > 0),
    entity_type text NOT NULL CHECK (length(btrim(entity_type)) > 0),
    entity_id uuid NOT NULL,
    outcome text NOT NULL CHECK (length(btrim(outcome)) > 0),
    request_id text NOT NULL CHECK (length(btrim(request_id)) > 0),
    change_summary text,
    occurred_at timestamptz NOT NULL DEFAULT now() CHECK (isfinite(occurred_at))
);
CREATE INDEX user_role_role ON serms.user_role(role_id);
CREATE INDEX reservation_requester_created ON serms.reservation(requester_id, created_at);
CREATE INDEX loan_status_due ON serms.loan(status, due_at);
CREATE INDEX maintenance_equipment_status ON serms.maintenance_case(equipment_id, status);
CREATE INDEX maintenance_loan ON serms.maintenance_case(loan_id) WHERE loan_id IS NOT NULL;
CREATE INDEX notification_recipient_read_created ON serms.notification(recipient_id, read_at, created_at);
CREATE INDEX notification_delivery_created ON serms.notification(delivery_status, created_at);
CREATE INDEX audit_entity_time ON serms.audit_log(entity_type, entity_id, occurred_at);

CREATE FUNCTION serms.bump_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.version := OLD.version + 1; RETURN NEW; END;
$$;
CREATE TRIGGER equipment_version BEFORE UPDATE ON serms.equipment FOR EACH ROW EXECUTE FUNCTION serms.bump_version();
CREATE TRIGGER reservation_version BEFORE UPDATE ON serms.reservation FOR EACH ROW EXECUTE FUNCTION serms.bump_version();
CREATE TRIGGER loan_version BEFORE UPDATE ON serms.loan FOR EACH ROW EXECUTE FUNCTION serms.bump_version();
CREATE TRIGGER maintenance_version BEFORE UPDATE ON serms.maintenance_case FOR EACH ROW EXECUTE FUNCTION serms.bump_version();

CREATE FUNCTION serms.reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Append-only history cannot be modified or removed' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON serms.audit_log
    FOR EACH ROW EXECUTE FUNCTION serms.reject_history_mutation();
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON serms.audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION serms.reject_history_mutation();
CREATE TRIGGER approval_immutable BEFORE UPDATE OR DELETE ON serms.approval_decision
    FOR EACH ROW EXECUTE FUNCTION serms.reject_history_mutation();

CREATE FUNCTION serms.check_approval() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE requester uuid;
BEGIN
    SELECT requester_id INTO requester FROM serms.reservation WHERE reservation_id=NEW.reservation_id;
    IF requester = NEW.approver_id THEN
        RAISE EXCEPTION 'Self approval is forbidden' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER approval_rules BEFORE INSERT ON serms.approval_decision FOR EACH ROW EXECUTE FUNCTION serms.check_approval();

-- Same predicate for reads and writes. Caller locks equipment when writing.
CREATE FUNCTION serms.equipment_can_reserve(item_id uuid, requested_start timestamptz)
RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT EXISTS (
        SELECT 1 FROM serms.equipment e WHERE e.equipment_id=item_id
        AND e.status IN ('AVAILABLE','ON_LOAN')
        AND NOT EXISTS (SELECT 1 FROM serms.maintenance_case m WHERE m.equipment_id=e.equipment_id
            AND m.status IN ('OPEN','ASSIGNED','IN_PROGRESS'))
        AND ((e.status='AVAILABLE' AND NOT EXISTS (
            SELECT 1 FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
            WHERE r.equipment_id=e.equipment_id AND l.status='ACTIVE'))
          OR (e.status='ON_LOAN' AND EXISTS (
            SELECT 1 FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
            WHERE r.equipment_id=e.equipment_id AND l.status='ACTIVE')
            AND NOT EXISTS (
            SELECT 1 FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
            WHERE r.equipment_id=e.equipment_id AND l.status='ACTIVE'
              AND (l.due_at < statement_timestamp() OR l.due_at > requested_start))))
    );
$$;

CREATE OR REPLACE FUNCTION serms.check_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item serms.equipment; person serms.app_user;
BEGIN
    IF TG_OP='UPDATE' THEN
        IF (NEW.reservation_id,NEW.requester_id,NEW.equipment_id,NEW.start_at,NEW.end_at,NEW.created_at)
            IS DISTINCT FROM (OLD.reservation_id,OLD.requester_id,OLD.equipment_id,OLD.start_at,OLD.end_at,OLD.created_at) THEN
            RAISE EXCEPTION 'Reservation details are immutable; cancel and rebook' USING ERRCODE='23514';
        END IF;
        IF NEW.status=OLD.status THEN RETURN NEW; END IF;
        IF NOT ((OLD.status='PENDING_APPROVAL' AND NEW.status IN ('CONFIRMED','REJECTED','CANCELLED'))
            OR (OLD.status='CONFIRMED' AND NEW.status IN ('CANCELLED','FULFILLED'))) THEN
            RAISE EXCEPTION 'Illegal reservation transition' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='INSERT' OR NEW.status='CONFIRMED' THEN
        SELECT * INTO item FROM serms.equipment WHERE equipment_id=NEW.equipment_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Unknown equipment' USING ERRCODE='23503'; END IF;
        SELECT * INTO person FROM serms.app_user WHERE user_id=NEW.requester_id FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Unknown user' USING ERRCODE='23503'; END IF;
        IF person.account_status<>'ACTIVE' OR NOT serms.equipment_can_reserve(NEW.equipment_id,NEW.start_at) THEN
            RAISE EXCEPTION 'User inactive or equipment unavailable' USING ERRCODE='23514';
        END IF;
        IF NEW.end_at <= statement_timestamp() THEN
            RAISE EXCEPTION 'Reservation interval has already ended' USING ERRCODE='23514';
        END IF;
        IF TG_OP='INSERT' AND NEW.status <>
            (CASE WHEN item.requires_approval THEN 'PENDING_APPROVAL' ELSE 'CONFIRMED' END) THEN
            RAISE EXCEPTION 'Incorrect initial reservation status' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER reservation_rules BEFORE INSERT OR UPDATE ON serms.reservation
    FOR EACH ROW EXECUTE FUNCTION serms.check_reservation();

-- Cross-entity integrity guards; application services still own authorization,
-- checkout-window checks and the atomic equipment/reservation/audit updates.
CREATE FUNCTION serms.check_loan() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item_id uuid; expected_due timestamptz;
BEGIN
    IF TG_OP='UPDATE' THEN
        IF (NEW.loan_id,NEW.reservation_id,NEW.checkout_by,NEW.checked_out_at,NEW.due_at)
            IS DISTINCT FROM (OLD.loan_id,OLD.reservation_id,OLD.checkout_by,OLD.checked_out_at,OLD.due_at)
            OR (OLD.status='RETURNED' AND
                (NEW.status,NEW.return_by,NEW.returned_at,NEW.return_condition,NEW.return_note)
                IS DISTINCT FROM (OLD.status,OLD.return_by,OLD.returned_at,OLD.return_condition,OLD.return_note)) THEN
            RAISE EXCEPTION 'Loan identity and completed return are immutable' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.status<>'ACTIVE' THEN
        RAISE EXCEPTION 'Loan must begin ACTIVE' USING ERRCODE='23514';
    END IF;
    SELECT equipment_id,end_at INTO item_id,expected_due FROM serms.reservation WHERE reservation_id=NEW.reservation_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Unknown reservation' USING ERRCODE='23503'; END IF;
    PERFORM 1 FROM serms.equipment WHERE equipment_id=item_id FOR UPDATE;
    IF NEW.due_at<>expected_due THEN
        RAISE EXCEPTION 'Due time must match reservation end' USING ERRCODE='23514';
    END IF;
    IF NEW.status='ACTIVE' AND EXISTS (
        SELECT 1 FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
        WHERE r.equipment_id=item_id AND l.status='ACTIVE' AND l.loan_id<>NEW.loan_id) THEN
        RAISE EXCEPTION 'Equipment already has an active loan' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER loan_rules BEFORE INSERT OR UPDATE ON serms.loan FOR EACH ROW EXECUTE FUNCTION serms.check_loan();

CREATE FUNCTION serms.check_maintenance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM serms.equipment WHERE equipment_id=NEW.equipment_id FOR UPDATE;
    IF NEW.loan_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
        WHERE l.loan_id=NEW.loan_id AND r.equipment_id=NEW.equipment_id) THEN
        RAISE EXCEPTION 'Maintenance loan must concern the same equipment' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' AND NEW.status<>'OPEN' THEN
        RAISE EXCEPTION 'Maintenance case must begin OPEN' USING ERRCODE='23514';
    ELSIF TG_OP='UPDATE' THEN
        IF (NEW.maintenance_case_id,NEW.equipment_id,NEW.reported_by,NEW.reported_at)
            IS DISTINCT FROM (OLD.maintenance_case_id,OLD.equipment_id,OLD.reported_by,OLD.reported_at)
            OR (OLD.loan_id IS NOT NULL AND NEW.loan_id IS DISTINCT FROM OLD.loan_id)
            OR (NEW.status<>OLD.status AND NOT (
                (OLD.status='OPEN' AND NEW.status='ASSIGNED')
                OR (OLD.status='ASSIGNED' AND NEW.status='IN_PROGRESS')
                OR (OLD.status='IN_PROGRESS' AND NEW.status IN ('RESOLVED','UNREPAIRABLE')))) THEN
            RAISE EXCEPTION 'Illegal maintenance change' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER maintenance_rules BEFORE INSERT OR UPDATE ON serms.maintenance_case
    FOR EACH ROW EXECUTE FUNCTION serms.check_maintenance();

CREATE FUNCTION serms.keep_retired() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status='RETIRED' AND NEW.status<>'RETIRED' THEN
        RAISE EXCEPTION 'Retired equipment cannot be restored' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER equipment_retired BEFORE UPDATE ON serms.equipment FOR EACH ROW EXECUTE FUNCTION serms.keep_retired();
INSERT INTO serms.schema_version(version,description) VALUES (2,'Align full schema with SERMS ERD and state specifications');
COMMIT;