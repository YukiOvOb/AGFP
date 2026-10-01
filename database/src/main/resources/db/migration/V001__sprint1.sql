-- Apply once to an empty database with psql -v ON_ERROR_STOP=1 -f ...
BEGIN;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA serms;
CREATE TABLE serms.schema_version (
    version integer PRIMARY KEY,
    description text NOT NULL,
    installed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE serms.app_user (
    id uuid PRIMARY KEY,
    email text NOT NULL CHECK (email = lower(btrim(email)) AND email LIKE '%_@_%._%'),
    display_name text NOT NULL CHECK (length(btrim(display_name)) > 0),
    password_hash text NOT NULL CHECK (length(btrim(password_hash)) > 0),
    role text NOT NULL CHECK (role IN ('BORROWER', 'APPROVER', 'TECHNICIAN', 'ADMIN')),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (email)
);
CREATE TABLE serms.equipment (
    id uuid PRIMARY KEY,
    asset_tag text NOT NULL UNIQUE CHECK (length(btrim(asset_tag)) > 0),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    category text NOT NULL CHECK (length(btrim(category)) > 0),
    location text NOT NULL CHECK (length(btrim(location)) > 0),
    status text NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'ON_LOAN', 'MAINTENANCE', 'RETIRED')),
    requires_approval boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE serms.reservation (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES serms.app_user(id) ON DELETE RESTRICT,
    equipment_id uuid NOT NULL REFERENCES serms.equipment(id) ON DELETE RESTRICT,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'FULFILLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (isfinite(starts_at) AND isfinite(ends_at) AND starts_at < ends_at),
    CONSTRAINT reservation_no_overlap EXCLUDE USING gist (
        equipment_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'))
);
CREATE INDEX reservation_user_time ON serms.reservation(user_id, starts_at);
CREATE INDEX equipment_category_status ON serms.equipment(category, status);
-- Lock equipment first, then user, consistently with repository writes.
CREATE FUNCTION serms.check_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item serms.equipment; person serms.app_user;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.id, NEW.user_id, NEW.equipment_id, NEW.starts_at, NEW.ends_at, NEW.created_at)
            IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.equipment_id, OLD.starts_at, OLD.ends_at, OLD.created_at) THEN
            RAISE EXCEPTION 'Reservation details are immutable; cancel and rebook' USING ERRCODE = '23514';
        END IF;
        IF NEW.status = OLD.status THEN RETURN NEW; END IF;
        IF NOT ((OLD.status = 'PENDING' AND NEW.status IN ('CONFIRMED', 'REJECTED', 'CANCELLED'))
            OR (OLD.status = 'CONFIRMED' AND NEW.status IN ('CANCELLED', 'FULFILLED'))) THEN
            RAISE EXCEPTION 'Illegal reservation transition' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'INSERT' OR NEW.status = 'CONFIRMED' THEN
        SELECT * INTO item FROM serms.equipment WHERE id = NEW.equipment_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Unknown equipment' USING ERRCODE = '23503'; END IF;
        SELECT * INTO person FROM serms.app_user WHERE id = NEW.user_id FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Unknown user' USING ERRCODE = '23503'; END IF;
        IF NOT person.active OR item.status <> 'AVAILABLE' THEN
            RAISE EXCEPTION 'User inactive or equipment unavailable' USING ERRCODE = '23514';
        END IF;
        IF NEW.starts_at < statement_timestamp() THEN
            RAISE EXCEPTION 'Reservation must start in the future' USING ERRCODE = '23514';
        END IF;
        IF TG_OP = 'INSERT' AND NEW.status <>
            (CASE WHEN item.requires_approval THEN 'PENDING' ELSE 'CONFIRMED' END) THEN
            RAISE EXCEPTION 'Incorrect initial reservation status' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER reservation_rules BEFORE INSERT OR UPDATE ON serms.reservation
    FOR EACH ROW EXECUTE FUNCTION serms.check_reservation();
INSERT INTO serms.schema_version(version, description) VALUES (1, 'Sprint 1 users, equipment, reservations');
COMMIT;