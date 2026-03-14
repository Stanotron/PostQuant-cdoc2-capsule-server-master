-- liquibase formatted sql
-- changeset expired_cdoc2_capsule_cleanup:3
CREATE OR REPLACE PROCEDURE expired_cdoc2_capsule_cleanup()
LANGUAGE SQL
AS $$
  DELETE FROM cdoc2_capsule
  WHERE expiry_time IN (
    SELECT expiry_time
    FROM cdoc2_capsule
    WHERE expiry_time < CURRENT_TIMESTAMP
    LIMIT 1000
  )
$$
