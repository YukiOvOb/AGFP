-- Fault category and priority for maintenance cases (Proposal 5.6, Issue #21). Follows V005.
-- Existing cases were reported without them and receive the neutral values OTHER and MEDIUM.
BEGIN;
ALTER TABLE serms.maintenance_case
    ADD COLUMN fault_category text NOT NULL DEFAULT 'OTHER'
        CONSTRAINT maintenance_fault_category_check CHECK (fault_category IN
            ('MECHANICAL', 'ELECTRICAL', 'SOFTWARE', 'PHYSICAL_DAMAGE', 'MISSING_PART', 'OTHER')),
    ADD COLUMN priority text NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT maintenance_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));
CREATE INDEX maintenance_open_priority ON serms.maintenance_case(priority, reported_at)
    WHERE status = 'OPEN';
INSERT INTO serms.schema_version(version, description)
VALUES (6, 'Maintenance fault category and priority');
COMMIT;
