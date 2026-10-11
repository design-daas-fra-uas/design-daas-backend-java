-- ============================================================
-- V2__registration_timestamps_to_instant.sql
-- registration_code_timeout / registration_used_moment are now mapped to
-- java.time.Instant (UTC), like locked_until and the token tables, instead of
-- the timezone-less LocalDateTime. Existing DATETIME values are converted
-- using the database session time zone.
-- ============================================================

ALTER TABLE users
    MODIFY registration_code_timeout TIMESTAMP (6) NULL DEFAULT NULL,
    MODIFY registration_used_moment TIMESTAMP (6) NULL DEFAULT NULL;
