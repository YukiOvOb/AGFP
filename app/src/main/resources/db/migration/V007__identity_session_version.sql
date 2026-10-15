-- Additive revocation counter. Keep hashes out of session identity; never rewrite old migrations.
BEGIN;
ALTER TABLE serms.app_user
  ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0
    CONSTRAINT app_user_session_version_nonnegative CHECK (session_version >= 0);
INSERT INTO serms.schema_version(version, description)
VALUES (7, 'Identity session version for administrator security changes');
COMMIT;
