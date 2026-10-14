-- Equipment master data required by Proposal 5.2 (Issue #40, split from #17).
-- Additive only: existing rows keep their values, no status or history is rewritten.
BEGIN;

-- Categories become a managed list. Existing free-text values are kept as the category names,
-- and equipment.category references them: a rename cascades, deleting a used category is refused.
CREATE TABLE serms.equipment_category (
    name text PRIMARY KEY CHECK (length(btrim(name)) > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO serms.equipment_category(name)
SELECT DISTINCT category FROM serms.equipment ORDER BY category;
ALTER TABLE serms.equipment
    ADD CONSTRAINT equipment_category_fk FOREIGN KEY (category)
        REFERENCES serms.equipment_category(name) ON UPDATE CASCADE ON DELETE RESTRICT;
-- Existing writers (local demo seed, UC01/UC03/UC04 test fixtures) still insert free-text
-- categories. Register an unknown category instead of failing; the management API validates
-- against the list.
CREATE FUNCTION serms.register_equipment_category() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO serms.equipment_category(name) VALUES (NEW.category) ON CONFLICT (name) DO NOTHING;
    RETURN NEW;
END;
$$;
CREATE TRIGGER equipment_category_register BEFORE INSERT OR UPDATE OF category ON serms.equipment
    FOR EACH ROW EXECUTE FUNCTION serms.register_equipment_category();

-- Serial numbers were never recorded, so existing rows stay NULL ("not recorded") rather than
-- receiving invented values. Recorded serial numbers are unique, like asset tags.
-- Condition describes physical wear; faults are handled by maintenance, so there is no DAMAGED
-- value here. Existing items are in service and start as GOOD.
-- NULL max_borrow_duration means the global reservation limit applies.
ALTER TABLE serms.equipment
    ADD COLUMN serial_number text
        CONSTRAINT equipment_serial_number_key UNIQUE
        CONSTRAINT equipment_serial_number_check CHECK (length(btrim(serial_number)) > 0),
    ADD COLUMN description text
        CONSTRAINT equipment_description_check CHECK (length(btrim(description)) > 0),
    ADD COLUMN condition text NOT NULL DEFAULT 'GOOD'
        CONSTRAINT equipment_condition_check CHECK (condition IN ('GOOD', 'FAIR', 'POOR')),
    ADD COLUMN max_borrow_duration interval
        CONSTRAINT equipment_max_borrow_duration_check CHECK (max_borrow_duration > interval '0');

INSERT INTO serms.schema_version(version, description)
VALUES (5, 'Equipment master data: categories, serial number, description, condition, maximum borrowing duration');
COMMIT;
