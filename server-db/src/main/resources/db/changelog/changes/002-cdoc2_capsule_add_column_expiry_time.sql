-- liquibase formatted sql
-- changeset cdoc2_capsule_add_column_expiry_time:2
ALTER TABLE cdoc2_capsule
ADD expiry_time timestamp;
